package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private volatile long nextRecoveryAttemptAtMillis;
    private volatile long recoveryBackoffTicks;

    public SyncService(RankForge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
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
        recoveryBackoffTicks = interval;
        recoveryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::attemptMySQLRecovery, interval, interval);
    }

    private void attemptMySQLRecovery() {
        if (!recoveryInProgress.compareAndSet(false, true)) return;
        try {
            if (plugin.getDatabaseManager().isConnected()) return;
            long now = System.currentTimeMillis();
            if (now < nextRecoveryAttemptAtMillis) return;

            if (!plugin.getDatabaseManager().reconnect()) {
                scheduleRecoveryRetry(now);
                return;
            }

            YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
            if (yaml == null) {
                plugin.getDatabaseManager().markUnavailable(
                        new SQLException("YAML recovery source is unavailable."));
                scheduleRecoveryRetry(now);
                return;
            }

            // A UUID can exist in both sources.  Merge first so recovery writes
            // each record once; cache is the live in-memory source of truth when
            // it contains a valid record, matching normal save semantics.
            Map<java.util.UUID, PlayerData> snapshots = new LinkedHashMap<>();
            for (PlayerData data : yaml.loadAll()) {
                if (data != null && data.isValidFor(data.uuid())) {
                    snapshots.put(data.uuid(), data);
                }
            }
            CacheManager cache = getCache();
            if (cache != null) {
                for (CacheManager.Entry entry : cache.getCache().values()) {
                    PlayerData data = entry.data();
                    if (data != null && data.isValidFor(data.uuid())) {
                        snapshots.put(data.uuid(), data);
                    }
                }
            }

            for (PlayerData data : snapshots.values()) {
                if (!getRepository().saveForRecovery(data)) {
                    plugin.getDatabaseManager().markUnavailable(
                            new SQLException("MySQL recovery snapshot was not fully persisted."));
                    scheduleRecoveryRetry(now);
                    return;
                }
            }

            plugin.getDatabaseManager().finishRecovery();
            recoveryBackoffTicks = Math.max(200L,
                    plugin.getConfig().getLong("sync.interval-ticks", 200L) * 3L);
            nextRecoveryAttemptAtMillis = 0L;
            plugin.getLogger().info("MySQL recovery verified; newest YAML/cache snapshots "
                    + "were persisted before failover ended.");
            // If MySQL was unavailable during startup, the normal flush task was
            // never created.  Resume it only after recovery has been verified.
            start();
        } finally {
            recoveryInProgress.set(false);
        }
    }

    private void scheduleRecoveryRetry(long nowMillis) {
        long delayTicks = Math.max(200L, recoveryBackoffTicks);
        nextRecoveryAttemptAtMillis = nowMillis + delayTicks * 50L;
        recoveryBackoffTicks = Math.min(delayTicks * 2L, 20L * 60L * 10L);
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
