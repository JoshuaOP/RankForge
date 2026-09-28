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
 *       session base, with clock-skew protection.</li>
 *   <li>Thread-safe: all maps use {@link ConcurrentHashMap}; reads and flushes are
 *       safe from any thread.</li>
 * </ul>
 */
public class PlaytimeTracker implements Listener {

    private final RankForge plugin;

    /**
     * Internal container holding a player's session baseline and start timestamp.
     */
    private record SessionData(long baseMinutes, long startTimeMillis) {}

    private final ConcurrentHashMap<UUID, SessionData> activeSessions = new ConcurrentHashMap<>();

    public PlaytimeTracker(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        UUID uuid = player.getUniqueId();
        long stored = loadStoredMinutes(uuid, player.getName());
        
        activeSessions.put(uuid, new SessionData(stored, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        UUID uuid = player.getUniqueId();
        flushToCache(uuid);
        activeSessions.remove(uuid);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current lifetime playtime for the player in minutes.
     */
    public long getPlayTime(UUID uuid) {
        if (uuid == null) return 0L;
        
        SessionData session = activeSessions.get(uuid);
        if (session != null) {
            long elapsedMillis = System.currentTimeMillis() - session.startTimeMillis();
            if (elapsedMillis < 0) elapsedMillis = 0L; // Guard against system clock skew/backwards jumps
            long elapsedMinutes = elapsedMillis / 60_000L;
            return session.baseMinutes() + elapsedMinutes;
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
     */
    public void setPlayTime(UUID uuid, long minutes) {
        if (uuid == null) return;
        long clamped = Math.max(0L, minutes);
        
        activeSessions.put(uuid, new SessionData(clamped, System.currentTimeMillis()));
        flushToCache(uuid);
    }

    /**
     * Flush the current accumulated playtime for the given UUID back into the cache.
     */
    public void flushToCache(UUID uuid) {
        if (uuid == null || plugin.getRankManager() == null) return;

        SessionData session = activeSessions.get(uuid);
        if (session == null) return;

        var cacheManager = plugin.getRankManager().getCacheManager();
        PlayerData current = cacheManager.getRaw(uuid);
        if (current == null) return;

        long elapsedMillis = System.currentTimeMillis() - session.startTimeMillis();
        if (elapsedMillis < 0) elapsedMillis = 0L;
        
        long elapsedMinutes = elapsedMillis / 60_000L;
        long newTotal = session.baseMinutes() + elapsedMinutes;

        if (current.playTime() == newTotal) return;

        cacheManager.put(uuid, current.withPlayTime(newTotal));
    }

    /**
     * Flush all online player playtime counters to the cache.
     */
    public void flushAll() {
        for (UUID uuid : activeSessions.keySet()) {
            flushToCache(uuid);
        }
    }

    /** Number of players currently being tracked (online count). */
    public int getTrackedCount() { return activeSessions.size(); }

    /** Read-only snapshot of active session data. */
    public Map<UUID, Long> getActiveSessionStarts() {
        Map<UUID, Long> starts = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, SessionData> entry : activeSessions.entrySet()) {
            starts.put(entry.getKey(), entry.getValue().startTimeMillis());
        }
        return java.util.Collections.unmodifiableMap(starts);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long loadStoredMinutes(UUID uuid, String playerName) {
        if (plugin.getRankManager() == null) return 0L;

        PlayerData cached = plugin.getRankManager().getCacheManager().getRaw(uuid);
        if (cached != null) return cached.playTime();

        PlayerData loaded = plugin.getRankManager().getRepository().load(uuid, playerName);
        return loaded != null ? loaded.playTime() : 0L;
    }
}
