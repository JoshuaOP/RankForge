package com.joshuaop.rankforge.protection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * General-purpose token-bucket rate limiter.
 * Tracks last-action timestamps per UUID to enforce cooldowns.
 *
 * Used for API access protection and command throttling.
 */
public class RateLimiter {

    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
    private final long cooldownMs;

    public RateLimiter(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    /**
     * Check if the UUID is allowed to act now.
     * If allowed, records the current time for future checks atomically.
     * @return true if allowed, false if still in cooldown or null.
     */
    public boolean tryAcquire(UUID id) {
        if (id == null) return false;
        long now = System.currentTimeMillis();
        final boolean[] allowed = {false};

        lastAction.compute(id, (k, last) -> {
            if (last == null || (now - last) >= cooldownMs) {
                allowed[0] = true;
                return now;
            }
            return last;
        });

        return allowed[0];
    }

    /** Remaining cooldown in milliseconds, or 0 if not in cooldown or null. */
    public long remainingMs(UUID id) {
        if (id == null) return 0;
        Long last = lastAction.get(id);
        if (last == null) return 0;
        long diff = cooldownMs - (System.currentTimeMillis() - last);
        return Math.max(0, diff);
    }

    /** Remove a player's cooldown entry (e.g., on logout). */
    public void remove(UUID id) {
        if (id != null) {
            lastAction.remove(id);
        }
    }

    /** Clean up all expired entries. Call periodically. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        lastAction.entrySet().removeIf(e -> (now - e.getValue()) > cooldownMs);
    }

    public int getTrackedCount() { return lastAction.size(); }
}
