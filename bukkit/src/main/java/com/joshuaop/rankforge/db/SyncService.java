package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Periodically flushes cached player data to MySQL asynchronously.
 * Only started when MySQL is connected. YAML sync is handled separately in RankForge.
 * Interval is configured via sync.interval-ticks (default: 200 ticks = 10 s).
 *
 * Before each flush the BlockBreakTracker and PlaytimeTracker counters are flushed
 * into the cache so the persisted values are always up-to-date.
 */
public class SyncService {

    private final RankForge     plugin;
    private BukkitTask          task;
    private BukkitTask          recoveryTask;
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private final Object        recoveryTaskLock = new Object();
    private volatile long       nextRecoveryAttemptAtMillis;
    private volatile long       recoveryBackoffTicks;

    public SyncService(RankForge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Ensure Bukkit task timer is always registered on the main server thread
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::start);
            return;
        }

        if (task != null && !task.isCancelled()) return;
        long interval = plugin.getConfig().getLong("sync.interval-ticks", 200L);

        // Snapshot live Bukkit state on the main thread. Only immutable
        // PlayerData records cross into the asynchronous database work.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null || !db.isMysqlConfigured() || !db.isReadyForReads()) {
                return;
            }

            CacheManager cache = getCache();
            RankDataRepository repo = getRepository();
            if (cache == null || repo == null) return;

            // Flush live counters on main thread before saving snapshots
            if (plugin.getBlockBreakTracker() != null) {
                plugin.getBlockBreakTracker().flushAll();
            }

            if (plugin.getPlaytimeTracker() != null) {
                plugin.getPlaytimeTracker().flushAll();
            }

            var snapshots = cache.snapshotOnlineAndUnexpired();
            if (snapshots.isEmpty()) return;

            // Execute database save operations asynchronously
            plugin.getTaskScheduler().async(() -> {
                int count = 0;
                for (PlayerData data : snapshots) {
                    if (data != null && repo.save(data)) {
                        count++;
                    }
                }
                if (count > 0 && plugin.getLogger().isLoggable(Level.FINE)) {
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
        synchronized (recoveryTaskLock) {
            if (recoveryTask != null && !recoveryTask.isCancelled()) return;
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null || !db.isMysqlConfigured()) return;

            long interval = Math.max(200L, plugin.getConfig().getLong("sync.interval-ticks", 200L) * 3L);
            recoveryBackoffTicks = interval;
            recoveryTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin, this::attemptMySQLRecovery, interval, interval);
        }
    }

    private void attemptMySQLRecovery() {
        if (!recoveryInProgress.compareAndSet(false, true)) return;
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null || db.isConnected()) return;

            long now = System.currentTimeMillis();
            if (now < nextRecoveryAttemptAtMillis) return;

            if (!db.reconnect()) {
                scheduleRecoveryRetry(now);
                return;
            }

            YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
            RankDataRepository repo = getRepository();
            if (yaml == null || repo == null) {
                db.markUnavailable(new SQLException("YAML recovery source or repository is unavailable."));
                scheduleRecoveryRetry(now);
                return;
            }

            // A UUID can exist in both sources. Merge first so recovery writes
            // each record once; cache is the live in-memory source of truth.
            Map<UUID, PlayerData> snapshots = new LinkedHashMap<>();

            // 1. Load valid snapshots from YAML storage (Async I/O safe)
            try {
                for (PlayerData data : yaml.loadAll()) {
                    if (data != null && data.isValidFor(data.uuid())) {
                        snapshots.put(data.uuid(), data);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to read YAML storage during recovery", e);
            }

            // 2. Safely capture live trackers & in-memory cache snapshots on primary thread
            CompletableFuture<Collection<PlayerData>> cacheFuture = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (plugin.getBlockBreakTracker() != null) {
                        plugin.getBlockBreakTracker().flushAll();
                    }
                    if (plugin.getPlaytimeTracker() != null) {
                        plugin.getPlaytimeTracker().flushAll();
                    }
                    CacheManager cache = getCache();
                    if (cache != null) {
                        cacheFuture.complete(cache.snapshotOnlineAndUnexpired());
                    } else {
                        cacheFuture.complete(Collections.emptyList());
                    }
                } catch (Throwable t) {
                    cacheFuture.completeExceptionally(t);
                }
            });

            Collection<PlayerData> cacheSnapshots;
            try {
                cacheSnapshots = cacheFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to retrieve live player snapshot on main thread during recovery", e);
                scheduleRecoveryRetry(now);
                return;
            }

            for (PlayerData data : cacheSnapshots) {
                if (data != null && data.isValidFor(data.uuid())) {
                    snapshots.put(data.uuid(), data);
                }
            }

            // 3. Persist snapshots to recovered database pool using explicit recovery save call
            for (PlayerData data : snapshots.values()) {
                if (!repo.saveForRecovery(data)) {
                    db.markUnavailable(new SQLException("MySQL recovery snapshot was not fully persisted."));
                    scheduleRecoveryRetry(now);
                    return;
                }
            }

            db.finishRecovery();
            recoveryBackoffTicks = Math.max(200L, plugin.getConfig().getLong("sync.interval-ticks", 200L) * 3L);
            nextRecoveryAttemptAtMillis = 0L;
            plugin.getLogger().info("MySQL connection restored. Synchronizing data...");

            // Resume regular sync timer on main server thread
            plugin.getServer().getScheduler().runTask(plugin, this::start);
            plugin.getLogger().info("MySQL recovery completed.");

            // Stop recovery task since database is healthy
            synchronized (recoveryTaskLock) {
                if (recoveryTask != null && !recoveryTask.isCancelled()) {
                    recoveryTask.cancel();
                    recoveryTask = null;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error during MySQL recovery attempt", e);
            scheduleRecoveryRetry(System.currentTimeMillis());
        } finally {
            recoveryInProgress.set(false);
        }
    }

    private void scheduleRecoveryRetry(long nowMillis) {
        long delayTicks = Math.max(200L, recoveryBackoffTicks);
        nextRecoveryAttemptAtMillis = nowMillis + delayTicks * 50L;
        // Cap exponential delay to prevent spam while retrying periodically (max 10 mins)
        recoveryBackoffTicks = Math.min(delayTicks * 2L, 20L * 60L * 10L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        synchronized (recoveryTaskLock) {
            if (recoveryTask != null && !recoveryTask.isCancelled()) {
                recoveryTask.cancel();
                recoveryTask = null;
            }
        }
    }

    /**
     * Instantly pushes all active cache entries to the database.
     * Invoked synchronously during onDisable to safeguard player data.
     * Block-break and playtime counters are flushed first.
     */
    public void flushNow() {
        CacheManager cache = getCache();
        RankDataRepository repo = getRepository();
        if (cache == null || repo == null) return;

        if (plugin.getBlockBreakTracker() != null) {
            plugin.getBlockBreakTracker().flushAll();
        }

        if (plugin.getPlaytimeTracker() != null) {
            plugin.getPlaytimeTracker().flushAll();
        }

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && db.isMysqlConfigured() && db.isReadyForReads()) {
            for (PlayerData data : cache.snapshotOnlineAndUnexpired()) {
                if (data != null) {
                    repo.save(data);
                }
            }
        }
    }

    private CacheManager getCache() {
        return plugin.getRankManager() != null
                ? plugin.getRankManager().getCacheManager()
                : null;
    }

    private RankDataRepository getRepository() {
        return plugin.getRankManager() != null ? plugin.getRankManager().getRepository() : null;
    }
}
