package com.joshuaop.rankforge.manager;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of per-player requirement bypasses granted by administrators
 * via {@code /rank bypassreq <player> <requirement>}.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>A bypass is granted instantly and takes effect on the next requirement
 *       evaluation (meetsAll / getUnmet / getRequirementProgress).</li>
 *   <li>All bypasses for a player are cleared automatically when the player
 *       successfully ranks up (see {@link com.joshuaop.rankforge.api.RankService}).</li>
 *   <li>Bypasses granted via {@code /rank bypassreq} are mirrored into the player's
 *       {@link com.joshuaop.rankforge.db.PlayerData#completedRequirements()} and saved
 *       through the plugin's normal storage (YAML or MySQL), so they survive
 *       {@code /rank reload}, server restarts, and player disconnect/reconnect.
 *       This in-memory registry is repopulated from that persisted data on join
 *       via {@link #loadPersisted(UUID, java.util.Collection)}.</li>
 * </ul>
 *
 * <h3>Bypass keys</h3>
 * Keys match the canonical requirement-type identifiers used by
 * {@link RequirementManager} and {@link com.joshuaop.rankforge.api.ProgressService}:
 * {@code block-breaks, mob-kills, statistic, quests, items, permission, worlds, custom}.
 *
 * <h3>Excluded types</h3>
 * {@code money}, {@code xp-level}, and {@code playtime} are never bypassable —
 * they use their own deduction / accumulation systems.
 */
public class BypassRegistry {

    /** Requirement types that may never be bypassed via this command. */
    public static final Set<String> NON_BYPASSABLE = Set.of(
            "money", "xp-level", "playtime"
    );

    /** Canonical keys for all bypassable requirement types. */
    public static final Set<String> BYPASSABLE = Set.of(
            "block-breaks", "mob-kills", "statistic",
            "quests", "items", "permission", "worlds", "custom"
    );

    /** uuid → set of bypassed requirement-type keys */
    private final ConcurrentHashMap<UUID, Set<String>> bypasses = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Grant a bypass for the given requirement type.
     *
     * @return {@code true} on success; {@code false} if the type is not bypassable.
     */
    public boolean grant(UUID uuid, String requirementType) {
        String key = requirementType.toLowerCase();
        if (!BYPASSABLE.contains(key)) return false;
        bypasses.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(key);
        return true;
    }

    /**
     * Returns {@code true} if the player currently has a bypass for the given type.
     */
    public boolean isBypassed(UUID uuid, String requirementType) {
        Set<String> set = bypasses.get(uuid);
        return set != null && set.contains(requirementType.toLowerCase());
    }

    /**
     * Returns {@code true} if the player has at least one active bypass.
     */
    public boolean hasAny(UUID uuid) {
        Set<String> set = bypasses.get(uuid);
        return set != null && !set.isEmpty();
    }

    /**
     * Returns an unmodifiable snapshot of the player's active bypass types.
     */
    public Set<String> getActive(UUID uuid) {
        Set<String> set = bypasses.get(uuid);
        return set != null ? Collections.unmodifiableSet(set) : Set.of();
    }

    /**
     * Remove all bypasses for a player.
     * Called automatically after a successful rank-up.
     */
    public void clearAll(UUID uuid) {
        bypasses.remove(uuid);
    }

    /**
     * Remove a specific bypass for a player.
     *
     * @return {@code true} if the bypass existed and was removed.
     */
    public boolean remove(UUID uuid, String requirementType) {
        Set<String> set = bypasses.get(uuid);
        if (set == null) return false;
        return set.remove(requirementType.toLowerCase());
    }

    /**
     * Seeds the in-memory bypass set for a player from previously persisted data
     * (e.g. {@code PlayerData.completedRequirements()}), without touching storage.
     * Called on player join so bypasses granted in a previous session — or before
     * a server restart — remain active immediately.
     */
    public void loadPersisted(UUID uuid, java.util.Collection<String> persisted) {
        if (uuid == null || persisted == null || persisted.isEmpty()) return;
        for (String type : persisted) {
            if (type == null) continue;
            String key = type.toLowerCase();
            if (BYPASSABLE.contains(key)) {
                bypasses.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(key);
            }
        }
    }
}
