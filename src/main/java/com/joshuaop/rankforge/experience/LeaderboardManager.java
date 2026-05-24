package com.joshuaop.rankforge.experience;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;

import java.util.*;

/**
 * Provides ranked leaderboard data for players.
 *
 * <p>Supports two leaderboard modes configurable in config.yml:
 * <ul>
 *   <li><b>rank-position</b> — sorted by highest rank in the chain (default).</li>
 *   <li><b>experience</b>    — sorted by total XP accumulated.</li>
 * </ul>
 *
 * Results are drawn from the in-memory cache plus any persisted YAML players
 * not currently online. Only players with saved data appear in the leaderboard.
 */
public class LeaderboardManager {

    /** Leaderboard sort mode. */
    public enum SortMode { RANK_POSITION, EXPERIENCE }

    private final RankForge plugin;

    public LeaderboardManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns the top-N players sorted by the given mode.
     *
     * @param limit max entries to return
     * @param mode  sort mode
     */
    public List<LeaderboardEntry> getTop(int limit, SortMode mode) {
        // Collect all known players: start from cache
        Map<UUID, PlayerData> combined = new LinkedHashMap<>();
        for (PlayerData d : plugin.getRankManager().getCacheManager().all()) {
            combined.put(d.uuid(), d);
        }

        // Supplement with YAML-persisted players not in the cache
        if (plugin.getYamlPlayerDataStorage() != null) {
            for (PlayerData d : plugin.getYamlPlayerDataStorage().loadAll()) {
                combined.putIfAbsent(d.uuid(), d);
            }
        }

        List<PlayerData> sorted = new ArrayList<>(combined.values());

        if (mode == SortMode.EXPERIENCE) {
            sorted.sort(Comparator.comparingLong(PlayerData::experience).reversed());
        } else {
            // Sort by rank chain position (highest index = highest rank)
            List<String> order = new ArrayList<>(plugin.getRankManager().getRankIds());
            sorted.sort(Comparator.comparingInt((PlayerData d) -> {
                int idx = order.indexOf(d.rankId());
                return idx < 0 ? -1 : idx;
            }).reversed());
        }

        List<LeaderboardEntry> result = new ArrayList<>();
        int position = 1;
        for (PlayerData d : sorted) {
            if (position > limit) break;
            result.add(new LeaderboardEntry(position++, d.playerName(), d.rankId(), d.experience()));
        }
        return result;
    }

    /**
     * Returns the top-N players using the configured sort mode from config.yml
     * ({@code leaderboard.sort-mode}: "rank-position" or "experience").
     */
    public List<LeaderboardEntry> getTop(int limit) {
        String modeStr = plugin.getConfig().getString("leaderboard.sort-mode", "rank-position");
        SortMode mode  = modeStr.equalsIgnoreCase("experience") ? SortMode.EXPERIENCE : SortMode.RANK_POSITION;
        return getTop(limit, mode);
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of one leaderboard row.
     */
    public record LeaderboardEntry(int position, String playerName, String rankId, long experience) {

        /** Format a display line for in-game messages. */
        public String toDisplayLine() {
            String pos = switch (position) {
                case 1 -> "§6#1";
                case 2 -> "§7#2";
                case 3 -> "§c#3";
                default -> "§8#" + position;
            };
            return pos + " §e" + playerName + " §8— §6" + rankId
                    + " §8(§a" + String.format("%,d", experience) + " XP§8)";
        }
    }
}
