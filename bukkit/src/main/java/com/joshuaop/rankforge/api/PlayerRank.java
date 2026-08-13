package com.joshuaop.rankforge.api;

import java.util.UUID;

/**
 * Immutable snapshot of a player's rank state, exposed via the public API.
 * External plugins should depend on this record, not on internal classes.
 */
public record PlayerRank(
        UUID   playerUuid,
        String playerName,
        String rankId,
        String displayName,
        String nextRankId,
        double progressPercent
) {
    /** True if this is the highest rank (no next rank). */
    public boolean isMaxRank() {
        return nextRankId == null || nextRankId.isBlank();
    }

    /** Progress as a formatted string, e.g. "73.4%". */
    public String progressString() {
        return String.format("%.1f%%", progressPercent);
    }
}
