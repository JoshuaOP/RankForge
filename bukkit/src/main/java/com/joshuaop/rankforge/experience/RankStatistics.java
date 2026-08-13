package com.joshuaop.rankforge.experience;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;

import java.util.UUID;

/**
 * Aggregates statistics for a single player.
 *
 * <p>Combines data from {@link ExperienceManager}, {@link RankHistoryManager},
 * and the in-memory cache to produce a complete player statistics snapshot.
 * Instances are lightweight value objects; create them on-demand.
 */
public class RankStatistics {

    private final UUID   playerUuid;
    private final String playerName;
    private final String currentRankId;
    private final long   experience;
    private final int    totalRankups;
    private final int    totalSets;
    private final int    totalResets;
    private final String highestRankId;

    private RankStatistics(Builder b) {
        this.playerUuid   = b.uuid;
        this.playerName   = b.playerName;
        this.currentRankId = b.currentRankId;
        this.experience   = b.experience;
        this.totalRankups = b.totalRankups;
        this.totalSets    = b.totalSets;
        this.totalResets  = b.totalResets;
        this.highestRankId = b.highestRankId;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Build statistics for a player from all available data sources.
     */
    public static RankStatistics of(UUID uuid, RankForge plugin) {
        RankHistoryManager history = plugin.getHistoryManager();
        ExperienceManager  xpMgr  = plugin.getExperienceManager();

        PlayerData data = plugin.getRankManager().getRepository()
                .loadOrCreate(uuid, "Unknown");
        String name    = data.playerName();
        String rankId  = data.rankId();
        long   xp      = data.experience();

        int rankups = 0, sets = 0, resets = 0;
        String highest = rankId;

        if (history != null) {
            for (RankHistoryEntry e : history.getHistory(uuid)) {
                switch (e.type()) {
                    case RANKUP -> rankups++;
                    case SET    -> sets++;
                    case RESET  -> resets++;
                }
                // Track the highest rank seen in history
                if (isHigher(e.toRankId(), highest, plugin)) {
                    highest = e.toRankId();
                }
            }
        }

        return new Builder(uuid)
                .playerName(name)
                .currentRankId(rankId)
                .experience(xp)
                .totalRankups(rankups)
                .totalSets(sets)
                .totalResets(resets)
                .highestRankId(highest)
                .build();
    }

    /** @return true if candidateId is "higher" in the rank chain than baseId. */
    private static boolean isHigher(String candidateId, String baseId, RankForge plugin) {
        int cIdx = 0, bIdx = 0, i = 0;
        for (String id : plugin.getRankManager().getRankIds()) {
            if (id.equals(candidateId)) cIdx = i;
            if (id.equals(baseId))      bIdx = i;
            i++;
        }
        return cIdx > bIdx;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID   getPlayerUuid()    { return playerUuid; }
    public String getPlayerName()    { return playerName; }
    public String getCurrentRankId() { return currentRankId; }
    public long   getExperience()    { return experience; }
    public int    getTotalRankups()  { return totalRankups; }
    public int    getTotalSets()     { return totalSets; }
    public int    getTotalResets()   { return totalResets; }
    public String getHighestRankId() { return highestRankId; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private final UUID uuid;
        private String playerName   = "Unknown";
        private String currentRankId = "Guest";
        private long   experience   = 0L;
        private int    totalRankups = 0;
        private int    totalSets    = 0;
        private int    totalResets  = 0;
        private String highestRankId = "Guest";

        public Builder(UUID uuid) { this.uuid = uuid; }
        public Builder playerName(String v)    { this.playerName    = v; return this; }
        public Builder currentRankId(String v) { this.currentRankId = v; return this; }
        public Builder experience(long v)      { this.experience    = v; return this; }
        public Builder totalRankups(int v)     { this.totalRankups  = v; return this; }
        public Builder totalSets(int v)        { this.totalSets     = v; return this; }
        public Builder totalResets(int v)      { this.totalResets   = v; return this; }
        public Builder highestRankId(String v) { this.highestRankId = v; return this; }
        public RankStatistics build()          { return new RankStatistics(this); }
    }
}
