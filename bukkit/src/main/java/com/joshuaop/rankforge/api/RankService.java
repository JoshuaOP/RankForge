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
import java.util.logging.Level;

/**
 * Core business logic for all rank operations, handling rank-ups, admin sets,
 * resets, inventory/statistic consumption, and safe state rollbacks.
 */
public class RankService {

    private final RankForge       plugin;
    private final ProgressService progressService;

    public RankService(RankForge plugin, ProgressService progressService) {
        this.plugin          = plugin;
        this.progressService = progressService;
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

        if (!applyRank(player, event.getNewRankId(), RankHistoryEntry.ChangeType.SET)) return false;
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

        if (!applyRank(player, event.getDefaultRankId(), RankHistoryEntry.ChangeType.RESET)) return;
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
        PlayerData data = loadData(player);
        RankManager rm = plugin.getRankManager();
        String nextId = rm.getNextRankId(data.rankId());

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

        String resolvedId = event.getNewRankId();
        RankModel nextModel = rm.getRank(resolvedId);
        if (nextModel == null) {
            plugin.getLogger().warning("Rank-up resolved to unknown rank '" + resolvedId + "'.");
            return false;
        }

        int oldLevel = player.getLevel();
        float oldExp = player.getExp();
        ItemStack[] oldContents = copyItems(player.getInventory().getContents());
        ItemStack[] oldArmor = copyItems(player.getInventory().getArmorContents());
        ItemStack oldOffhand = player.getInventory().getItemInOffHand();
        if (oldOffhand != null) oldOffhand = oldOffhand.clone();
        int oldMobKills = safeStatistic(player, Statistic.MOB_KILLS);
        String requiredStatisticId = nextModel.getRequiredStatisticId();
        int oldRequiredStatistic = -1;
        if (requiredStatisticId != null && !requiredStatisticId.isBlank()) {
            try {
                Statistic statistic = Statistic.valueOf(requiredStatisticId.toUpperCase());
                if (statistic.getType() == Statistic.Type.UNTYPED) {
                    oldRequiredStatistic = safeStatistic(player, statistic);
                }
            } catch (IllegalArgumentException ignored) {}
        }

        if (!plugin.getSoftDependency().applyRankPermissions(player, oldRankId, resolvedId)) {
            plugin.getLangManager().send(player, "rankup_fail");
            return false;
        }

        double cost = nextModel.getRequiredMoney();
        boolean charged = false;
        if (cost > 0) {
            charged = plugin.getRequirementManager().withdrawMoney(player, cost);
            if (!charged) {
                restorePermissions(player, oldRankId);
                plugin.getLogger().warning("Rank-up payment was not confirmed for "
                        + player.getUniqueId() + "; rank-up aborted.");
                plugin.getLangManager().send(player, "rankup_fail");
                return false;
            }
            PlayerData chargedData = plugin.getRankManager().getCacheManager()
                    .getRaw(player.getUniqueId());
            if (chargedData != null) {
                plugin.getRankManager().getCacheManager().put(player.getUniqueId(),
                        chargedData.withMoney(plugin.getSoftDependency().getBalance(player)));
            }
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
                Runnable saveTask = () -> {
                    boolean saved = plugin.getRankManager().getRepository().save(finalData);
                    if (!saved) {
                        if (plugin.getYamlPlayerDataStorage() != null) {
                            plugin.getYamlPlayerDataStorage().savePlayerForEmergencyFallback(finalData);
                        } else if (plugin.isDebug()) {
                            plugin.getLogger().warning("Async rank-up persistence failed for " + player.getUniqueId());
                        }
                    }
                };
                Bukkit.getScheduler().runTaskAsynchronously(plugin, saveTask);
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
        if (!plugin.getSoftDependency().applyRankPermissions(player, null, rankId)) {
            plugin.getLogger().severe("Could not restore permissions for "
                    + player.getUniqueId() + " after a failed rank operation.");
        }
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

    private boolean applyRank(Player player, String newRankId,
                              RankHistoryEntry.ChangeType changeType) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("Rank changes must run on the Bukkit main thread.");
            return false;
        }
        PlayerData oldData = loadData(player);
        String     oldRank = oldData.rankId();

        if (!plugin.getSoftDependency().applyRankPermissions(player, oldRank, newRankId)) {
            plugin.getLogger().warning("RankForge permission update was not accepted for "
                    + player.getUniqueId() + ".");
            return false;
        }

        PlayerData updated = oldData.withRank(newRankId);
        plugin.getRankManager().getCacheManager().put(player.getUniqueId(), updated);
        
        Runnable saveTask = () -> {
            boolean saved = plugin.getRankManager().getRepository().save(updated);
            if (!saved) {
                if (plugin.getYamlPlayerDataStorage() != null) {
                    plugin.getYamlPlayerDataStorage().savePlayerForEmergencyFallback(updated);
                } else {
                    plugin.getLogger().warning("Async rank persistence failed for " + player.getUniqueId() + "; data retained in cache/YAML fallback.");
                }
            }
        };
        Bukkit.getScheduler().runTaskAsynchronously(plugin, saveTask);

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
