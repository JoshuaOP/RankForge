package com.joshuaop.rankforge.tracker;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the exact cumulative number of blocks broken per player.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>An {@link AtomicLong} counter per UUID holds the fully-accumulated total
 *       (not just the session delta). This means getCount() always returns the
 *       correct lifetime value usable for requirement checks with no extra maths.</li>
 *   <li>On JOIN the persisted value from {@link PlayerData} is loaded into the counter
 *       safely via atomic compute functions.</li>
 *   <li>On QUIT the final counter value is stitched back into the cache entry so the
 *       normal sync/save pipeline persists it without any special handling.</li>
 * </ul>
 */
public class BlockBreakTracker implements Listener {

    private static final Set<Material> DECORATIVE_GRASS = EnumSet.of(
            Material.SHORT_GRASS,
            Material.TALL_GRASS,
            Material.FERN,
            Material.LARGE_FERN
    );

    private final RankForge                         plugin;
    private final ConcurrentHashMap<UUID, AtomicLong> counters = new ConcurrentHashMap<>();

    public BlockBreakTracker(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (type.isAir() || !type.isBlock() || block.isLiquid() || DECORATIVE_GRASS.contains(type)) return;

        Player player = event.getPlayer();
        counters.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0L)).incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        long stored = loadStoredCount(uuid, event.getPlayer().getName());
        
        // Atomically initialize or update the counter without wiping out concurrent increments
        counters.compute(uuid, (k, existing) -> {
            if (existing == null) {
                return new AtomicLong(stored);
            }
            // If an entry somehow already exists, ensure we take the maximum or add them safely
            long currentVal = existing.get();
            if (stored > currentVal) {
                existing.set(stored);
            }
            return existing;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushToCache(uuid);
        counters.remove(uuid);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public long getCount(UUID uuid) {
        AtomicLong counter = counters.get(uuid);
        if (counter != null) return counter.get();

        if (plugin.getRankManager() != null) {
            PlayerData data = plugin.getRankManager().getCacheManager().getRaw(uuid);
            if (data != null) return data.blockBreaks();
        }
        return 0L;
    }

    public void setCount(UUID uuid, long value) {
        long clamped = Math.max(0L, value);
        counters.put(uuid, new AtomicLong(clamped));
        flushToCache(uuid);
    }

    public void addCount(UUID uuid, long delta) {
        if (delta <= 0) return;
        counters.computeIfAbsent(uuid, k -> new AtomicLong(0L)).addAndGet(delta);
        flushToCache(uuid);
    }

    public void flushToCache(UUID uuid) {
        AtomicLong counter = counters.get(uuid);
        if (counter == null || plugin.getRankManager() == null) return;

        var cacheManager = plugin.getRankManager().getCacheManager();
        PlayerData current = cacheManager.getRaw(uuid);
        if (current == null) return;

        long newTotal = counter.get();
        if (current.blockBreaks() == newTotal) return;

        cacheManager.put(uuid, current.withBlockBreaks(newTotal));
    }

    public void flushAll() {
        for (UUID uuid : counters.keySet()) {
            flushToCache(uuid);
        }
    }

    public Map<UUID, AtomicLong> getActiveCounters() {
        return java.util.Collections.unmodifiableMap(counters);
    }

    public int getTrackedCount() { return counters.size(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long loadStoredCount(UUID uuid, String playerName) {
        if (plugin.getRankManager() == null) return 0L;

        PlayerData cached = plugin.getRankManager().getCacheManager().getRaw(uuid);
        if (cached != null) return cached.blockBreaks();

        PlayerData loaded = plugin.getRankManager().getRepository().load(uuid, playerName);
        return loaded != null ? loaded.blockBreaks() : 0L;
    }
}
