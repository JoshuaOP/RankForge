package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Thread-safe, TTL-aware in-memory player data cache.
 * Entries expire after TTL of inactivity; call purgeExpired() periodically.
 *
 * Live-stitching injects the current session values for XP, Vault balance,
 * block-break count, and real-world playtime from their respective live systems
 * so callers always receive up-to-date values without triggering a storage read.
 */
public class CacheManager {

    public record Entry(PlayerData data, long expiresAt, boolean activeOnline) {}

    private final RankForge                      plugin;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    private static final long TTL_MS = 10 * 60 * 1000L;

    public CacheManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void put(UUID id, PlayerData data) {
        cache.put(id, new Entry(data, System.currentTimeMillis() + TTL_MS, true));
    }

    public void putAll(Map<UUID, PlayerData> map) {
        long exp = System.currentTimeMillis() + TTL_MS;
        map.forEach((k, v) -> cache.put(k, new Entry(v, exp, true)));
    }

    public void remove(UUID id) { cache.remove(id); }

    /** Drop TTL to 1-minute grace period after logout. */
    public void scheduleCleanup(UUID id) {
        Entry e = cache.get(id);
        if (e != null)
            cache.put(id, new Entry(e.data(), System.currentTimeMillis() + 60_000L, false));
    }

    /** Remove expired offline entries; never evicts active online players. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> !e.getValue().activeOnline() && e.getValue().expiresAt() < now);
    }

    /**
     * Replace the rankId of any cached player whose current rank fails the given validity check.
     * Preserves the original TTL and online-state so the repair is transparent to the sync pipeline.
     * Call this after a ranks.yml reload to fix orphaned rank references.
     */
    public void repairOrphanedRankIds(Predicate<String> isValidRankId, String fallbackRankId) {
        for (Map.Entry<UUID, Entry> entry : cache.entrySet()) {
            PlayerData data = entry.getValue().data();
            if (!isValidRankId.test(data.rankId())) {
                Entry old = entry.getValue();
                cache.put(entry.getKey(),
                        new Entry(data.withRank(fallbackRankId), old.expiresAt(), old.activeOnline()));
            }
        }
    }

    public void clear() { cache.clear(); }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns player data with live XP, Vault balance, block-break count, and playtime
     * stitched in for online players.
     */
    public PlayerData get(UUID id) {
        Entry e = cache.get(id);
        if (e == null) return null;

        long now = System.currentTimeMillis();
        if (!e.activeOnline() && now > e.expiresAt()) {
            cache.remove(id);
            return null;
        }

        if (e.activeOnline())
            cache.put(id, new Entry(e.data(), now + TTL_MS, true));

        return stitchLiveData(e.data(), e.activeOnline());
    }

    /** Raw retrieval without stitching — avoids recursion in internal save paths. */
    public PlayerData getRaw(UUID id) {
        Entry e = cache.get(id);
        return e != null ? e.data() : null;
    }

    public boolean contains(UUID id) {
        Entry e = cache.get(id);
        if (e == null) return false;
        long now = System.currentTimeMillis();
        if (!e.activeOnline() && now > e.expiresAt()) {
            cache.remove(id);
            return false;
        }
        return true;
    }

    public int size() { return cache.size(); }

    public ConcurrentHashMap<UUID, Entry> getCache() { return cache; }

    public Collection<PlayerData> all() {
        return cache.values().stream()
                .map(e -> stitchLiveData(e.data(), e.activeOnline()))
                .toList();
    }

    public Collection<PlayerData> getOnlineAndUnexpired() {
        long now = System.currentTimeMillis();
        return cache.values().stream()
                .filter(e -> e.activeOnline() || e.expiresAt() >= now)
                .map(e -> stitchLiveData(e.data(), e.activeOnline()))
                .toList();
    }

    // ── Live Data Stitching ───────────────────────────────────────────────────

    /**
     * Appends live XP, Vault balance, block-break count, and real-world playtime
     * for online players so cached records never return stale values during an active session.
     *
     * <p>Block breaks — sourced from {@link com.joshuaop.rankforge.tracker.BlockBreakTracker}
     * (exact {@link java.util.concurrent.atomic.AtomicLong} counter).
     *
     * <p>Playtime — sourced from {@link com.joshuaop.rankforge.tracker.PlaytimeTracker}
     * (wall-clock elapsed minutes; independent of server TPS).
     */
    private PlayerData stitchLiveData(PlayerData data, boolean isActiveOnline) {
        if (!isActiveOnline) return data;

        Player player = Bukkit.getPlayer(data.uuid());
        if (player == null || !player.isOnline()) return data;

        // ── Live XP ───────────────────────────────────────────────────────────
        long liveXp = plugin.getExperienceManager() != null
                ? plugin.getExperienceManager().getXp(player)
                : data.experience();

        // ── Live Vault balance ────────────────────────────────────────────────
        double liveMoney = data.money();
        if (plugin.getSoftDependency() != null && plugin.getSoftDependency().hasVault()) {
            try { liveMoney = plugin.getSoftDependency().getBalance(player); }
            catch (Exception ignored) {}
        }

        // ── Live block-break count from BlockBreakTracker ─────────────────────
        long liveBlockBreaks = data.blockBreaks();
        if (plugin.getBlockBreakTracker() != null) {
            liveBlockBreaks = plugin.getBlockBreakTracker().getCount(player.getUniqueId());
        }

        // ── Live playtime from PlaytimeTracker (wall-clock, not ticks) ────────
        long livePlaytime = data.playTime();
        if (plugin.getPlaytimeTracker() != null) {
            livePlaytime = plugin.getPlaytimeTracker().getPlayTime(player.getUniqueId());
        }

        return new PlayerData(
                data.uuid(), player.getName(), data.rankId(),
                liveXp, liveMoney, data.language(), liveBlockBreaks, livePlaytime
        );
    }
}
