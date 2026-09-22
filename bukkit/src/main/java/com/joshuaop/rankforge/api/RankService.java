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
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
import java.util.UUID;


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

        // Reset all tracked requirement progress after a successful rank-up.
        resetTrackedProgress(player, nextModel);

        // Clear any admin-granted requirement bypasses — they were for this rank only.
        if (plugin.getBypassRegistry() != null) {
            plugin.getBypassRegistry().clearAll(player.getUniqueId());
        }

        // Also clear the persisted completed-requirements record so a bypass granted
        // for the previous rank doesn't silently carry over into the next one.
        PlayerData afterRank = plugin.getRankManager().getCacheManager().get(player.getUniqueId());
        if (afterRank != null && !afterRank.completedRequirements().isEmpty()) {
            PlayerData cleared = afterRank.withCompletedRequirements(java.util.Set.of());
            plugin.getRankManager().getCacheManager().put(player.getUniqueId(), cleared);
            plugin.getRankManager().getRepository().save(cleared);
        }

        plugin.getHookRegistry().fireRankup(player, oldRankId, resolvedId);

        return true;
    }

    /**
     * Resets every progress-based requirement counter to zero (or consumes items)
     * after a rank-up.
     *
     * <h3>Reset behaviour per type:</h3>
     * <ul>
     *   <li><b>block-breaks</b> — reset to 0 unconditionally
     *       ({@link com.joshuaop.rankforge.tracker.BlockBreakTracker})</li>
     *   <li><b>playtime</b> — <em>not reset</em>; treated as a cumulative lifetime
     *       statistic so progress carries over to the next rank's requirement</li>
     *   <li><b>mob-kills</b> — Bukkit {@code MOB_KILLS} statistic reset to 0 unconditionally</li>
     *   <li><b>statistic</b> — the untyped Bukkit statistic required by {@code achieved}
     *       is reset to 0 (rank-specific, conditional on the rank having a statistic field)</li>
     *   <li><b>items</b> — required items consumed from the player's inventory
     *       (rank-specific, conditional on the rank having item requirements)</li>
     *   <li><b>quests</b> — always live-checked via permission nodes; not reset here</li>
     *   <li><b>custom</b> — always live-checked via the API; not reset here</li>
     *   <li><b>money / xp-level</b> — always deducted in {@link #doRankUp} before this
     *       method is called; never part of this reset</li>
     *   <li><b>permission / worlds</b> — always live-checked; not reset here</li>
     * </ul>
     *
     * @param player    the player who just ranked up
     * @param achieved  the {@link RankModel} whose requirements were just met; may be
     *                  {@code null} if an event listener resolved an unknown rank ID —
     *                  unconditional resets still run; rank-specific ones are skipped
     */
    private void resetTrackedProgress(Player player, RankModel achieved) {
        UUID uuid = player.getUniqueId();

        // ── Unconditional resets (always run regardless of rank model) ────────

        // Block Breaks — clears the RankForge custom counter
        if (plugin.getBlockBreakTracker() != null) {
            plugin.getBlockBreakTracker().setCount(uuid, 0L);
        }

        // Mob Kills — clears the vanilla MOB_KILLS statistic
        try {
            player.setStatistic(Statistic.MOB_KILLS, 0);
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning(
                        "[RankService] Could not reset MOB_KILLS for "
                                + player.getName() + ": " + e.getMessage());
            }
        }

        // ── Rank-specific resets (skipped when achieved rank model is unavailable) ─

        if (achieved == null) return;

        // Statistic — reset whichever untyped Bukkit statistic the achieved rank required
        // so that consecutive ranks sharing the same statistic always start from zero.
        String statId = achieved.getRequiredStatisticId();
        if (statId != null && !statId.isBlank() && achieved.getRequiredStatisticValue() > 0) {
            try {
                Statistic stat = Statistic.valueOf(statId.toUpperCase());
                if (stat.getType() == Statistic.Type.UNTYPED) {
                    player.setStatistic(stat, 0);
                }
            } catch (IllegalArgumentException e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning(
                            "[RankService] Unknown statistic '" + statId
                                    + "' — cannot reset. Check ranks.yml spelling.");
                }
            } catch (Exception e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning(
                            "[RankService] Could not reset statistic '" + statId
                                    + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        // Items — consume required items from the player's inventory.
        Map<String, Integer> requiredItems = achieved.getRequiredItems();
        if (requiredItems != null && !requiredItems.isEmpty()) {
            consumeRequiredItems(player, requiredItems);
        }
    }

    /**
     * Removes the specified item quantities from the player's inventory.
     *
     * <p>Each material is removed up to the required amount. If the player has fewer
     * than the required amount (e.g. due to a race condition), the remainder is silently
     * ignored — the requirement check already confirmed they had enough.
     *
     * @param player the player whose inventory to modify
     * @param items  map of material name → quantity to remove
     */
    private void consumeRequiredItems(Player player, Map<String, Integer> items) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            try {
                Material mat    = Material.valueOf(entry.getKey().toUpperCase());
                int      amount = entry.getValue();
                if (amount <= 0) continue;
                player.getInventory().removeItem(new ItemStack(mat, amount));
            } catch (IllegalArgumentException e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning(
                            "[RankService] Unknown material '" + entry.getKey()
                                    + "' — cannot consume items. Check ranks.yml spelling.");
                }
            } catch (Exception e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning(
                            "[RankService] Could not consume item '" + entry.getKey()
                                    + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
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
