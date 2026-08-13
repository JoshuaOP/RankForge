package com.joshuaop.rankforge.db;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
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
 *   playTime         — cumulative real-world playtime in whole minutes, tracked by
 *                      PlaytimeTracker using wall-clock time (not Minecraft ticks).
 *                      Immune to TPS fluctuations and server lag.
 *   completedRequirements — canonical requirement-type keys (see BypassRegistry.BYPASSABLE)
 *                      that have been manually marked complete for the player's *current*
 *                      rank, e.g. via {@code /rank bypassreq}. Persisted through the same
 *                      storage as the rest of a player's data (YAML or MySQL) so the
 *                      completion survives reloads, restarts, and reconnects. Cleared
 *                      automatically once the player successfully ranks up.
 */
public record PlayerData(
        UUID   uuid,
        String playerName,
        String rankId,
        long   experience,
        double money,
        String language,
        long   blockBreaks,
        long   playTime,
        Set<String> completedRequirements
) {

    /** Canonical constructor — defensively copies the completed-requirements set. */
    public PlayerData {
        completedRequirements = completedRequirements == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(completedRequirements));
    }

    /** Create default PlayerData for a brand-new player. */
    public static PlayerData defaultData(UUID uuid, String playerName, String defaultRank) {
        return new PlayerData(uuid, playerName, defaultRank, 0L, 0.0, "en", 0L, 0L, Set.of());
    }

    // ── Functional copies ─────────────────────────────────────────────────────

    public PlayerData withRank(String newRankId) {
        return new PlayerData(uuid, playerName, newRankId, experience, money, language,
                blockBreaks, playTime, completedRequirements);
    }

    public PlayerData withLanguage(String newLang) {
        return new PlayerData(uuid, playerName, rankId, experience, money, newLang,
                blockBreaks, playTime, completedRequirements);
    }

    public PlayerData withMoney(double newMoney) {
        return new PlayerData(uuid, playerName, rankId, experience, newMoney, language,
                blockBreaks, playTime, completedRequirements);
    }

    public PlayerData withExperience(long newExp) {
        return new PlayerData(uuid, playerName, rankId, newExp, money, language,
                blockBreaks, playTime, completedRequirements);
    }

    public PlayerData withPlayerName(String newPlayerName) {
        return new PlayerData(uuid, newPlayerName, rankId, experience, money, language,
                blockBreaks, playTime, completedRequirements);
    }

    public PlayerData withBlockBreaks(long newBlockBreaks) {
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                Math.max(0L, newBlockBreaks), playTime, completedRequirements);
    }

    public PlayerData withPlayTime(long newPlayTime) {
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                blockBreaks, Math.max(0L, newPlayTime), completedRequirements);
    }

    /** Returns a copy with the given requirement-type key added to the completed set. */
    public PlayerData withCompletedRequirement(String requirementType) {
        if (requirementType == null || requirementType.isBlank()) return this;
        Set<String> updated = new LinkedHashSet<>(completedRequirements);
        updated.add(requirementType.toLowerCase());
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                blockBreaks, playTime, updated);
    }

    /** Returns a copy with the given requirement-type key removed from the completed set. */
    public PlayerData withoutCompletedRequirement(String requirementType) {
        if (requirementType == null || completedRequirements.isEmpty()) return this;
        Set<String> updated = new LinkedHashSet<>(completedRequirements);
        if (!updated.remove(requirementType.toLowerCase())) return this;
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                blockBreaks, playTime, updated);
    }

    /** Returns a copy with the completed-requirements set replaced entirely (e.g. cleared on rank-up). */
    public PlayerData withCompletedRequirements(Set<String> newCompleted) {
        return new PlayerData(uuid, playerName, rankId, experience, money, language,
                blockBreaks, playTime, newCompleted);
    }

    /** Convenience check used by BypassRegistry/RequirementManager for persisted completions. */
    public boolean hasCompletedRequirement(String requirementType) {
        return requirementType != null && completedRequirements.contains(requirementType.toLowerCase());
    }
}
