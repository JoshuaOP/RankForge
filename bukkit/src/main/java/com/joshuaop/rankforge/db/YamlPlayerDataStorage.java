package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML-based player data storage.
 * Default fallback when MySQL is unavailable.
 * Path: plugins/RankForge/data/playerdata.yml
 *
 * Schema versions:
 *   v1 — legacy (legacy-rank-node field)
 *   v2 — experience, money, language fields
 *   v3 — adds block-breaks (BlockBreakTracker exact counter)
 *   v4 — adds playtime-minutes (real wall-clock playtime, not tick-based)
 *   v5 — adds completed-requirements (requirement-type keys manually completed via
 *        /rank bypassreq for the player's current rank; cleared automatically on rank-up)
 */
public class YamlPlayerDataStorage {

    private static final int CURRENT_DATA_VERSION = 5;

    private final RankForge       plugin;
    private final File            dataFile;
    private final Logger          logger;
    private YamlConfiguration     yaml;
    private final Object          writeLock = new Object();
    private final Object          fileWriteLock = new Object();
    private final Map<UUID, Long>  latestPlayerWrite = new HashMap<>();
    private YamlSaveSnapshot      pendingSnapshot;
    private YamlSaveSnapshot      inFlightSnapshot;
    private BukkitTask             asyncWriterTask;
    private long                  writeGeneration;
    private long                  writerToken;
    private int                   consecutiveWriteFailures;
    private boolean               asyncWriteScheduled;
    private boolean               acceptingWrites = true;
    private boolean               writerShutdown;
    private static final long     EMERGENCY_FALLBACK_TIMEOUT_MILLIS = 5_000L;
    private static final long     SHUTDOWN_WAIT_TIMEOUT_MILLIS = 10_000L;
    private static final int      MAX_AUTOMATIC_RETRIES = 5;
    private static final long     RETRY_BASE_DELAY_TICKS = 20L;
    private static final long     RETRY_MAX_DELAY_TICKS = 20L * 30L;

    /**
     * Immutable snapshot prepared on the main thread. The YAML document is serialized
     * before dispatch so the async writer only performs file I/O.
     */
    private record YamlSaveSnapshot(
            String yamlContent,
            List<UUID> affectedUuids,
            List<SaveOperation> operations
    ) {
        private YamlSaveSnapshot {
            affectedUuids = List.copyOf(affectedUuids);
            operations = List.copyOf(operations);
        }
    }

    /**
     * Result state belongs to one save request rather than to the storage instance.
     * An operation remains pending while its snapshot is retried. It reaches exactly
     * one terminal state after a write succeeds or the bounded retry budget is spent.
     */
    private static final class SaveOperation {
        private enum State { PENDING, SUCCEEDED, FAILED }

        private final CountDownLatch completion = new CountDownLatch(1);
        private volatile State state = State.PENDING;

        private synchronized void complete(boolean succeeded) {
            if (state == State.PENDING) {
                state = succeeded ? State.SUCCEEDED : State.FAILED;
                completion.countDown();
            }
        }

        private boolean await(long timeoutMillis) {
            try {
                if (!completion.await(timeoutMillis, TimeUnit.MILLISECONDS)) return false;
                return state == State.SUCCEEDED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    public YamlPlayerDataStorage(RankForge plugin) {
        this.plugin  = plugin;
        this.logger  = plugin.getLogger();
        File dataDir = new File(plugin.getDataFolder(), "data");
        try {
            Files.createDirectories(dataDir.toPath());
            if (!Files.isDirectory(dataDir.toPath())) {
                throw new IOException("Required player-data path is not a directory: " + dataDir);
            }
        } catch (IOException | SecurityException e) {
            throw new IllegalStateException("Could not create player-data directory "
                    + dataDir + "; YAML persistence is unavailable.", e);
        }
        this.dataFile = new File(dataDir, "playerdata.yml");
        load();
        checkAndMigrateSchema();
    }

    // ── Load / Init ───────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            yaml = new YamlConfiguration();
            yaml.set("data-version", CURRENT_DATA_VERSION);
            if (!persist(yaml)) {
                throw new IllegalStateException("Could not initialize playerdata.yml; "
                        + "YAML persistence is unavailable.");
            }
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void checkAndMigrateSchema() {
        int savedVersion = yaml.getInt("data-version", 1);
        if (savedVersion > CURRENT_DATA_VERSION) {
            acceptingWrites = false;
            plugin.getLogger().severe("playerdata.yml uses unsupported future schema v"
                    + savedVersion + " (supported through v" + CURRENT_DATA_VERSION
                    + "). YAML writes are disabled to protect the file.");
            return;
        }
        if (savedVersion >= CURRENT_DATA_VERSION) return;

        plugin.getLogger().info("Migrating player data v" + savedVersion
                + " → v" + CURRENT_DATA_VERSION + "...");

        YamlConfiguration migrated = new YamlConfiguration();
        try {
            migrated.loadFromString(yaml.saveToString());
            if (savedVersion < 2) migrateV1ToV2(migrated);
            if (savedVersion < 3) migrateV2ToV3(migrated);
            if (savedVersion < 4) migrateV3ToV4(migrated);
            if (savedVersion < 5) migrateV4ToV5(migrated);
            migrated.set("data-version", CURRENT_DATA_VERSION);
        } catch (Exception e) {
            acceptingWrites = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Player data migration failed before persistence; original data was retained.", e);
            return;
        }
        if (!persist(migrated)) {
            acceptingWrites = false;
            plugin.getLogger().severe("Player data migration could not be persisted. "
                    + "The original file remains intact and YAML writes are disabled.");
            return;
        }
        yaml = migrated;
        plugin.getLogger().info("Player data migration complete.");
    }

    /** v1 → v2: rename legacy-rank-node → rank */
    private void migrateV1ToV2(YamlConfiguration target) {
        ConfigurationSection players = target.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr;
            if (target.contains(path + ".legacy-rank-node")) {
                target.set(path + ".rank", target.getString(path + ".legacy-rank-node"));
                target.set(path + ".legacy-rank-node", null);
            }
        }
    }

    /** v2 → v3: add block-breaks field defaulting to 0 for all existing entries */
    private void migrateV2ToV3(YamlConfiguration target) {
        ConfigurationSection players = target.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr + ".block-breaks";
            if (!target.contains(path)) {
                target.set(path, 0L);
            }
        }
    }

    /**
     * v3 → v4: add playtime-minutes field defaulting to 0 for all existing entries.
     * No conversion from the old vanilla PLAY_ONE_MINUTE statistic is performed because
     * the tick-based stat is inherently inaccurate and would propagate that error forward.
     * Players simply begin accumulating real-world playtime from this point onward.
     */
    private void migrateV3ToV4(YamlConfiguration target) {
        ConfigurationSection players = target.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr + ".playtime-minutes";
            if (!target.contains(path)) {
                target.set(path, 0L);
            }
        }
    }

    /**
     * v4 → v5: add completed-requirements field defaulting to an empty list for all
     * existing entries. No conversion is required since this is a brand-new field.
     */
    private void migrateV4ToV5(YamlConfiguration target) {
        ConfigurationSection players = target.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            String path = "players." + uuidStr + ".completed-requirements";
            if (!target.contains(path)) {
                target.set(path, new ArrayList<String>());
            }
        }
    }

    // ── Player Read/Write ─────────────────────────────────────────────────────

    public PlayerData loadPlayer(UUID uuid, String playerName) {
        synchronized (writeLock) {
            String path = "players." + uuid;
            if (!yaml.contains(path)) {
                String defaultRank = plugin.getRankManager() != null
                        ? plugin.getRankManager().getDefaultRankId() : "Guest";
                PlayerData def = PlayerData.defaultData(uuid, playerName, defaultRank);
                // Loading can happen on the database/recovery executor.  Do not call
                // savePlayer() here: it snapshots live Bukkit state and is main-thread
                // only.  The caller publishes this new-player record to the cache, and
                // the normal main-thread YAML sync persists it safely.
                return def;
            }

            ConfigurationSection section = yaml.getConfigurationSection(path);
            if (section == null) {
                logger.warning("Player data section is missing or invalid for " + uuid + ".");
                String defaultRank = plugin.getRankManager() != null
                        ? plugin.getRankManager().getDefaultRankId() : "Guest";
                return PlayerData.defaultData(uuid, playerName, defaultRank);
            }
            return fromSection(uuid, section);
        }
    }

    public void savePlayer(PlayerData data) {
        requireMainThread();
        PlayerData inputSnapshot = copyData(data);
        savePlayerOnMain(inputSnapshot);
    }

    public void saveAll(Collection<PlayerData> players) {
        if (players == null || players.isEmpty()) return;
        requireMainThread();
        List<PlayerData> inputSnapshot = copyPlayers(players);
        if (inputSnapshot.isEmpty()) return;
        saveAllOnMain(inputSnapshot);
    }

    /**
     * Saves one already-created player snapshot and waits for the atomic async write.
     * This is used only by the MySQL emergency fallback so its log can distinguish a
     * successful YAML write from a failed one.
     */
    boolean savePlayerForEmergencyFallback(PlayerData data) {
        final long requestGeneration;
        synchronized (writeLock) {
            requestGeneration = ++writeGeneration;
        }
        if (Bukkit.isPrimaryThread()) {
            return savePlayerAndAwait(data, requestGeneration);
        }

        CountDownLatch completion = new CountDownLatch(1);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        try {
            logger.warning("Scheduling the YAML emergency fallback on the main thread.");
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    succeeded.set(savePlayerAndAwait(data, requestGeneration));
                } finally {
                    completion.countDown();
                }
            });
        } catch (RuntimeException e) {
            logger.warning("Could not schedule the YAML emergency fallback: " + e.getMessage());
            return saveEmergencyPlayerDirect(data, requestGeneration);
        }

        try {
            if (!completion.await(EMERGENCY_FALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                logger.warning("Timed out while waiting for the YAML emergency fallback for "
                        + data.uuid() + "; refusing an unsafe concurrent direct write.");
                return saveEmergencyPlayerDirect(data, requestGeneration);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while waiting for the YAML emergency fallback; "
                    + "attempting a direct emergency write.");
            return saveEmergencyPlayerDirect(data, requestGeneration);
        }
        return succeeded.get();
    }

    /**
     * Waits for previously queued YAML file writes to finish. Intended for lifecycle
     * boundaries where the plugin must not return before its final save is durable.
     */
    public void awaitPendingWrites() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("YAML writes must be awaited from the main thread.");
        }

        if (!awaitPendingWrites(SHUTDOWN_WAIT_TIMEOUT_MILLIS)) {
            logger.warning("YAML writes did not drain within " + SHUTDOWN_WAIT_TIMEOUT_MILLIS
                    + " ms; attempting synchronous recovery.");
            if (!recoverPendingWritesSynchronously()) {
                logger.severe("YAML writer could not drain before shutdown; the failed snapshot "
                        + "has been retained for a future retry.");
            }
        }
    }

    /**
     * Prevents new YAML saves from being queued while allowing the current writer task and
     * its pending snapshot to finish. This must be called before the writer is shut down.
     */
    public void beginShutdown() {
        synchronized (writeLock) {
            acceptingWrites = false;
        }
    }

    /**
     * Marks the Bukkit-backed writer as no longer needed after all pending work has drained.
     * Bukkit owns the underlying scheduler, so there is no separate executor to terminate here.
     */
    public void shutdownWriter() {
        synchronized (writeLock) {
            if (asyncWriteScheduled || pendingSnapshot != null || inFlightSnapshot != null) {
                logger.warning("YAML writer shutdown requested while writes are still pending.");
                return;
            }
            writerShutdown = true;
        }
    }

    private boolean awaitPendingWrites(long timeoutMillis) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("YAML writes must be awaited from the main thread.");
        }

        final long deadline = timeoutMillis > 0
                ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
                : 0L;
        boolean interrupted = false;
        synchronized (writeLock) {
            while (asyncWriteScheduled || pendingSnapshot != null || inFlightSnapshot != null) {
                clearCancelledWriterLocked();
                if (!asyncWriteScheduled && pendingSnapshot != null) break;
                try {
                    if (timeoutMillis <= 0) {
                        writeLock.wait();
                    } else {
                        long remainingNanos = deadline - System.nanoTime();
                        if (remainingNanos <= 0) break;
                        long waitMillis = Math.max(1L,
                                TimeUnit.NANOSECONDS.toMillis(remainingNanos));
                        writeLock.wait(waitMillis);
                    }
                } catch (InterruptedException e) {
                    logger.warning("Interrupted while waiting for playerdata.yml save to finish.");
                    if (timeoutMillis > 0) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    // Shutdown must still drain the already-submitted write. Restore the
                    // interrupt status after the write has completed.
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (!asyncWriteScheduled && pendingSnapshot == null && inFlightSnapshot == null) {
                return true;
            }
        }
        return recoverPendingWritesSynchronously();
    }

    public List<PlayerData> loadAll() {
        synchronized (writeLock) {
            List<PlayerData> result = new ArrayList<>();
            ConfigurationSection section = yaml.getConfigurationSection("players");
            if (section == null) return result;
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection ps = section.getConfigurationSection(key);
                    if (ps == null) {
                        logger.warning("Skipping invalid player data section for key " + key + ".");
                        continue;
                    }
                    result.add(fromSection(uuid, ps));
                } catch (IllegalArgumentException ignored) {}
            }
            return result;
        }
    }

    public boolean hasPlayer(UUID uuid) {
        synchronized (writeLock) {
            return yaml.contains("players." + uuid);
        }
    }

    public File getDataFile() { return dataFile; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void write(PlayerData data) {
        write(yaml, data);
    }

    private static void write(YamlConfiguration target, PlayerData data) {
        String path = "players." + data.uuid();
        target.set(path + ".name",             data.playerName());
        target.set(path + ".rank",             data.rankId());
        target.set(path + ".experience",       data.experience());
        target.set(path + ".money",            data.money());
        target.set(path + ".language",         data.language());
        target.set(path + ".block-breaks",     data.blockBreaks());
        target.set(path + ".playtime-minutes", data.playTime());
        target.set(path + ".completed-requirements", new ArrayList<>(data.completedRequirements()));
    }

    private synchronized SaveOperation savePlayerOnMain(PlayerData input) {
        return savePlayerOnMain(input, null);
    }

    private synchronized SaveOperation savePlayerOnMain(
            PlayerData input,
            Long emergencyRequestGeneration
    ) {
        requireMainThread();
        if (!isAcceptingWrites()) {
            logger.warning("Ignoring YAML player save for " + input.uuid()
                    + " because shutdown has started.");
            return null;
        }
        PlayerData snapshot = copyData(stitchRuntimeData(input));
        synchronized (writeLock) {
            if (!acceptingWrites || writerShutdown) {
                logger.warning("Ignoring YAML player save for " + input.uuid()
                        + " because shutdown has started.");
                return null;
            }
            if (emergencyRequestGeneration != null
                    && latestPlayerWrite.getOrDefault(input.uuid(), Long.MIN_VALUE)
                            > emergencyRequestGeneration) {
                logger.fine("Skipping stale YAML emergency snapshot for " + input.uuid() + ".");
                return null;
            }
            write(snapshot);
            return queueAsyncWrite(List.of(snapshot.uuid()), emergencyRequestGeneration);
        }
    }

    private synchronized SaveOperation saveAllOnMain(List<PlayerData> inputs) {
        requireMainThread();
        if (!isAcceptingWrites()) {
            logger.warning("Ignoring YAML player save batch because shutdown has started.");
            return null;
        }
        List<PlayerData> snapshots = inputs.stream()
                .map(this::stitchRuntimeData)
                .map(YamlPlayerDataStorage::copyData)
                .toList();
        if (snapshots.isEmpty()) return null;
        synchronized (writeLock) {
            if (!acceptingWrites || writerShutdown) {
                logger.warning("Ignoring YAML player save batch because shutdown has started.");
                return null;
            }
            for (PlayerData snapshot : snapshots) write(snapshot);
            return queueAsyncWrite(snapshots.stream().map(PlayerData::uuid).toList());
        }
    }

    private SaveOperation queueAsyncWrite(List<UUID> affectedUuids) {
        return queueAsyncWrite(affectedUuids, null);
    }

    private SaveOperation queueAsyncWrite(
            List<UUID> affectedUuids,
            Long requestedGeneration
    ) {
        // YamlConfiguration is only touched on the main thread. The immutable,
        // already-serialized YAML text captures the complete document, including
        // records not in this save batch.
        SaveOperation operation = new SaveOperation();
        YamlSaveSnapshot snapshot;
        try {
            snapshot = new YamlSaveSnapshot(
                    yaml.saveToString(), affectedUuids, List.of(operation));
        } catch (Exception e) {
            operation.complete(false);
            String context = affectedContext(affectedUuids);
            logger.log(Level.WARNING, "Failed to serialize playerdata.yml" + context + ".", e);
            return operation;
        }
        boolean scheduleWriter = false;
        long token = 0L;
        synchronized (writeLock) {
            if (!acceptingWrites || writerShutdown) {
                logger.warning("Ignoring YAML write because the storage system is shutting down.");
                operation.complete(false);
                return operation;
            }
            long generation = requestedGeneration == null
                    ? ++writeGeneration : requestedGeneration;
            for (UUID uuid : affectedUuids) latestPlayerWrite.put(uuid, generation);
            pendingSnapshot = mergeSnapshots(pendingSnapshot, snapshot);
            clearCancelledWriterLocked();
            if (!asyncWriteScheduled) {
                asyncWriteScheduled = true;
                token = ++writerToken;
                scheduleWriter = true;
            }
        }

        if (!scheduleWriter) return operation;
        scheduleAsyncWriter(token, 0L);
        return operation;
    }

    private void scheduleAsyncWriter(long token, long delayTicks) {
        try {
            BukkitTask task = delayTicks <= 0
                    ? plugin.getServer().getScheduler()
                            .runTaskAsynchronously(plugin, () -> drainAsyncWrites(token))
                    : plugin.getServer().getScheduler()
                            .runTaskLaterAsynchronously(plugin,
                                    () -> drainAsyncWrites(token), delayTicks);
            synchronized (writeLock) {
                if (asyncWriteScheduled && writerToken == token) {
                    asyncWriterTask = task;
                } else {
                    task.cancel();
                }
            }
        } catch (RuntimeException e) {
            synchronized (writeLock) {
                if (writerToken == token) {
                    asyncWriteScheduled = false;
                    asyncWriterTask = null;
                    writerToken++;
                    writeLock.notifyAll();
                }
            }
            logger.log(Level.WARNING, "Could not schedule playerdata.yml writer.", e);
            if (delayTicks <= 0 && recoverPendingWritesSynchronously()) return;
            failPendingWrites();
        }
    }

    private void failPendingWrites() {
        synchronized (writeLock) {
            if (pendingSnapshot != null) {
                completeOperations(pendingSnapshot, false);
            }
            writeLock.notifyAll();
        }
    }

    /**
     * The async writer deliberately uses only the already serialized snapshot and
     * Java file I/O. It does not touch Bukkit, Vault, players, or live PlayerData.
     */
    private void drainAsyncWrites(long token) {
        long retryToken = 0L;
        long retryDelay = 0L;
        try {
            while (true) {
                YamlSaveSnapshot snapshot;
                synchronized (writeLock) {
                    if (writerToken != token) return;
                    asyncWriterTask = null;
                    snapshot = pendingSnapshot;
                    pendingSnapshot = null;
                    if (snapshot == null) {
                        asyncWriteScheduled = false;
                        asyncWriterTask = null;
                        writeLock.notifyAll();
                        return;
                    }
                    inFlightSnapshot = snapshot;
                }
                boolean succeeded = true;
                try {
                    succeeded = writeSnapshot(snapshot.yamlContent(), snapshot.affectedUuids());
                } catch (Throwable t) {
                    // Keep the tracking state releasable even if an unexpected writer
                    // failure escapes the normal file-I/O handling.
                    succeeded = false;
                    logger.log(Level.SEVERE, "Unexpected failure while saving playerdata.yml"
                            + affectedContext(snapshot.affectedUuids()) + ".", t);
                }
                synchronized (writeLock) {
                    inFlightSnapshot = null;
                    if (succeeded) {
                        consecutiveWriteFailures = 0;
                        completeOperations(snapshot, true);
                    } else {
                        // If a newer document is queued, it supersedes the failed bytes but
                        // carries the failed operation until that newer document is durable.
                        pendingSnapshot = mergeFailedSnapshot(snapshot, pendingSnapshot);
                        consecutiveWriteFailures++;
                        if (consecutiveWriteFailures > MAX_AUTOMATIC_RETRIES) {
                            completeOperations(pendingSnapshot, false);
                            pendingSnapshot = null;
                            consecutiveWriteFailures = 0;
                            asyncWriteScheduled = false;
                            asyncWriterTask = null;
                            writerToken++;
                            writeLock.notifyAll();
                            return;
                        }
                        retryDelay = Math.min(RETRY_MAX_DELAY_TICKS,
                                RETRY_BASE_DELAY_TICKS
                                        << Math.min(consecutiveWriteFailures - 1, 30));
                        retryToken = ++writerToken;
                        asyncWriteScheduled = true;
                        asyncWriterTask = null;
                        writeLock.notifyAll();
                    }
                }
                if (retryToken != 0L) {
                    scheduleAsyncWriter(retryToken, retryDelay);
                    return;
                }
            }
        } finally {
            synchronized (writeLock) {
                if (writerToken == token) {
                    if (inFlightSnapshot != null && pendingSnapshot == null) {
                        pendingSnapshot = inFlightSnapshot;
                    }
                    inFlightSnapshot = null;
                    asyncWriteScheduled = false;
                    asyncWriterTask = null;
                    writeLock.notifyAll();
                }
            }
        }
    }

    private boolean writeSnapshot(String content, List<UUID> affectedUuids) {
        Path target = dataFile.toPath();
        Path temporary = null;
        synchronized (fileWriteLock) {
            try {
                temporary = Files.createTempFile(target.getParent(), "playerdata-", ".tmp");
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to save playerdata.yml"
                        + affectedContext(affectedUuids) + ".", e);
                return false;
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void clearCancelledWriterLocked() {
        if (asyncWriteScheduled && inFlightSnapshot == null && asyncWriterTask != null
                && asyncWriterTask.isCancelled()) {
            asyncWriteScheduled = false;
            asyncWriterTask = null;
            writeLock.notifyAll();
        }
    }

    private static YamlSaveSnapshot mergeSnapshots(
            YamlSaveSnapshot older,
            YamlSaveSnapshot newer
    ) {
        if (older == null) return newer;
        return new YamlSaveSnapshot(
                newer.yamlContent(),
                mergeUuids(older.affectedUuids(), newer.affectedUuids()),
                mergeOperations(older.operations(), newer.operations())
        );
    }

    private static YamlSaveSnapshot mergeFailedSnapshot(
            YamlSaveSnapshot failed,
            YamlSaveSnapshot newer
    ) {
        if (newer == null) return failed;
        return new YamlSaveSnapshot(
                newer.yamlContent(),
                mergeUuids(failed.affectedUuids(), newer.affectedUuids()),
                mergeOperations(failed.operations(), newer.operations())
        );
    }

    private static List<UUID> mergeUuids(List<UUID> first, List<UUID> second) {
        LinkedHashSet<UUID> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static List<SaveOperation> mergeOperations(
            List<SaveOperation> first,
            List<SaveOperation> second
    ) {
        List<SaveOperation> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static void completeOperations(YamlSaveSnapshot snapshot, boolean succeeded) {
        for (SaveOperation operation : snapshot.operations()) {
            operation.complete(succeeded);
        }
    }

    private static String affectedContext(List<UUID> affectedUuids) {
        if (affectedUuids == null || affectedUuids.isEmpty()) return "";
        if (affectedUuids.size() == 1) return " for player " + affectedUuids.get(0);
        return " for players " + affectedUuids;
    }

    /**
     * Recovers a pending snapshot without Bukkit. This is intentionally used only
     * after scheduling has failed or a bounded lifecycle wait has expired; normal
     * saves continue to use the asynchronous Bukkit writer.
     */
    private boolean recoverPendingWritesSynchronously() {
        YamlSaveSnapshot snapshot;
        synchronized (writeLock) {
            clearCancelledWriterLocked();
            if (inFlightSnapshot != null || asyncWriteScheduled) return false;
            snapshot = pendingSnapshot;
            if (snapshot == null) return true;
            pendingSnapshot = null;
            inFlightSnapshot = snapshot;
            asyncWriteScheduled = true;
        }

        boolean succeeded = writeSnapshot(snapshot.yamlContent(), snapshot.affectedUuids());
        synchronized (writeLock) {
            inFlightSnapshot = null;
            completeOperations(snapshot, succeeded);
            if (!succeeded) pendingSnapshot = mergeFailedSnapshot(snapshot, pendingSnapshot);
            asyncWriteScheduled = false;
            asyncWriterTask = null;
            writeLock.notifyAll();
            return succeeded && pendingSnapshot == null;
        }
    }

    /**
     * Emergency-only direct write for a pre-built PlayerData snapshot. It does not
     * inspect Bukkit or live players, so a broken main-thread scheduler cannot make
     * the MySQL-to-YAML fallback wait forever.
     */
    private boolean saveEmergencyPlayerDirect(PlayerData data, long requestGeneration) {
        synchronized (writeLock) {
            // A direct write cannot safely run while the Bukkit writer has bytes
            // in flight: those older bytes could land after this write. Let the
            // normal writer finish instead of risking a stale overwrite.
            clearCancelledWriterLocked();
            if (inFlightSnapshot != null || asyncWriteScheduled) {
                logger.warning("Deferring direct YAML emergency write for " + data.uuid()
                        + " because another YAML write is in progress.");
                return false;
            }
            if (latestPlayerWrite.getOrDefault(data.uuid(), Long.MIN_VALUE)
                    > requestGeneration) {
                logger.fine("Skipping stale direct YAML emergency snapshot for "
                        + data.uuid() + ".");
                return false;
            }

            YamlSaveSnapshot baseSnapshot =
                    pendingSnapshot != null ? pendingSnapshot : inFlightSnapshot;
            YamlConfiguration emergency = new YamlConfiguration();
            try {
                if (baseSnapshot != null) {
                    emergency.loadFromString(baseSnapshot.yamlContent());
                } else if (dataFile.exists()) {
                    emergency = YamlConfiguration.loadConfiguration(dataFile);
                }
                emergency.set("data-version", CURRENT_DATA_VERSION);
                write(emergency, data);
                String emergencyContent = emergency.saveToString();
                boolean succeeded = writeSnapshot(
                        emergencyContent, List.of(data.uuid()));
                if (succeeded && pendingSnapshot != null) {
                    // The emergency document was based on the pending document
                    // and is now newer for the affected UUIDs. It supersedes
                    // that queued document, so its waiters can complete safely.
                    completeOperations(pendingSnapshot, true);
                    pendingSnapshot = null;
                    writeLock.notifyAll();
                }
                return succeeded;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Direct YAML emergency write failed for "
                        + data.uuid() + ".", e);
                return false;
            }
        }
    }

    private boolean savePlayerAndAwait(PlayerData data, long requestGeneration) {
        requireMainThread();
        if (!isAcceptingWrites()) {
            logger.warning("Cannot perform YAML emergency fallback for " + data.uuid()
                    + " because shutdown has started.");
            return false;
        }
        SaveOperation operation = savePlayerOnMain(data, requestGeneration);
        return operation != null && operation.await(EMERGENCY_FALLBACK_TIMEOUT_MILLIS);
    }

    private boolean isAcceptingWrites() {
        synchronized (writeLock) {
            return acceptingWrites && !writerShutdown;
        }
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Playerdata snapshots must be created on the main thread.");
        }
    }

    private static PlayerData copyData(PlayerData data) {
        return new PlayerData(data.uuid(), data.playerName(), data.rankId(), data.experience(),
                data.money(), data.language(), data.blockBreaks(), data.playTime(),
                data.completedRequirements());
    }

    private static List<PlayerData> copyPlayers(Collection<PlayerData> players) {
        List<PlayerData> snapshots = new ArrayList<>(players.size());
        for (PlayerData player : players) snapshots.add(copyData(player));
        return List.copyOf(snapshots);
    }

    private PlayerData fromSection(UUID uuid, ConfigurationSection s) {
        String defaultRank = plugin.getRankManager() != null
                ? plugin.getRankManager().getDefaultRankId() : "Guest";
        List<String> completedRequirements = s.getStringList("completed-requirements");
        return new PlayerData(
                uuid,
                s.getString("name",             "Unknown"),
                s.getString("rank",             defaultRank),
                s.getLong("experience",          0L),
                s.getDouble("money",             0.0),
                s.getString("language",          "en"),
                s.getLong("block-breaks",        0L),
                s.getLong("playtime-minutes",    0L),
                new java.util.LinkedHashSet<>(completedRequirements)
        );
    }

    private boolean persist(YamlConfiguration configuration) {
        try {
            return writeSnapshot(configuration.saveToString(), List.of());
        }
        catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save playerdata.yml.", e);
            return false;
        }
    }

    /**
     * Stitches live XP, Vault balance, block-break count, and playtime for online players
     * so saves always reflect the current session state.
     */
    private PlayerData stitchRuntimeData(PlayerData data) {
        Player player = Bukkit.getPlayer(data.uuid());
        if (player == null || !player.isOnline()) return data;

        long liveXp = plugin.getExperienceManager() != null
                ? plugin.getExperienceManager().getXp(player)
                : data.experience();

        double liveMoney = data.money();
        if (plugin.getSoftDependency() != null && plugin.getSoftDependency().hasVault()) {
            try { liveMoney = plugin.getSoftDependency().getBalance(player); }
            catch (Exception ignored) {}
        }

        long liveBlocks = plugin.getBlockBreakTracker() != null
                ? plugin.getBlockBreakTracker().getCount(player.getUniqueId())
                : data.blockBreaks();

        long livePlaytime = plugin.getPlaytimeTracker() != null
                ? plugin.getPlaytimeTracker().getPlayTime(player.getUniqueId())
                : data.playTime();

        return new PlayerData(
                data.uuid(), player.getName(), data.rankId(),
                liveXp, liveMoney, data.language(), liveBlocks, livePlaytime,
                data.completedRequirements()
        );
    }
}
