package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.requirement.CustomRequirement;
import com.joshuaop.rankforge.api.requirement.CustomRequirementRegistry;
import com.joshuaop.rankforge.rank.RankModel;
import com.joshuaop.rankforge.util.FormatUtil;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Evaluates whether a player meets all requirements for a given rank.
 *
 * Built-in requirement types (configured per-rank in ranks.yml):
 *   money     — Vault balance
 *   xp-level  — Minecraft XP level
 *   permission — Bukkit permission node
 *   playtime   — Real wall-clock playtime tracked by PlaytimeTracker (not ticks)
 *               Format: "5d 5hr 5m 5s" (d=days, hr=hours, m=minutes, s=seconds)
 *               Legacy key "playtime-minutes: 60" is still accepted for backward compatibility.
 *   mob-kills  — Total mob kills via Statistic.MOB_KILLS
 *   block-breaks      — Exact cumulative count from BlockBreakTracker (not vanilla stats)
 *   statistic-id      — Any general untyped Bukkit Statistic name
 *   statistic-value   — Required value for statistic-id
 *   quests            — Quest IDs checked via permission rankforge.quest.completed.<id>
 *   worlds            — Player must be in one of the listed worlds
 *   items             — Player must have specific items in inventory
 *   custom            — Third-party implementations via CustomRequirementRegistry
 */
public class RequirementManager {

    private final RankForge plugin;

    /** Per-rank custom requirement values: rankId → { typeId → configValue } */
    private final Map<String, Map<String, String>> rankCustomRequirements = new HashMap<>();

    public RequirementManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /** @return true if the player satisfies every requirement of the given rank. */
    public boolean meetsAll(Player player, String rankId) {
        return getUnmet(player, rankId).isEmpty();
    }

    /** @return list of human-readable unmet requirements; empty if all met. */
    public List<String> getUnmet(Player player, String rankId) {
        List<String> unmet = new ArrayList<>();
        if (player == null || rankId == null) return unmet;

        RankModel rank = plugin.getRankManager().getRank(rankId);
        if (rank == null) return unmet;

        checkMoney(player, rank, unmet);
        checkXpLevel(player, rank, unmet);
        checkPermission(player, rank, unmet);
        checkPlaytime(player, rank, unmet);
        checkMobKills(player, rank, unmet);
        checkBlockBreaks(player, rank, unmet);
        checkStatistic(player, rank, unmet);
        checkQuests(player, rank, unmet);
        checkWorlds(player, rank, unmet);
        checkItems(player, rank, unmet);
        checkCustom(player, rank, rankId, unmet);

        return unmet;
    }

    // ── Built-in Checks ───────────────────────────────────────────────────────

    private void checkMoney(Player player, RankModel rank, List<String> unmet) {
        double required = rank.getRequiredMoney();
        if (required <= 0) return;
        double balance = plugin.getSoftDependency().getBalance(player);
        if (balance < required)
            unmet.add("§7Money: §c$" + String.format("%,.0f", required)
                    + " §8(have $" + String.format("%,.0f", balance) + ")");
    }

    private void checkXpLevel(Player player, RankModel rank, List<String> unmet) {
        int required = rank.getRequiredXpLevel();
        if (required <= 0) return;
        if (player.getLevel() < required)
            unmet.add("§7XP Level: §c" + required + " §8(have " + player.getLevel() + ")");
    }

    private void checkPermission(Player player, RankModel rank, List<String> unmet) {
        String perm = rank.getRequiredPermission();
        if (perm == null || perm.isBlank()) return;
        if (!player.hasPermission(perm))
            unmet.add("§7Permission: §c" + perm);
    }

    /**
     * Checks the player's playtime against the rank requirement.
     *
     * <p><strong>Source:</strong> {@link com.joshuaop.rankforge.tracker.PlaytimeTracker}
     * — tracks real-world elapsed time using {@link System#currentTimeMillis()}.
     * This is completely independent of server TPS, tick rate, or lag spikes.
     *
     * <p>The old approach of dividing {@code Statistic.PLAY_ONE_MINUTE} ticks by 1200
     * has been removed entirely. That method was inaccurate because the vanilla statistic
     * counts server ticks (affected by TPS) rather than wall-clock seconds.
     */
    private void checkPlaytime(Player player, RankModel rank, List<String> unmet) {
        long requiredMinutes = rank.getRequiredPlayTime();
        if (requiredMinutes <= 0) return;

        if (plugin.getPlaytimeTracker() == null) {
            if (plugin.isDebug())
                plugin.getLogger().warning("PlaytimeTracker is null — playtime check skipped.");
            return;
        }

        long minutesPlayed = plugin.getPlaytimeTracker().getPlayTime(player.getUniqueId());
        if (minutesPlayed < requiredMinutes)
            unmet.add("§7Playtime: §c" + FormatUtil.formatTime(requiredMinutes)
                    + " §8(have " + FormatUtil.formatTime(minutesPlayed) + ")");
    }

    private void checkMobKills(Player player, RankModel rank, List<String> unmet) {
        int required = rank.getRequiredMobKills();
        if (required <= 0) return;
        try {
            int kills = player.getStatistic(Statistic.MOB_KILLS);
            if (kills < required)
                unmet.add("§7Mob Kills: §c" + required + " §8(have " + kills + ")");
        } catch (Exception e) {
            if (plugin.isDebug())
                plugin.getLogger().warning("Mob-kills check failed: " + e.getMessage());
        }
    }

    /**
     * Checks the player's block-break count against the rank requirement.
     *
     * <p><strong>Source:</strong> {@link com.joshuaop.rankforge.tracker.BlockBreakTracker}
     * — an exact per-player {@link java.util.concurrent.atomic.AtomicLong} counter
     * incremented by a {@link org.bukkit.event.block.BlockBreakEvent} listener.
     * This is always accurate and never approximated via vanilla statistics.
     */
    private void checkBlockBreaks(Player player, RankModel rank, List<String> unmet) {
        int required = rank.getRequiredBlockBreaks();
        if (required <= 0) return;

        long actual = 0L;
        if (plugin.getBlockBreakTracker() != null) {
            actual = plugin.getBlockBreakTracker().getCount(player.getUniqueId());
        } else if (plugin.isDebug()) {
            plugin.getLogger().warning("BlockBreakTracker is null — block-breaks check skipped.");
            return;
        }

        if (actual < required)
            unmet.add("§7Block Breaks: §c" + String.format("%,d", required)
                    + " §8(have " + String.format("%,d", actual) + ")");
    }

    private void checkStatistic(Player player, RankModel rank, List<String> unmet) {
        String statId = rank.getRequiredStatisticId();
        int required  = rank.getRequiredStatisticValue();
        if (statId == null || statId.isBlank() || required <= 0) return;
        try {
            Statistic stat = Statistic.valueOf(statId.toUpperCase());
            int current = 0;
            if (stat.getType() == Statistic.Type.UNTYPED) {
                current = player.getStatistic(stat);
            } else {
                if (plugin.isDebug())
                    plugin.getLogger().info("Statistic '" + statId
                            + "' requires a type parameter — use custom requirement API for this.");
                return;
            }
            if (current < required)
                unmet.add("§7Statistic §e" + statId + "§7: §c" + required
                        + " §8(have " + current + ")");
        } catch (IllegalArgumentException e) {
            if (plugin.isDebug())
                plugin.getLogger().warning("Unknown statistic '" + statId
                        + "' in rank '" + rank.getId() + "'");
        } catch (Exception e) {
            if (plugin.isDebug())
                plugin.getLogger().warning("Statistic check failed: " + e.getMessage());
        }
    }

    private void checkQuests(Player player, RankModel rank, List<String> unmet) {
        List<String> quests = rank.getRequiredQuests();
        if (quests == null || quests.isEmpty()) return;
        for (String questId : quests) {
            if (questId == null || questId.isBlank()) continue;
            String permNode = "rankforge.quest.completed." + questId.toLowerCase();
            if (!player.hasPermission(permNode))
                unmet.add("§7Quest: §c" + questId);
        }
    }

    private void checkWorlds(Player player, RankModel rank, List<String> unmet) {
        List<String> worlds = rank.getRequiredWorlds();
        if (worlds == null || worlds.isEmpty()) return;
        if (player.getWorld() == null) return;
        String current = player.getWorld().getName();
        if (!worlds.contains(current))
            unmet.add("§7World: §c" + String.join("§8/§c", worlds)
                    + " §8(in " + current + ")");
    }

    private void checkItems(Player player, RankModel rank, List<String> unmet) {
        Map<String, Integer> required = rank.getRequiredItems();
        if (required == null || required.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            try {
                Material mat   = Material.valueOf(entry.getKey().toUpperCase());
                int reqAmount  = entry.getValue();
                int haveAmount = FormatUtil.countItem(player, mat);
                if (haveAmount < reqAmount)
                    unmet.add("§7Item: §c" + reqAmount + "x " + FormatUtil.formatMaterial(mat)
                            + " §8(have " + haveAmount + ")");
            } catch (IllegalArgumentException e) {
                if (plugin.isDebug())
                    plugin.getLogger().warning("Unknown material in rank '"
                            + rank.getId() + "': " + entry.getKey());
            }
        }
    }

    private void checkCustom(Player player, RankModel rank, String rankId, List<String> unmet) {
        CustomRequirementRegistry registry = plugin.getCustomRequirementRegistry();
        if (registry == null || registry.getAll().isEmpty()) return;

        Map<String, String> rankReqs = rankCustomRequirements.get(rankId.toLowerCase());
        if (rankReqs == null || rankReqs.isEmpty()) return;

        for (Map.Entry<String, String> entry : rankReqs.entrySet()) {
            CustomRequirement req = registry.get(entry.getKey());
            if (req == null) continue;
            try {
                if (!req.check(player, rank, entry.getValue()))
                    unmet.add(req.getUnmetMessage(player, rank, entry.getValue()));
            } catch (Exception e) {
                plugin.getLogger().warning("CustomRequirement '"
                        + entry.getKey() + "' threw exception: " + e.getMessage());
            }
        }
    }

    // ── Money Withdrawal ──────────────────────────────────────────────────────

    /** Withdraw rank-up cost from the player via Vault (if money is configured). */
    public boolean withdrawMoney(Player player, double amount) {
        return plugin.getSoftDependency().withdraw(player, amount);
    }

    // ── Custom Requirement Configuration ─────────────────────────────────────

    public void addRankRequirement(String rankId, String typeId, String configValue) {
        if (rankId == null || typeId == null) return;
        rankCustomRequirements
                .computeIfAbsent(rankId.toLowerCase(), k -> new LinkedHashMap<>())
                .put(typeId.toLowerCase(), configValue);
    }

    public boolean removeRankRequirement(String rankId, String typeId) {
        if (rankId == null || typeId == null) return false;
        Map<String, String> reqs = rankCustomRequirements.get(rankId.toLowerCase());
        if (reqs == null) return false;
        return reqs.remove(typeId.toLowerCase()) != null;
    }

    public void clearRankRequirements(String rankId) {
        if (rankId != null) rankCustomRequirements.remove(rankId.toLowerCase());
    }

    public Map<String, String> getRankCustomRequirements(String rankId) {
        if (rankId == null) return Map.of();
        return Collections.unmodifiableMap(
                rankCustomRequirements.getOrDefault(rankId.toLowerCase(), Map.of()));
    }
}
