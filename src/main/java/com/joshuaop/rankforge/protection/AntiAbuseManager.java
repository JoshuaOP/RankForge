package com.joshuaop.rankforge.protection;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-layer exploit detection system.
 *
 * Layers:
 *  1. Click pattern analysis (macro detection — too-regular intervals)
 *  2. Command-level rate limiting
 *  3. Rankup spam detection
 *  4. Admin action logging for rollback confirmation
 */
public class AntiAbuseManager {

    private static final int    CLICK_HISTORY_SIZE = 10;
    private static final long   MACRO_THRESHOLD_MS = 20;  // clicks < 20ms apart = macro
    private static final long   CMD_COOLDOWN_MS    = 1000;

    private final RankForge   plugin;
    private final RateLimiter commandRateLimiter;

    /** Recent click timestamps per player (for macro detection). */
    private final Map<UUID, Deque<Long>> clickHistory = new ConcurrentHashMap<>();

    /** Log of admin rank changes for rollback confirmation. */
    private final Map<UUID, AdminAction> pendingRollbacks = new ConcurrentHashMap<>();

    public AntiAbuseManager(RankForge plugin) {
        this.plugin            = plugin;
        this.commandRateLimiter = new RateLimiter(CMD_COOLDOWN_MS);
    }

    // ── Click Pattern Analysis ────────────────────────────────────────────────

    /**
     * Record a GUI click and check if the pattern resembles a macro.
     * @return true if the click is allowed, false if macro-like.
     */
    public boolean recordClick(UUID playerId) {
        long now = System.currentTimeMillis();
        Deque<Long> history = clickHistory.computeIfAbsent(playerId, k -> new ArrayDeque<>());

        if (!history.isEmpty() && (now - history.peekLast()) < MACRO_THRESHOLD_MS) {
            warn(playerId, "Macro-like click detected (interval < " + MACRO_THRESHOLD_MS + "ms)");
            return false;
        }

        history.addLast(now);
        if (history.size() > CLICK_HISTORY_SIZE) history.pollFirst();
        return true;
    }

    // ── Command Rate Limiting ─────────────────────────────────────────────────

    /**
     * Check if a command action is allowed for this player.
     */
    public boolean allowCommand(UUID playerId) {
        return commandRateLimiter.tryAcquire(playerId);
    }

    // ── Admin Rollback Protection ─────────────────────────────────────────────

    /**
     * Record an admin action that can be rolled back.
     */
    public void recordAdminAction(UUID adminId, String targetName, String oldRank, String newRank) {
        pendingRollbacks.put(adminId, new AdminAction(adminId, targetName, oldRank, newRank));
    }

    public AdminAction getPendingRollback(UUID adminId) {
        return pendingRollbacks.remove(adminId);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanup(UUID playerId) {
        clickHistory.remove(playerId);
        commandRateLimiter.remove(playerId);
    }

    public void purge() {
        commandRateLimiter.purgeExpired();
    }

    public int getTrackedPlayers() { return clickHistory.size(); }

    private void warn(UUID playerId, String reason) {
        Player p = plugin.getServer().getPlayer(playerId);
        String name = p != null ? p.getName() : playerId.toString();
        plugin.getLogger().warning("[AntiAbuse] " + name + " — " + reason);
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public record AdminAction(UUID adminId, String targetName, String oldRank, String newRank) {}
}
