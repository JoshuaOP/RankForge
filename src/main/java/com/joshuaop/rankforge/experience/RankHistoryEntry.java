package com.joshuaop.rankforge.experience;

import java.util.UUID;

/**
 * Immutable record representing a single rank-change event in a player's history.
 * Stored and loaded by {@link RankHistoryManager}.
 */
public record RankHistoryEntry(
        UUID   playerUuid,
        String playerName,
        String fromRankId,
        String toRankId,
        ChangeType type,
        long   timestamp
) {

    /**
     * The reason a rank changed.
     */
    public enum ChangeType {
        /** Player earned a rank-up via requirements. */
        RANKUP,
        /** An admin directly set the player's rank. */
        SET,
        /** The rank was reset to the server default. */
        RESET
    }

    /** Human-readable formatted date-time string (local server time). */
    public String getFormattedTime() {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault());
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dt);
    }

    /** Short display line for in-game history messages. */
    public String toDisplayLine() {
        return "§8[" + getFormattedTime() + "§8] §7"
                + type.name() + "§8: §e" + fromRankId + " §7→ §a" + toRankId;
    }
}
