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
     * @return true if acquired (safe to proceed), false if already processing.
     */
    public boolean acquire(UUID playerId) {
        return processing.add(playerId);
    }

    /** Release the rankup lock after processing is complete. */
    public void release(UUID playerId) {
        processing.remove(playerId);
    }

    /** @return true if this player has a rankup in progress. */
    public boolean isProcessing(UUID playerId) {
        return processing.contains(playerId);
    }

    public int getQueueSize() { return processing.size(); }
}
