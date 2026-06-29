package com.joshuaop.rankforge.db;

import java.util.UUID;

/**
 * Immutable data record representing a player's persistent rank state.
 * Uses Java 21 records for clean, allocation-efficient storage.
 *
 * Fields:
 *   uuid             — player UUID (Bedrock/Geyser compatible — never name-based)
 *   playerName       — last-known display name (cosmetic only; UUID is authoritative)
 *   rankId           — current rank ID as defined in ranks.yml
 *   experience       — total RankForge XP earned
 *   money            — cached Vault balance (refreshed at sync time)
 *   language         — per-player language code (e.g. "en", "de")
 *   blockBreaks      — cumulative blocks broken tracked by BlockBreakTracker;
 *                      replaces vanilla MINE_BLOCK stat approximation entirely
 *   playtimeMinutes  — cumulative real-world playtime in whole minutes, tracked by
 *                      PlaytimeTracker using wall-clock time (not Minecraft ticks).
 *                      Immune to TPS fluctuations and server lag.
 */
public record PlayerData(
        UUID   uuid,
        String playerName,
        String rankId,
        long   experience,
        double money,
        String language,
        long   blockBreaks,
        long   playtimeMinutes
) {

    /** Create default PlayerData for a brand-new player. */
    public static PlayerData defaultData(UUID uuid, String playerName, String defaultRank) {
        return new PlayerData(uuid, playerName, defaultRank, 0L, 0.0, "en", 0L, 0L);
    }

    // ── Functional copies ─────────────────────────────────────────────────────

    public PlayerData withRank(String newRankId) {
        return new PlayerData(uuid, playerName, newRankId, experience, money, language,
                blockBreaks, playtimeMinutes);
    }

    public PlayerData withLanguage(String newLang) {
        return new PlayerData(uuid, playerName, rankId, experience, money, newLang,
                blockBreaks, playtimeMinutes);
    }

    public PlayerData withMoney(double newMoney) {
        return new PlayerData(uuid, playerName, rankId, experience, newMoney, language,
                blockBreaks, playtimeMinutes);
    }

    public PlayerData withExperience(long newExp) {
        return new PlayerData(uuid, playerName, rankId, newExp, money, language,
                blockBreaks, playtimeMinutes);
    }

    public PlayerData withPlayerName(String newPlayerName) {
        return new PlayerData(uuid, newPlayerName, rankId, experience, money, language,
                blockBreaks, playtimeMinutes);
    }

    public PlayerData withBlockBreaks(long newBlockBreaks) {
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                Math.max(0L, newBlockBreaks), playtimeMinutes);
    }

    public PlayerData withPlaytimeMinutes(long newPlaytimeMinutes) {
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                blockBreaks, Math.max(0L, newPlaytimeMinutes));
    }
}
