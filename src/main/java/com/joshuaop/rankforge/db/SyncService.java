package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically flushes cached player data to MySQL asynchronously.
 * Only started when MySQL is connected. YAML sync is handled separately in RankForge.
 * Interval is configured via sync.interval-ticks (default: 200 ticks = 10 s).
 *
 * Before each flush the BlockBreakTracker and PlaytimeTracker counters are flushed
 * into the cache so the persisted values are always up-to-date.
 */
public class SyncService {

    private final RankForge plugin;
    private BukkitTask      task;

    public SyncService(RankForge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = plugin.getConfig().getLong("sync.interval-ticks", 200L);

        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            CacheManager cache = getCache();
            if (cache == null) return;

            // Flush all live block-break counters into the cache before saving.
            if (plugin.getBlockBreakTracker() != null) {
                plugin.getBlockBreakTracker().flushAll();
            }

            // Flush all live playtime counters into the cache before saving.
            if (plugin.getPlaytimeTracker() != null) {
                plugin.getPlaytimeTracker().flushAll();
            }

            int count = 0;
            for (PlayerData data : cache.getOnlineAndUnexpired()) {
                getRepository().save(data);
                count++;
            }
            if (count > 0) {
                plugin.getLogger().fine("[Sync] Flushed " + count + " player records to MySQL.");
            }
        }, interval, interval);

        plugin.getLogger().info("[Sync] MySQL sync started (every " + interval + " ticks).");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            plugin.getLogger().info("[Sync] Background sync stopped.");
        }
    }

    /**
     * Instantly pushes all active cache entries to the database.
     * Invoked synchronously during onDisable to safeguard player data.
     * Block-break and playtime counters are flushed first.
     */
    public void flushNow() {
        CacheManager cache = getCache();
        if (cache == null) return;

        if (plugin.getBlockBreakTracker() != null) {
            plugin.getBlockBreakTracker().flushAll();
        }

        if (plugin.getPlaytimeTracker() != null) {
            plugin.getPlaytimeTracker().flushAll();
        }

        for (PlayerData data : cache.getOnlineAndUnexpired()) {
            getRepository().save(data);
        }
    }

    private CacheManager getCache() {
        return plugin.getRankManager() != null
                ? plugin.getRankManager().getCacheManager()
                : null;
    }

    private RankDataRepository getRepository() {
        return plugin.getRankManager().getRepository();
    }
}
