package com.joshuaop.rankforge.api;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.api.requirement.CustomRequirement;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;
import com.joshuaop.rankforge.util.FormatUtil;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Calculates a player's overall progress percentage toward their next rank.
 *
 * Every active requirement type contributes to the percentage:
 *   - Vanilla XP level
 *   - Economy / money (Vault)
 *   - Block breaks (BlockBreakTracker)
 *   - Mob kills (Bukkit statistic)
 *   - Playtime minutes (Bukkit statistic)
 *   - Items in inventory
 *   - Permissions / quests (binary: 0 or 100 %)
 *   - World requirement (binary)
 *   - Custom requirements (binary per requirement)
 *
 * The overall percentage is the minimum across all active requirements so a
 * player truly needs to meet every requirement before reaching 100 %.
 *
 * Individual requirement progress entries are exposed via getRequirementProgress()
 * for detailed GUI and placeholder rendering.
 */
public class ProgressService {

    private final RankForge plugin;

    public ProgressService(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns overall progress 0.0 – 100.0 toward the player's next rank.
     * Returns 100.0 if the player is at max rank or has no next rank.
     */
    public double getPercent(Player player) {
        if (player == null) return 0.0;
        RankModel next = resolveNextRank(player);
        if (next == null) return 100.0;

        List<RequirementProgress> reqs = getRequirementProgress(player, next);
        if (reqs.isEmpty()) return 100.0;

        double min = 100.0;
        for (RequirementProgress rp : reqs) {
            min = Math.min(min, rp.percent());
        }
        return Math.max(0.0, Math.min(100.0, min));
    }

    /**
     * Returns a 10-block colored progress bar based on overall percentage.
     * §a (green) for filled blocks, §7 (gray) for remaining.
     */
    public String getProgressBar(Player player) {
        return buildBar(getPercent(player), 10);
    }

    /**
     * Returns a detailed list of per-requirement progress entries for display
     * in GUIs, chat, and placeholders.
     */
    public List<RequirementProgress> getRequirementProgress(Player player, RankModel next) {
        if (player == null || next == null) return List.of();

        List<RequirementProgress> list = new ArrayList<>();

        addMoney(player, next, list);
        addXpLevel(player, next, list);
        addBlockBreaks(player, next, list);
        addMobKills(player, next, list);
        addPlaytime(player, next, list);
        addItems(player, next, list);
        addPermission(player, next, list);
        addQuests(player, next, list);
        addWorlds(player, next, list);
        addStatistic(player, next, list);
        addCustom(player, next, list);

        return list;
    }

    /**
     * Convenience overload that resolves the next rank automatically.
     */
    public List<RequirementProgress> getRequirementProgress(Player player) {
        if (player == null) return List.of();
        RankModel next = resolveNextRank(player);
        if (next == null) return List.of();
        return getRequirementProgress(player, next);
    }

    // ── Per-requirement collectors ─────────────────────────────────────────

    private void addMoney(Player player, RankModel next, List<RequirementProgress> out) {
        double required = next.getRequiredMoney();
        if (required <= 0) return;
        double have = safeGetBalance(player);
        double pct  = Math.min(100.0, (have / required) * 100.0);
        out.add(new RequirementProgress("Money",
                String.format("$%,.0f", have), String.format("$%,.0f", required), pct));
    }

    private void addXpLevel(Player player, RankModel next, List<RequirementProgress> out) {
        int required = next.getRequiredXpLevel();
        if (required <= 0) return;
        int have = player.getLevel();
        double pct = Math.min(100.0, ((double) have / required) * 100.0);
        out.add(new RequirementProgress("XP Level",
                String.valueOf(have), "Level " + required, pct));
    }

    private void addBlockBreaks(Player player, RankModel next, List<RequirementProgress> out) {
        int required = next.getRequiredBlockBreaks();
        if (required <= 0) return;
        long have = 0L;
        if (plugin.getBlockBreakTracker() != null) {
            have = plugin.getBlockBreakTracker().getCount(player.getUniqueId());
        }
        double pct = Math.min(100.0, ((double) have / required) * 100.0);
        out.add(new RequirementProgress("Block Breaks",
                String.format("%,d", have), String.format("%,d", required), pct));
    }

    private void addMobKills(Player player, RankModel next, List<RequirementProgress> out) {
        int required = next.getRequiredMobKills();
        if (required <= 0) return;
        int have = 0;
        try { have = player.getStatistic(Statistic.MOB_KILLS); }
        catch (Exception ignored) {}
        double pct = Math.min(100.0, ((double) have / required) * 100.0);
        out.add(new RequirementProgress("Mob Kills",
                String.format("%,d", have), String.format("%,d", required), pct));
    }

    private void addPlaytime(Player player, RankModel next, List<RequirementProgress> out) {
        long requiredMin = next.getRequiredPlaytimeMinutes();
        if (requiredMin <= 0) return;
        long haveMin = 0L;
        try {
            int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            haveMin = ticks / 1200L;
        } catch (Exception ignored) {}
        double pct = Math.min(100.0, ((double) haveMin / requiredMin) * 100.0);
        out.add(new RequirementProgress("Playtime",
                FormatUtil.formatTime(haveMin), FormatUtil.formatTime(requiredMin), pct));
    }

    private void addItems(Player player, RankModel next, List<RequirementProgress> out) {
        Map<String, Integer> required = next.getRequiredItems();
        if (required == null || required.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            try {
                Material mat  = Material.valueOf(entry.getKey().toUpperCase());
                int req       = entry.getValue();
                int have      = FormatUtil.countItem(player, mat);
                double pct    = req <= 0 ? 100.0 : Math.min(100.0, ((double) have / req) * 100.0);
                out.add(new RequirementProgress("Item: " + FormatUtil.formatMaterial(mat),
                        String.valueOf(have), String.valueOf(req), pct));
            } catch (IllegalArgumentException e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning("[ProgressService] Unknown material in items requirement: "
                            + entry.getKey());
                }
            }
        }
    }

    private void addPermission(Player player, RankModel next, List<RequirementProgress> out) {
        String perm = next.getRequiredPermission();
        if (perm == null || perm.isBlank()) return;
        boolean has = player.hasPermission(perm);
        out.add(new RequirementProgress("Permission: " + perm,
                has ? "§aGranted" : "§cMissing", "Granted", has ? 100.0 : 0.0));
    }

    private void addQuests(Player player, RankModel next, List<RequirementProgress> out) {
        List<String> quests = next.getRequiredQuests();
        if (quests == null || quests.isEmpty()) return;
        for (String questId : quests) {
            if (questId == null || questId.isBlank()) continue;
            String node = "rankforge.quest.completed." + questId.toLowerCase();
            boolean done = player.hasPermission(node);
            out.add(new RequirementProgress("Quest: " + questId,
                    done ? "§aCompleted" : "§cIncomplete", "Completed", done ? 100.0 : 0.0));
        }
    }

    private void addWorlds(Player player, RankModel next, List<RequirementProgress> out) {
        List<String> worlds = next.getRequiredWorlds();
        if (worlds == null || worlds.isEmpty()) return;
        String current = player.getWorld() != null ? player.getWorld().getName() : "";
        boolean ok = worlds.contains(current);
        out.add(new RequirementProgress("World",
                current, String.join("/", worlds), ok ? 100.0 : 0.0));
    }

    private void addStatistic(Player player, RankModel next, List<RequirementProgress> out) {
        String statId  = next.getRequiredStatisticId();
        int    reqVal  = next.getRequiredStatisticValue();
        if (statId == null || statId.isBlank() || reqVal <= 0) return;
        try {
            Statistic stat = Statistic.valueOf(statId.toUpperCase());
            if (stat.getType() != Statistic.Type.UNTYPED) return;
            int have = player.getStatistic(stat);
            double pct = Math.min(100.0, ((double) have / reqVal) * 100.0);
            out.add(new RequirementProgress("Stat: " + statId,
                    String.format("%,d", have), String.format("%,d", reqVal), pct));
        } catch (Exception ignored) {}
    }

    private void addCustom(Player player, RankModel next, List<RequirementProgress> out) {
        var registry = plugin.getCustomRequirementRegistry();
        if (registry == null || registry.getAll().isEmpty()) return;

        Map<String, String> rankReqs = plugin.getRequirementManager()
                .getRankCustomRequirements(next.getId());
        if (rankReqs == null || rankReqs.isEmpty()) return;

        for (Map.Entry<String, String> entry : rankReqs.entrySet()) {
            CustomRequirement req = registry.get(entry.getKey());
            if (req == null) continue;
            try {
                boolean met = req.check(player, next, entry.getValue());
                out.add(new RequirementProgress("Custom: " + entry.getKey(),
                        met ? "§aMet" : "§cUnmet", "Met", met ? 100.0 : 0.0));
            } catch (Exception e) {
                if (plugin.isDebug()) {
                    plugin.getLogger().warning("[ProgressService] CustomRequirement '"
                            + entry.getKey() + "' threw: " + e.getMessage());
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RankModel resolveNextRank(Player player) {
        PlayerData data = loadData(player);
        if (data == null) return null;
        String nextId = plugin.getRankManager().getNextRankId(data.rankId());
        if (nextId == null || nextId.isBlank()) return null;
        return plugin.getRankManager().getRank(nextId);
    }

    private PlayerData loadData(Player player) {
        var cache = plugin.getRankManager().getCacheManager();
        if (cache.contains(player.getUniqueId())) return cache.get(player.getUniqueId());
        return PlayerData.defaultData(player.getUniqueId(), player.getName(),
                plugin.getRankManager().getDefaultRankId());
    }

    private double safeGetBalance(Player player) {
        try {
            if (plugin.getSoftDependency() != null && plugin.getSoftDependency().hasVault()) {
                return plugin.getSoftDependency().getBalance(player);
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    /**
     * Builds a colored progress bar.
     * @param percent 0.0–100.0
     * @param blocks  total bar block count
     */
    public static String buildBar(double percent, int blocks) {
        int filled = Math.max(0, Math.min(blocks, (int) (percent / 100.0 * blocks)));
        var sb = new StringBuilder("§a");
        for (int i = 0; i < blocks; i++) {
            if (i == filled) sb.append("§7");
            sb.append("█");
        }
        return sb.toString();
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    /**
     * Carries progress data for a single requirement.
     *
     * @param label    Human-readable label ("Money", "XP Level", etc.)
     * @param current  Current value as string
     * @param required Required value as string
     * @param percent  Progress 0.0–100.0
     */
    public record RequirementProgress(
            String label,
            String current,
            String required,
            double percent
    ) {
        /** Formats the entry as a single colorized chat line. */
        public String toDisplayLine() {
            boolean met = percent >= 100.0;
            String  bar = buildBar(percent, 5);
            return (met ? "§a✔ " : "§c✘ ") + "§7" + label + ": "
                    + current + " §8/ §7" + required
                    + " §8[" + bar + "§8] §e" + String.format("%.0f", percent) + "§7%";
        }
    }
}
