package com.joshuaop.rankforge.api;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.event.RankResetEvent;
import com.joshuaop.rankforge.api.event.RankSetEvent;
import com.joshuaop.rankforge.api.event.RankupEvent;
import com.joshuaop.rankforge.api.reward.CustomReward;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.experience.RankHistoryEntry;
import com.joshuaop.rankforge.rank.RankManager;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Core business logic for all rank operations.
 *
 * <p>Every rank mutation flows through this service, ensuring:
 * <ul>
 *   <li>The appropriate cancellable Bukkit event is fired before any change.</li>
 *   <li>The {@link com.joshuaop.rankforge.protection.RankupQueue} prevents concurrent processing.</li>
 *   <li>XP is awarded via {@link com.joshuaop.rankforge.experience.ExperienceManager}.</li>
 *   <li>History is recorded via {@link com.joshuaop.rankforge.experience.RankHistoryManager}.</li>
 *   <li>Custom rewards ({@link com.joshuaop.rankforge.api.reward.CustomRewardRegistry}) are applied.</li>
 *   <li>Plugin hooks ({@link com.joshuaop.rankforge.api.hook.HookRegistry}) are called.</li>
 *   <li>Challenge and quest systems are notified.</li>
 * </ul>
 */
public class RankService {

    private final RankForge       plugin;
    private final ProgressService progressService;

    public RankService(RankForge plugin, ProgressService progressService) {
        this.plugin          = plugin;
        this.progressService = progressService;
    }

    // ── Public operations ─────────────────────────────────────────────────────

    /**
     * Attempt to rank up the player, enforcing all safety checks.
     *
     * @return true if the player ranked up successfully.
     */
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

    /**
     * Directly set a player's rank by ID (admin operation — skips requirements).
     * Fires {@link RankSetEvent}; returns false if cancelled or rank ID not found.
     */
    public boolean setRank(Player player, String rankId) {
        return setRank(player, rankId, Bukkit.getConsoleSender());
    }

    /**
     * Set a player's rank with an explicit command sender for event data.
     */
    public boolean setRank(Player player, String rankId, CommandSender setter) {
        if (plugin.getRankManager().getRankData(rankId) == null) return false;
        String oldRankId = getRankId(player);

        // Fire cancellable event
        RankSetEvent event = new RankSetEvent(player, setter, oldRankId, rankId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        applyRank(player, event.getNewRankId(), RankHistoryEntry.ChangeType.SET);
        plugin.getHookRegistry().fireRankSet(player, oldRankId, rankId);
        return true;
    }

    /**
     * Reset the player to the server's default rank.
     * Fires {@link RankResetEvent}; does nothing if cancelled.
     */
    public void resetRank(Player player) {
        resetRank(player, Bukkit.getConsoleSender());
    }

    /**
     * Reset with an explicit command sender for event data.
     */
    public void resetRank(Player player, CommandSender resetter) {
        String oldRankId    = getRankId(player);
        String defaultRankId = plugin.getRankManager().getDefaultRankId();

        RankResetEvent event = new RankResetEvent(player, resetter, oldRankId, defaultRankId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        applyRank(player, event.getDefaultRankId(), RankHistoryEntry.ChangeType.RESET);
        plugin.getHookRegistry().fireRankReset(player, oldRankId);
    }

    /**
     * Build a {@link PlayerRank} snapshot for the given player.
     */
    public PlayerRank getPlayerRank(Player player) {
        PlayerData data = loadData(player);
        RankManager rm  = plugin.getRankManager();
        return new PlayerRank(player.getUniqueId(), player.getName(),
                data.rankId(), rm.getDisplayName(data.rankId()),
                rm.getNextRankId(data.rankId()), progressService.getPercent(player));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

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

        // Fire cancellable event — third-party plugins can cancel rank-ups here
        RankupEvent event = new RankupEvent(player, oldRankId, nextId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        // Deduct money requirement
        RankModel nextModel = rm.getRankData(nextId);
        if (nextModel != null && nextModel.getRequiredMoney() > 0) {
            plugin.getRequirementManager().withdrawMoney(player, nextModel.getRequiredMoney());
        }

        // Apply the rank (triggers cosmetics, announcements, LuckPerms, etc.)
        applyRank(player, event.getNewRankId(), RankHistoryEntry.ChangeType.RANKUP);

        // Award XP for ranking up
        if (plugin.getExperienceManager() != null)
            plugin.getExperienceManager().awardRankup(player);

        // Notify hook registry
        plugin.getHookRegistry().fireRankup(player, oldRankId, event.getNewRankId());

        // Notify challenge system (RANKUP-type challenges)
        if (plugin.getChallengeManager() != null)
            plugin.getChallengeManager().onRankup(player);

        return true;
    }

    /**
     * Central rank application — updates cache, triggers cosmetics/sounds/announcements,
     * records history, and applies custom rewards.
     */
    private void applyRank(Player player, String newRankId, RankHistoryEntry.ChangeType changeType) {
        PlayerData oldData = loadData(player);
        String     oldRank = oldData.rankId();

        // Update cache
        plugin.getRankManager().getCacheManager().put(player.getUniqueId(),
                oldData.withRank(newRankId));

        // Built-in effects
        String display = plugin.getRankManager().getDisplayName(newRankId);
        plugin.getSoundManager().playRankup(player);
        plugin.getAnnouncementManager().sendRankup(player, display);
        plugin.getSoftDependency().applyRankPermissions(player, newRankId);
        plugin.getCosmeticManager().onRankup(player, newRankId, display);
        executeRankCommands(player, newRankId);

        // Record history
        if (plugin.getHistoryManager() != null) {
            plugin.getHistoryManager().record(new RankHistoryEntry(
                    player.getUniqueId(), player.getName(),
                    oldRank, newRankId, changeType, System.currentTimeMillis()));
        }

        // Apply registered custom rewards
        applyCustomRewards(player, newRankId);
    }

    /** Execute console commands defined in ranks.yml for this rank. */
    private void executeRankCommands(Player player, String rankId) {
        RankModel model = plugin.getRankManager().getRankData(rankId);
        if (model == null || model.getCommands().isEmpty()) return;
        var console = Bukkit.getConsoleSender();
        for (String raw : model.getCommands()) {
            String cmd = raw.replace("%player%", player.getName());
            if (Bukkit.isPrimaryThread()) Bukkit.dispatchCommand(console, cmd);
            else Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(console, cmd));
        }
    }

    /**
     * Invoke all registered {@link CustomReward} handlers for the new rank.
     * Each registered reward is called with an empty config value;
     * rewards determine their own target config if needed.
     */
    private void applyCustomRewards(Player player, String rankId) {
        RankModel model = plugin.getRankManager().getRankData(rankId);
        if (model == null) return;

        for (CustomReward reward : plugin.getCustomRewardRegistry().getAll()) {
            try {
                reward.apply(player, model, "");
            } catch (Exception e) {
                plugin.getLogger().warning("[API] CustomReward '" + reward.getTypeId()
                        + "' threw exception for rank '" + rankId + "': " + e.getMessage());
            }
        }
    }

    private PlayerData loadData(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(player.getUniqueId())) return cache.get(player.getUniqueId());
        return PlayerData.defaultData(player.getUniqueId(), player.getName(),
                plugin.getRankManager().getDefaultRankId());
    }

    private String getRankId(Player player) {
        PlayerData d = loadData(player);
        return d.rankId();
    }
}
