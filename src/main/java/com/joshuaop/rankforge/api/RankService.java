package com.joshuaop.rankforge.api;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.event.RankResetEvent;
import com.joshuaop.rankforge.api.event.RankSetEvent;
import com.joshuaop.rankforge.api.event.RankupEvent;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.experience.RankHistoryEntry;
import com.joshuaop.rankforge.rank.RankManager;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Core business logic for all rank operations.
 */
public class RankService {

    private final RankForge       plugin;
    private final ProgressService progressService;

    public RankService(RankForge plugin, ProgressService progressService) {
        this.plugin          = plugin;
        this.progressService = progressService;
    }

    public boolean rankUp(Player player) {
        if (!plugin.getRankupQueue().acquire(player.getUniqueId())) {
            plugin.getLangManager().send(player, "rankup_processing");
            return false;
        }
        try {
            return doRankUp(player);
        } finally {
            plugin.getRankupQueue().release(player.getUniqueId());
        }
    }

    public boolean setRank(Player player, String rankId) {
        return setRank(player, rankId, Bukkit.getConsoleSender());
    }

    public boolean setRank(Player player, String rankId, CommandSender setter) {
        if (plugin.getRankManager().getRank(rankId) == null) return false;
        String oldRankId = getRankId(player);

        RankSetEvent event = new RankSetEvent(player, setter, oldRankId, rankId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        applyRank(player, event.getNewRankId(), RankHistoryEntry.ChangeType.SET);
        plugin.getHookRegistry().fireRankSet(player, oldRankId, rankId);
        return true;
    }

    public void resetRank(Player player) {
        resetRank(player, Bukkit.getConsoleSender());
    }

    public void resetRank(Player player, CommandSender resetter) {
        String oldRankId     = getRankId(player);
        String defaultRankId = plugin.getRankManager().getDefaultRankId();

        RankResetEvent event = new RankResetEvent(player, resetter, oldRankId, defaultRankId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        applyRank(player, event.getDefaultRankId(), RankHistoryEntry.ChangeType.RESET);
        plugin.getHookRegistry().fireRankReset(player, oldRankId);
    }

    public PlayerRank getPlayerRank(Player player) {
        PlayerData data = loadData(player);
        RankManager rm  = plugin.getRankManager();
        return new PlayerRank(player.getUniqueId(), player.getName(),
                data.rankId(), rm.getDisplayName(data.rankId()),
                rm.getNextRankId(data.rankId()), progressService.getPercent(player));
    }

    private boolean doRankUp(Player player) {
        PlayerData  data   = loadData(player);
        RankManager rm     = plugin.getRankManager();
        String      nextId = rm.getNextRankId(data.rankId());

        if (nextId == null || nextId.isBlank()) {
            plugin.getLangManager().send(player, "rankup_max");
            return false;
        }
        if (!plugin.getAntiBypassManager().check(player.getUniqueId())) {
            plugin.getLangManager().send(player, "gui_click_fast");
            return false;
        }
        if (!plugin.getRequirementManager().meetsAll(player, nextId)) {
            plugin.getLangManager().send(player, "rankup_fail");
            return false;
        }

        String oldRankId = data.rankId();

        RankupEvent event = new RankupEvent(player, oldRankId, nextId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        // Use the rank ID resolved by the event (may have been changed by a listener).
        String resolvedId = event.getNewRankId();

        RankModel nextModel = rm.getRank(resolvedId);
        if (nextModel != null && nextModel.getRequiredMoney() > 0) {
            plugin.getRequirementManager().withdrawMoney(player, nextModel.getRequiredMoney());
        }

        applyRank(player, resolvedId, RankHistoryEntry.ChangeType.RANKUP);

        // Deduct XP levels after rank is applied and all checks have passed.
        if (plugin.getExperienceManager() != null) {
            plugin.getExperienceManager().deductRankup(player, resolvedId);
        }

        plugin.getHookRegistry().fireRankup(player, oldRankId, resolvedId);

        return true;
    }

    private void applyRank(Player player, String newRankId, RankHistoryEntry.ChangeType changeType) {
        PlayerData oldData = loadData(player);
        String     oldRank = oldData.rankId();

        plugin.getRankManager().getCacheManager().put(player.getUniqueId(),
                oldData.withRank(newRankId));

        String display = plugin.getRankManager().getDisplayName(newRankId);
        plugin.getSoundManager().playRankup(player);
        plugin.getAnnouncementManager().sendRankup(player, display);
        plugin.getSoftDependency().applyRankPermissions(player, newRankId);
        plugin.getCosmeticManager().onRankup(player, newRankId, display);
        executeRankCommands(player, newRankId);

        if (plugin.getHistoryManager() != null) {
            plugin.getHistoryManager().record(new RankHistoryEntry(
                    player.getUniqueId(), player.getName(),
                    oldRank, newRankId, changeType, System.currentTimeMillis()));
        }
    }

    private void executeRankCommands(Player player, String rankId) {
        RankModel model = plugin.getRankManager().getRank(rankId);
        if (model == null || model.getCommands().isEmpty()) return;
        var console = Bukkit.getConsoleSender();
        for (String raw : model.getCommands()) {
            String cmd = raw.replace("%player%", player.getName());
            if (Bukkit.isPrimaryThread()) Bukkit.dispatchCommand(console, cmd);
            else Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(console, cmd));
        }
    }

    private PlayerData loadData(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(player.getUniqueId())) return cache.get(player.getUniqueId());
        return PlayerData.defaultData(player.getUniqueId(), player.getName(),
                plugin.getRankManager().getDefaultRankId());
    }

    private String getRankId(Player player) {
        return loadData(player).rankId();
    }
}
