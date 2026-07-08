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
 *       so the counter is immediately accurate from the first tick of the session.</li>
 *   <li>On QUIT the final counter value is stitched back into the cache entry so the
 *       normal sync/save pipeline persists it without any special handling.</li>
 *   <li>The counter is authoritative for online players; the {@link PlayerData} record
 *       is authoritative for offline players.</li>
 *   <li>Thread-safe: {@link BlockBreakEvent} may fire on the main thread; all map
 *       operations use {@link ConcurrentHashMap} and {@link AtomicLong}.</li>
 * </ul>
 *
 * <h3>Crossplay / Bedrock</h3>
 * All lookups are by {@link UUID}. No player-name or platform assumptions are made,
 * making this fully compatible with Geyser / Floodgate clients.
 */
public class BlockBreakTracker implements Listener {

    /**
     * Decorative grass-type plants that must not count as block breaks.
     * These pass Material.isBlock() but are not real placeable blocks.
     */
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

        // Only count real, tangible blocks — not air, liquids, non-block materials,
        // or decorative grass-type plants (SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN).
        // isAir()   covers AIR, CAVE_AIR, and VOID_AIR.
        // isBlock() is false for purely item-form materials (should never appear here,
        //            but guards against synthetic events fired by other plugins).
        // isLiquid() covers WATER, LAVA, and BUBBLE_COLUMN source/flowing variants.
        if (type.isAir() || !type.isBlock() || block.isLiquid() || DECORATIVE_GRASS.contains(type)) return;

        Player player = event.getPlayer();
        // Silktouch and creative-mode blocks still count — this is a raw activity metric.
        // Servers that want to exclude creative can add a check here:
        //   if (player.getGameMode() == GameMode.CREATIVE) return;
        counters.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0L)).incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Load the persisted lifetime total so getCount() is immediately accurate.
        long stored = loadStoredCount(uuid, event.getPlayer().getName());
        counters.put(uuid, new AtomicLong(stored));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushToCache(uuid);
        // Leave counter in map; CacheManager TTL will handle cleanup.
        // Explicitly remove after flush to free memory now:
        counters.remove(uuid);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current lifetime block-break count for the given UUID.
     * <ul>
     *   <li>Online players  — live counter (accurate to the current tick).</li>
     *   <li>Offline players — falls back to the value persisted in {@link PlayerData}.</li>
     * </ul>
     */
    public long getCount(UUID uuid) {
        AtomicLong counter = counters.get(uuid);
        if (counter != null) return counter.get();

        // Offline player: read directly from cache / storage.
        if (plugin.getRankManager() != null) {
            PlayerData data = plugin.getRankManager().getCacheManager().getRaw(uuid);
            if (data != null) return data.blockBreaks();
        }
        return 0L;
    }

    /**
     * Forcefully set the block-break count for a player (admin override / correction).
     * Also updates the cache immediately so requirement checks reflect the change.
     */
    public void setCount(UUID uuid, long value) {
        long clamped = Math.max(0L, value);
        counters.put(uuid, new AtomicLong(clamped));
        flushToCache(uuid);
    }

    /**
     * Add blocks to a player's counter (e.g. from migration or import).
     */
    public void addCount(UUID uuid, long delta) {
        if (delta <= 0) return;
        counters.computeIfAbsent(uuid, k -> new AtomicLong(0L)).addAndGet(delta);
        flushToCache(uuid);
    }

    /**
     * Flush the in-memory counter for a UUID back into the {@link com.joshuaop.rankforge.db.CacheManager}.
     * Called on quit and by the periodic sync pipeline.
     */
    public void flushToCache(UUID uuid) {
        AtomicLong counter = counters.get(uuid);
        if (counter == null || plugin.getRankManager() == null) return;

        var cacheManager = plugin.getRankManager().getCacheManager();
        PlayerData current = cacheManager.getRaw(uuid);
        if (current == null) return;

        long newTotal = counter.get();
        if (current.blockBreaks() == newTotal) return; // nothing changed

        cacheManager.put(uuid, current.withBlockBreaks(newTotal));
    }

    /**
     * Flush all online counters into the cache. Called before a bulk sync/save.
     */
    public void flushAll() {
        for (UUID uuid : counters.keySet()) {
            flushToCache(uuid);
        }
    }

    /** Returns a snapshot of all active (in-memory) counters. Read-only view. */
    public Map<UUID, AtomicLong> getActiveCounters() {
        return java.util.Collections.unmodifiableMap(counters);
    }

    /** Number of players with an active in-memory counter. */
    public int getTrackedCount() { return counters.size(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Load the persisted block-break count for a player.
     * Prefers cache → YAML/MySQL via repository.
     */
    private long loadStoredCount(UUID uuid, String playerName) {
        if (plugin.getRankManager() == null) return 0L;

        // Use raw (un-stitched) cache data to avoid circular stitching on join.
        PlayerData cached = plugin.getRankManager().getCacheManager().getRaw(uuid);
        if (cached != null) return cached.blockBreaks();

        // Not in cache yet — load from storage (blocks main thread briefly on join,
        // acceptable since player join is always synchronous in Bukkit).
        PlayerData loaded = plugin.getRankManager().getRepository().load(uuid, playerName);
        return loaded != null ? loaded.blockBreaks() : 0L;
    }
}
