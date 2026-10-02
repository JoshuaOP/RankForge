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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Core business logic for all rank operations, handling rank-ups, admin sets,
 * resets, inventory/statistic consumption, and safe state rollbacks.
 */
public class RankService {

    private final RankForge       plugin;
    private final ProgressService progressService;
    private final Executor        mainThreadExecutor;

    public RankService(RankForge plugin, ProgressService progressService) {
        this.plugin          = plugin;
        this.progressService = progressService;
        this.mainThreadExecutor = runnable -> Bukkit.getScheduler().runTask(plugin, runnable);
    }

    /**
     * Attempts to rank up a player, enforcing thread safety, queue checks,
     * requirement verification, and atomic state transitions.
     */
    public boolean rankUp(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("Rank-up rejected off the Bukkit main thread.");
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (!plugin.getRankupQueue().acquire(uuid)) {
            plugin.getLangManager().send(player, "rankup_processing");
            return false;
        }

        doRankUpAsync(player).whenComplete((success, throwable) -> {
            plugin.getRankupQueue().release(uuid);
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Rank-up encountered an unexpected error for " + uuid, throwable);
            }
        });
        return true;
    }

    public boolean setRank(Player player, String rankId) {
        setRank(player, rankId, Bukkit.getConsoleSender());
        return true;
    }

    public CompletableFuture<Boolean> setRank(Player player, String rankId, CommandSender setter) {
        if (plugin.getRankManager().getRank(rankId) == null) return CompletableFuture.completedFuture(false);
        String oldRankId = getRankId(player);

        RankSetEvent event = new RankSetEvent(player, setter, oldRankId, rankId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CompletableFuture.completedFuture(false);

        return applyRank(player, event.getNewRankId(), RankHistoryEntry.ChangeType.SET)
                .thenApply(success -> {
                    if (success) {
                        plugin.getHookRegistry().fireRankSet(player, oldRankId, rankId);
                    }
                    return success;
                });
    }

    public void resetRank(Player player) {
        resetRank(player, Bukkit.getConsoleSender());
    }

    public CompletableFuture<Void> resetRank(Player player, CommandSender resetter) {
        String oldRankId     = getRankId(player);
        String defaultRankId = plugin.getRankManager().getDefaultRankId();

        RankResetEvent event = new RankResetEvent(player, resetter, oldRankId, defaultRankId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CompletableFuture.completedFuture(null);

        return applyRank(player, event.getDefaultRankId(), RankHistoryEntry.ChangeType.RESET)
                .thenAccept(success -> {
                    if (success) {
                        plugin.getHookRegistry().fireRankReset(player, oldRankId);
                    }
                });
    }

    public PlayerRank getPlayerRank(Player player) {
        PlayerData data = loadData(player);
        RankManager rm  = plugin.getRankManager();
        return new PlayerRank(player.getUniqueId(), player.getName(),
                data.rankId(), rm.getDisplayName(data.rankId()),
                rm.getNextRankId(data.rankId()), progressService.getPercent(player));
    }

    private CompletableFuture<Boolean> doRankUpAsync(Player player) {
        PlayerData data = loadData(player);
        RankManager rm = plugin.getRankManager();
        String nextId = rm.getNextRankId(data.rankId());

        if (nextId == null || nextId.isBlank()) {
            plugin.getLangManager().send(player, "rankup_max");
            return CompletableFuture.completedFuture(false);
        }
        if (!plugin.getAntiBypassManager().check(player.getUniqueId())) {
            plugin.getLangManager().send(player, "gui_click_fast");
            return CompletableFuture.completedFuture(false);
        }
        if (!plugin.getRequirementManager().meetsAll(player, nextId)) {
            plugin.getLangManager().send(player, "rankup_fail");
            return CompletableFuture.completedFuture(false);
        }

        String oldRankId = data.rankId();
        RankupEvent event = new RankupEvent(player, oldRankId, nextId);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CompletableFuture.completedFuture(false);

        String resolvedId = event.getNewRankId();
        RankModel nextModel = rm.getRank(resolvedId);
        if (nextModel == null) {
            plugin.getLogger().warning("Rank-up resolved to unknown rank '" + resolvedId + "'.");
            return CompletableFuture.completedFuture(false);
        }

        int oldLevel = player.getLevel();
        float oldExp = player.getExp();
        ItemStack[] oldContents = copyItems(player.getInventory().getContents());
        ItemStack[] oldArmor = copyItems(player.getInventory().getArmorContents());
        
        ItemStack rawOffhand = player.getInventory().getItemInOffHand();
        final ItemStack oldOffhand = rawOffhand != null ? rawOffhand.clone() : null;
        
        int oldMobKills = safeStatistic(player, Statistic.MOB_KILLS);
        String requiredStatisticId = nextModel.getRequiredStatisticId();
        
        int tempReqStat = -1;
        if (requiredStatisticId != null && !requiredStatisticId.isBlank()) {
            try {
                Statistic statistic = Statistic.valueOf(requiredStatisticId.toUpperCase());
                if (statistic.getType() == Statistic.Type.UNTYPED) {
                    tempReqStat = safeStatistic(player, statistic);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        final int oldRequiredStatistic = tempReqStat;

        return plugin.getSoftDependency().applyRankPermissions(player, oldRankId, resolvedId)
                .thenApplyAsync(success -> {
                    if (!success || !player.isOnline()) {
                        plugin.getLangManager().send(player, "rankup_fail");
                        return false;
                    }

                    double cost = nextModel.getRequiredMoney();
                    final boolean charged;
                    if (cost > 0) {
                        boolean withdrawResult = plugin.getRequirementManager().withdrawMoney(player, cost);
                        if (!withdrawResult) {
                            restorePermissions(player, oldRankId);
                            plugin.getLogger().warning("Rank-up payment was not confirmed for "
                                    + player.getUniqueId() + "; rank-up aborted.");
                            plugin.getLangManager().send(player, "rankup_fail");
                            return false;
                        }
                        charged = true;
                        PlayerData chargedData = plugin.getRankManager().getCacheManager()
                                .getRaw(player.getUniqueId());
                        if (chargedData != null) {
                            plugin.getRankManager().getCacheManager().put(player.getUniqueId(),
                                    chargedData.withMoney(plugin.getSoftDependency().getBalance(player)));
                        }
                    } else {
                        charged = false;
                    }

                    try {
                        PlayerData currentRankData = plugin.getRankManager().getCacheManager()
                                .getRaw(player.getUniqueId());
                        if (currentRankData == null) currentRankData = data;
                        plugin.getRankManager().getCacheManager().put(
                                player.getUniqueId(), currentRankData.withRank(resolvedId));
                        
                        if (plugin.getExperienceManager() != null) {
                            plugin.getExperienceManager().deductRankup(player, resolvedId);
                        }
                        resetTrackedProgress(player, nextModel);

                        PlayerData afterRank = plugin.getRankManager().getCacheManager()
                                .getRaw(player.getUniqueId());
                        if (afterRank != null && !afterRank.completedRequirements().isEmpty()) {
                            plugin.getRankManager().getCacheManager().put(player.getUniqueId(),
                                    afterRank.withCompletedRequirements(Set.of()));
                        }

                        PlayerData finalData = plugin.getRankManager().getCacheManager()
                                .getRaw(player.getUniqueId());
                        
                        if (finalData != null) {
                            boolean saved = plugin.getRankManager().getRepository().save(finalData);
                            if (!saved) {
                                if (plugin.getYamlPlayerDataStorage() != null) {
                                    plugin.getYamlPlayerDataStorage().savePlayerForEmergencyFallback(finalData);
                                } else if (plugin.isDebug()) {
                                    plugin.getLogger().warning("Rank-up persistence failed for " + player.getUniqueId());
                                }
                            }
                        }
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Rank-up state preparation failed for " + player.getUniqueId() + ".", e);
                        if (charged && !plugin.getSoftDependency().refund(player, cost)) {
                            plugin.getLogger().severe("Could not refund " + cost + " to "
                                    + player.getUniqueId() + " after rank-up failure.");
                        }
                        restoreRankUpState(player, data, oldLevel, oldExp, oldContents,
                                oldArmor, oldOffhand, oldMobKills, requiredStatisticId,
                                oldRequiredStatistic);
                        restorePermissions(player, oldRankId);
                        plugin.getLangManager().send(player, "rankup_fail");
                        return false;
                    }

                    if (plugin.getBypassRegistry() != null) {
                        plugin.getBypassRegistry().clearAll(player.getUniqueId());
                    }

                    String display = plugin.getRankManager().getDisplayName(resolvedId);
                    plugin.getSoundManager().playRankup(player);
                    plugin.getAnnouncementManager().sendRankup(player, display);
                    plugin.getCosmeticManager().onRankup(player, resolvedId, display);
                    executeRankCommands(player, resolvedId);
                    
                    if (plugin.getHistoryManager() != null) {
                        plugin.getHistoryManager().record(new RankHistoryEntry(
                                player.getUniqueId(), player.getName(), oldRankId, resolvedId,
                                RankHistoryEntry.ChangeType.RANKUP, System.currentTimeMillis()));
                    }
                    plugin.getHookRegistry().fireRankup(player, oldRankId, resolvedId);
                    return true;
                }, mainThreadExecutor);
    }

    private void resetTrackedProgress(Player player, RankModel achieved) {
        UUID uuid = player.getUniqueId();

        if (plugin.getBlockBreakTracker() != null) {
            plugin.getBlockBreakTracker().setCount(uuid, 0L);
        }

        try {
            player.setStatistic(Statistic.MOB_KILLS, 0);
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning(
                        "[RankService] Could not reset MOB_KILLS for "
                                + player.getName() + ": " + e.getMessage());
            }
        }

        if (achieved == null) return;

        String statId = achieved.getRequiredStatisticId();
        if (statId != null && !statId.isBlank() && achieved.getRequiredStatisticValue() > 0) {
            try {
                Statistic stat = Statistic.valueOf(statId.toUpperCase());
                if (stat.getType() == Statistic.Type.UNTYPED) {
                    player.setStatistic(stat, 0);
                }
            } catch (Exception e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning(
                            "[RankService] Could not reset statistic '" + statId
                                    + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        Map<String, Integer> requiredItems = achieved.getRequiredItems();
        if (requiredItems != null && !requiredItems.isEmpty()) {
            consumeRequiredItems(player, requiredItems);
        }
    }

    private void consumeRequiredItems(Player player, Map<String, Integer> items) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            try {
                Material mat    = Material.valueOf(entry.getKey().toUpperCase());
                int      amount = entry.getValue();
                if (amount <= 0) continue;
                player.getInventory().removeItem(new ItemStack(mat, amount));
            } catch (Exception e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning(
                            "[RankService] Could not consume item '" + entry.getKey()
                                    + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private static ItemStack[] copyItems(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    private static int safeStatistic(Player player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void restorePermissions(Player player, String rankId) {
        plugin.getSoftDependency().applyRankPermissions(player, null, rankId).whenComplete((success, error) -> {
            if (error != null || !success) {
                plugin.getLogger().severe("Could not restore permissions for "
                        + player.getUniqueId() + " after a failed rank operation.");
            }
        });
    }

    private void restoreRankUpState(Player player, PlayerData oldData,
                                    int oldLevel, float oldExp,
                                    ItemStack[] oldContents, ItemStack[] oldArmor,
                                    ItemStack oldOffhand, int oldMobKills,
                                    String requiredStatisticId,
                                    int oldRequiredStatistic) {
        try {
            player.setLevel(oldLevel);
            player.setExp(oldExp);
            player.getInventory().setContents(copyItems(oldContents));
            player.getInventory().setArmorContents(copyItems(oldArmor));
            player.getInventory().setItemInOffHand(
                    oldOffhand == null ? null : oldOffhand.clone());
            player.setStatistic(Statistic.MOB_KILLS, oldMobKills);
            if (oldRequiredStatistic >= 0 && requiredStatisticId != null) {
                Statistic statistic = Statistic.valueOf(requiredStatisticId.toUpperCase());
                if (statistic.getType() == Statistic.Type.UNTYPED) {
                    player.setStatistic(statistic, oldRequiredStatistic);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not fully restore live rank-up state for "
                            + player.getUniqueId() + ".", e);
        }
        if (plugin.getBlockBreakTracker() != null) {
            plugin.getBlockBreakTracker().setCount(player.getUniqueId(), oldData.blockBreaks());
        }
        plugin.getRankManager().getCacheManager().put(player.getUniqueId(), oldData);
    }

    private CompletableFuture<Boolean> applyRank(Player player, String newRankId,
                              RankHistoryEntry.ChangeType changeType) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("Rank changes must run on the Bukkit main thread.");
            return CompletableFuture.completedFuture(false);
        }
        PlayerData oldData = loadData(player);
        String     oldRank = oldData.rankId();

        return plugin.getSoftDependency().applyRankPermissions(player, oldRank, newRankId)
                .thenApplyAsync(success -> {
                    if (!success || !player.isOnline()) {
                        plugin.getLogger().warning("RankForge permission update was not accepted for "
                                + player.getUniqueId() + ".");
                        return false;
                    }

                    PlayerData updated = oldData.withRank(newRankId);
                    plugin.getRankManager().getCacheManager().put(player.getUniqueId(), updated);
                    
                    boolean saved = plugin.getRankManager().getRepository().save(updated);
                    if (!saved) {
                        if (plugin.getYamlPlayerDataStorage() != null) {
                            plugin.getYamlPlayerDataStorage().savePlayerForEmergencyFallback(updated);
                        } else {
                            plugin.getLogger().warning("Async rank persistence failed for " + player.getUniqueId() + "; data retained in cache/YAML fallback.");
                        }
                    }

                    String display = plugin.getRankManager().getDisplayName(newRankId);
                    plugin.getSoundManager().playRankup(player);
                    plugin.getAnnouncementManager().sendRankup(player, display);
                    plugin.getCosmeticManager().onRankup(player, newRankId, display);
                    executeRankCommands(player, newRankId);

                    if (plugin.getHistoryManager() != null) {
                        plugin.getHistoryManager().record(new RankHistoryEntry(
                                player.getUniqueId(), player.getName(),
                                oldRank, newRankId, changeType, System.currentTimeMillis()));
                    }
                    return true;
                }, mainThreadExecutor);
    }

    private void executeRankCommands(Player player, String rankId) {
        RankModel model = plugin.getRankManager().getRank(rankId);
        if (model == null || model.getCommands().isEmpty()) return;
        var console = Bukkit.getConsoleSender();
        for (String cmd : model.getCommands()) {
            String parsed = cmd.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(console, parsed);
        }
    }

    private PlayerData loadData(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getRankManager().getCacheManager().get(uuid);
        if (data != null) return data;
        return plugin.getRankManager().getRepository().load(uuid, player.getName());
    }

    private String getRankId(Player player) {
        PlayerData data = loadData(player);
        return data != null ? data.rankId() : plugin.getRankManager().getDefaultRankId();
    }
}
