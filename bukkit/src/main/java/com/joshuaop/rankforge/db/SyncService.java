package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;

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
    private BukkitTask      recoveryTask;

    public SyncService(RankForge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = plugin.getConfig().getLong("sync.interval-ticks", 200L);

        // Snapshot live Bukkit state on the main thread. Only immutable
        // PlayerData records cross into the asynchronous database work.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
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

            var snapshots = cache.snapshotOnlineAndUnexpired();
            if (snapshots.isEmpty()) return;
            plugin.getTaskScheduler().async(() -> {
                int count = 0;
                for (PlayerData data : snapshots) {
                    if (getRepository().save(data)) count++;
                }
                if (count > 0) {
                    plugin.getLogger().fine("Flushed " + count + " player records to storage.");
                }
            });
        }, interval, interval);

    }

    /**
     * Monitors a failed MySQL connection. Recovery writes the newest valid YAML
     * and in-memory snapshots into the verified pool before normal MySQL use
     * resumes; it never imports potentially stale MySQL rows into the cache.
     */
    public void startRecoveryMonitor() {
        if (recoveryTask != null && !recoveryTask.isCancelled()) return;
        if (!plugin.getDatabaseManager().isMysqlConfigured()) return;
        long interval = Math.max(200L,
                plugin.getConfig().getLong("sync.interval-ticks", 200L) * 3L);
        recoveryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::attemptMySQLRecovery, interval, interval);
    }

    private void attemptMySQLRecovery() {
        if (plugin.getDatabaseManager().isConnected()) return;
        if (!plugin.getDatabaseManager().reconnect()) return;

        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml == null) return;

        boolean recovered = true;
        for (PlayerData data : yaml.loadAll()) {
            if (!getRepository().save(data)) {
                recovered = false;
                break;
            }
        }

        if (recovered) {
            CacheManager cache = getCache();
            if (cache != null) {
                for (CacheManager.Entry entry : cache.getCache().values()) {
                    PlayerData data = entry.data();
                    if (data != null && !getRepository().save(data)) {
                        recovered = false;
                        break;
                    }
                }
            }
        }

        if (!recovered) {
            plugin.getDatabaseManager().markUnavailable(
                    new SQLException("MySQL recovery snapshot was not fully persisted."));
        } else {
            plugin.getDatabaseManager().finishRecovery();
            plugin.getLogger().info("MySQL recovery verified; newest YAML/cache snapshots "
                    + "were persisted before failover ended.");
        }
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        if (recoveryTask != null && !recoveryTask.isCancelled()) {
            recoveryTask.cancel();
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

        for (PlayerData data : cache.snapshotOnlineAndUnexpired()) {
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
