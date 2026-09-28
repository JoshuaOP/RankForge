package com.joshuaop.rankforge.protection;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents simultaneous rank-up calculations for the same player.
 * Ensures only one rankup operation runs at a time per UUID.
 *
 * Usage: acquire() before processing, release() when done.
 */
public class RankupQueue {

    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    /**
     * Attempt to acquire the rankup lock for this player.
     * @return true if acquired (safe to proceed), false if already processing or null.
     */
    public boolean acquire(UUID playerId) {
        if (playerId == null) return false;
        return processing.add(playerId);
    }

    /** Release the rankup lock after processing is complete. */
    public void release(UUID playerId) {
        if (playerId == null) return;
        processing.remove(playerId);
    }

    /** @return true if this player has a rankup in progress. */
    public boolean isProcessing(UUID playerId) {
        if (playerId == null) return false;
        return processing.contains(playerId);
    }

    public int getQueueSize() { return processing.size(); }
}
