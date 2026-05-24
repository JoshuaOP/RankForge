package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically flushes cached player data to the database (MySQL) asynchronously.
 * Only started when MySQL is connected. YAML sync is handled separately in RankForge.
 * Interval is configured via sync.interval-ticks (default: 200 ticks = 10 s).
 */
public class SyncService {

    private final RankForge          plugin;
    private final RankDataRepository repository;
    private BukkitTask               task;

    public SyncService(RankForge plugin) {
        this.plugin     = plugin;
        this.repository = new RankDataRepository(plugin, plugin.getRankManager().getCacheManager());
    }

    public void start() {
        long interval = plugin.getConfig().getLong("sync.interval-ticks", 200L);

        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            CacheManager cache = getSharedCache();
            if (cache == null) return;

            int count = 0;
            for (PlayerData data : cache.all()) {
                repository.save(data);
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

    public void flushNow() {
        CacheManager cache = getSharedCache();
        if (cache == null) return;
        for (PlayerData data : cache.all()) {
            repository.save(data);
        }
    }

    private CacheManager getSharedCache() {
        return plugin.getRankManager() != null
                ? plugin.getRankManager().getCacheManager()
                : null;
    }
}
