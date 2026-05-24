package com.joshuaop.rankforge.db;

import java.util.UUID;

/**
 * Immutable data record representing a player's rank state.
 * Uses Java 21 records for clean, allocation-efficient storage.
 */
public record PlayerData(
        UUID uuid,
        String playerName,
        String rankId,
        long experience,
        double money,
        String language
) {
    /** Create default PlayerData for a brand-new player. */
    public static PlayerData defaultData(UUID uuid, String playerName, String defaultRank) {
        return new PlayerData(uuid, playerName, defaultRank, 0L, 0.0, "en");
    }

    /** Return a copy with updated rank. */
    public PlayerData withRank(String newRankId) {
        return new PlayerData(uuid, playerName, newRankId, experience, money, language);
    }

    /** Return a copy with updated language. */
    public PlayerData withLanguage(String newLang) {
        return new PlayerData(uuid, playerName, rankId, experience, money, newLang);
    }

    /** Return a copy with updated money balance. */
    public PlayerData withMoney(double newMoney) {
        return new PlayerData(uuid, playerName, rankId, experience, newMoney, language);
    }

    /** Return a copy with updated experience. */
    public PlayerData withExperience(long newExp) {
        return new PlayerData(uuid, playerName, rankId, newExp, money, language);
    }
}
