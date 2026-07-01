package com.joshuaop.rankforge.tracker;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real-world elapsed playtime per player using wall-clock time.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Uses {@link System#currentTimeMillis()} — entirely independent of server TPS
 *       or Minecraft tick rate. Accurate even during lag spikes, TPS drops, or GC pauses.</li>
 *   <li>On JOIN the persisted lifetime total from {@link PlayerData} is loaded as the
 *       session base. The current wall-clock time is recorded as the session start.</li>
 *   <li>{@link #getPlayTime(UUID)} always returns baseMinutes + elapsed since join,
 *       giving a live, accurate value without requiring a flush.</li>
 *   <li>On QUIT the final total is flushed to the {@link com.joshuaop.rankforge.db.CacheManager}
 *       so the normal sync/save pipeline persists it correctly.</li>
 *   <li>Thread-safe: all maps use {@link ConcurrentHashMap}; reads and flushes are
 *       safe from any thread.</li>
 * </ul>
 *
 * <h3>Storage</h3>
 * Playtime is stored as cumulative minutes (long) in {@link PlayerData#playTime()}.
 * Both YAML and MySQL backends persist and restore this value across restarts.
 * Sub-minute precision is intentionally discarded at flush time — sessions shorter than
 * 60 seconds do not award a minute of playtime, preventing inflated counts.
 *
 * <h3>Migration</h3>
 * Existing data has {@code playTime = 0}. On first join after the update the
 * tracker initialises from the stored value (zero) and begins accumulating from that
 * point. No conversion of the old vanilla {@code PLAY_ONE_MINUTE} statistic is needed
 * because the stat itself was inaccurate (tick-based) and would compound the existing error.
 */
public class PlaytimeTracker implements Listener {

    private final RankForge plugin;

    /**
     * Wall-clock timestamp (ms) at which the player joined this session.
     * Absent if the player is offline.
     */
    private final ConcurrentHashMap<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    /**
     * Lifetime playtime total (minutes) as loaded from storage at the start of this session.
     * We compute the running total as {@code base + elapsed} rather than mutating the base
     * on every flush, which prevents double-counting.
     */
    private final ConcurrentHashMap<UUID, Long> sessionBase = new ConcurrentHashMap<>();

    public PlaytimeTracker(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        long stored = loadStoredMinutes(uuid, event.getPlayer().getName());
        sessionBase.put(uuid, stored);
        sessionStart.put(uuid, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushToCache(uuid);
        sessionBase.remove(uuid);
        sessionStart.remove(uuid);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current lifetime playtime for the player in minutes.
     * <ul>
     *   <li>Online  — live value: base from last save + elapsed since join (whole minutes).</li>
     *   <li>Offline — value stored in {@link PlayerData} via cache or storage.</li>
     * </ul>
     */
    public long getPlayTime(UUID uuid) {
        Long start = sessionStart.get(uuid);
        Long base  = sessionBase.get(uuid);
        if (start != null && base != null) {
            long elapsedMinutes = (System.currentTimeMillis() - start) / 60_000L;
            return base + elapsedMinutes;
        }
        // Offline player — read from cache/storage
        if (plugin.getRankManager() != null) {
            PlayerData data = plugin.getRankManager().getCacheManager().getRaw(uuid);
            if (data != null) return data.playTime();
        }
        return 0L;
    }

    /**
     * Forcefully set the lifetime playtime for a player (admin override / correction).
     * Also flushes the new value into the cache immediately.
     */
    public void setPlayTime(UUID uuid, long minutes) {
        long clamped = Math.max(0L, minutes);
        sessionBase.put(uuid, clamped);
        if (sessionStart.containsKey(uuid)) {
            // Reset the session start so the set value becomes the new baseline
            sessionStart.put(uuid, System.currentTimeMillis());
        }
        flushToCache(uuid);
    }

    /**
     * Flush the current accumulated playtime for the given UUID back into the cache.
     * Called on quit, and by the periodic sync pipeline.
     */
    public void flushToCache(UUID uuid) {
        Long start = sessionStart.get(uuid);
        Long base  = sessionBase.get(uuid);
        if (start == null || base == null) return;
        if (plugin.getRankManager() == null) return;

        var cacheManager = plugin.getRankManager().getCacheManager();
        PlayerData current = cacheManager.getRaw(uuid);
        if (current == null) return;

        long elapsedMinutes = (System.currentTimeMillis() - start) / 60_000L;
        long newTotal = base + elapsedMinutes;

        if (current.playTime() == newTotal) return; // nothing changed

        cacheManager.put(uuid, current.withPlayTime(newTotal));
    }

    /**
     * Flush all online player playtime counters to the cache.
     * Called before a bulk sync/save to ensure storage reflects current session totals.
     */
    public void flushAll() {
        for (UUID uuid : sessionStart.keySet()) {
            flushToCache(uuid);
        }
    }

    /** Number of players currently being tracked (online count). */
    public int getTrackedCount() { return sessionStart.size(); }

    /** Read-only snapshot of active session start times. */
    public Map<UUID, Long> getActiveSessionStarts() {
        return java.util.Collections.unmodifiableMap(sessionStart);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Load the persisted playtime (minutes) for a player from cache or storage.
     * Prefers raw cache → repository (blocks main thread briefly on join, which
     * is acceptable since player join is always synchronous in Bukkit).
     */
    private long loadStoredMinutes(UUID uuid, String playerName) {
        if (plugin.getRankManager() == null) return 0L;

        PlayerData cached = plugin.getRankManager().getCacheManager().getRaw(uuid);
        if (cached != null) return cached.playTime();

        PlayerData loaded = plugin.getRankManager().getRepository().load(uuid, playerName);
        return loaded != null ? loaded.playTime() : 0L;
    }
}
