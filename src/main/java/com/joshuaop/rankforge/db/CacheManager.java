package com.joshuaop.rankforge.db;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, TTL-aware in-memory player data cache.
 * Uses ConcurrentHashMap for lock-free concurrent reads.
 * Entries expire after TTL_MS of inactivity; call purgeExpired() periodically.
 */
public class CacheManager {

    private static final long TTL_MS = 10 * 60 * 1000L; // 10 min default

    private record Entry(PlayerData data, long expiresAt) {}

    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    // ── Write ─────────────────────────────────────────────────────────────────

    public void put(UUID id, PlayerData data) {
        cache.put(id, new Entry(data, System.currentTimeMillis() + TTL_MS));
    }

    public void putAll(Map<UUID, PlayerData> map) {
        long exp = System.currentTimeMillis() + TTL_MS;
        map.forEach((k, v) -> cache.put(k, new Entry(v, exp)));
    }

    public void remove(UUID id) { cache.remove(id); }

    /** Shorten TTL for a player who just logged out — cleaned up soon. */
    public void scheduleCleanup(UUID id) {
        Entry e = cache.get(id);
        if (e != null) cache.put(id, new Entry(e.data(), System.currentTimeMillis() + 60_000L));
    }

    /** Remove all expired entries — call periodically (e.g., every 5 min). */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    public void clear() { cache.clear(); }

    // ── Read ──────────────────────────────────────────────────────────────────

    public PlayerData get(UUID id) {
        Entry e = cache.get(id);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAt()) { cache.remove(id); return null; }
        return e.data();
    }

    public boolean contains(UUID id) {
        Entry e = cache.get(id);
        if (e == null) return false;
        if (System.currentTimeMillis() > e.expiresAt()) { cache.remove(id); return false; }
        return true;
    }

    public int size() { return cache.size(); }

    public Collection<PlayerData> all() {
        return cache.values().stream().map(Entry::data).toList();
    }

    // ── Top Players ───────────────────────────────────────────────────────────

    /**
     * Return the top-N players sorted by rank position (highest first).
     *
     * @param orderedRankIds  rank IDs in progression order (lowest → highest)
     * @param limit           max results
     */
    public List<PlayerData> getTopPlayers(Set<String> orderedRankIds, int limit) {
        List<String> order = new ArrayList<>(orderedRankIds);
        return cache.values().stream()
                .map(Entry::data)
                .sorted(Comparator.comparingInt(d -> {
                    int idx = order.indexOf(d.rankId());
                    return idx < 0 ? Integer.MAX_VALUE : -idx; // negate → highest rank first
                }))
                .limit(limit)
                .toList();
    }
}
