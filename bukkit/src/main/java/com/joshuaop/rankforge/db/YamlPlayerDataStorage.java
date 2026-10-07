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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML-based player data storage. Default fallback when MySQL is unavailable.
 * Path: plugins/RankForge/data/playerdata.yml
 */
public class YamlPlayerDataStorage {

    private static final int CURRENT_DATA_VERSION = 5;
    private static final long EMERGENCY_TIMEOUT_MS = 3_000L;
    private static final long SHUTDOWN_TIMEOUT_MS = 10_000L;
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_BASE_DELAY = 20L;
    private static final long RETRY_MAX_DELAY = 20L * 30L;

    private final RankForge plugin;
    private final File dataFile;
    private final Logger logger;
    private YamlConfiguration yaml;

    private final Object writeLock = new Object();
    private final Object fileWriteLock = new Object();
    private final Map<UUID, Long> latestPlayerWrite = new HashMap<>();

    private YamlSaveSnapshot pendingSnapshot;
    private YamlSaveSnapshot inFlightSnapshot;
    private BukkitTask asyncWriterTask;

    private long writeGeneration;
    private long writerToken;
    private int consecutiveWriteFailures;
    private boolean asyncWriteScheduled;
    private boolean acceptingWrites = true;
    private boolean writerShutdown;

    private record YamlSaveSnapshot(
            String yamlContent,
            List<UUID> affectedUuids,
            List<SaveOperation> operations,
            long snapshotGeneration
    ) {
        private YamlSaveSnapshot {
            affectedUuids = List.copyOf(affectedUuids);
            operations = List.copyOf(operations);
        }
    }

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
                return completion.await(timeoutMillis, TimeUnit.MILLISECONDS) && state == State.SUCCEEDED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    public YamlPlayerDataStorage(RankForge plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        File dataDir = new File(plugin.getDataFolder(), "data");

        try {
            Files.createDirectories(dataDir.toPath());
            if (!Files.isDirectory(dataDir.toPath())) {
                throw new IOException("Path is not a directory: " + dataDir);
            }
        } catch (IOException | SecurityException e) {
            throw new IllegalStateException("Could not create player-data directory; YAML unavailable.", e);
        }

        this.dataFile = new File(dataDir, "playerdata.yml");
        load();
        checkAndMigrateSchema();
    }

    private void load() {
        if (!dataFile.exists()) {
            yaml = new YamlConfiguration();
            yaml.set("data-version", CURRENT_DATA_VERSION);
            if (!persistDirect(yaml.saveToString())) {
                throw new IllegalStateException("Could not initialize playerdata.yml");
            }
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void checkAndMigrateSchema() {
        int savedVersion = yaml.getInt("data-version", 1);
        if (savedVersion > CURRENT_DATA_VERSION) {
            acceptingWrites = false;
            logger.severe("playerdata.yml uses unsupported future schema v" + savedVersion + ". YAML writes disabled.");
            return;
        }
        if (savedVersion >= CURRENT_DATA_VERSION) return;

        logger.info("Migrating player data v" + savedVersion + " → v" + CURRENT_DATA_VERSION + "...");
        YamlConfiguration migrated = new YamlConfiguration();

        try {
            migrated.loadFromString(yaml.saveToString());
            ConfigurationSection players = migrated.getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    String path = "players." + uuidStr;
                    
                    if (savedVersion < 2) {
                        if (migrated.contains(path + ".legacy-rank-node")) {
                            migrated.set(path + ".rank", migrated.getString(path + ".legacy-rank-node"));
                            migrated.set(path + ".legacy-rank-node", null);
                        }
                        if (!migrated.contains(path + ".experience")) migrated.set(path + ".experience", 0L);
                        if (!migrated.contains(path + ".money")) migrated.set(path + ".money", 0.0);
                        if (!migrated.contains(path + ".language")) migrated.set(path + ".language", "en");
                    }
                    if (savedVersion < 3 && !migrated.contains(path + ".block-breaks")) {
                        migrated.set(path + ".block-breaks", 0L);
                    }
                    if (savedVersion < 4 && !migrated.contains(path + ".playtime-minutes")) {
                        migrated.set(path + ".playtime-minutes", 0L);
                    }
                    if (savedVersion < 5 && !migrated.contains(path + ".completed-requirements")) {
                        migrated.set(path + ".completed-requirements", new ArrayList<String>());
                    }
                }
            }
            migrated.set("data-version", CURRENT_DATA_VERSION);
        } catch (Exception e) {
            acceptingWrites = false;
            logger.log(Level.SEVERE, "Player data migration failed.", e);
            return;
        }

        if (!persistDirect(migrated.saveToString())) {
            acceptingWrites = false;
            logger.severe("Player data migration could not be persisted.");
            return;
        }
        yaml = migrated;
        logger.info("Player data migration complete.");
    }

    public PlayerData loadPlayer(UUID uuid, String playerName) {
        synchronized (writeLock) {
            ConfigurationSection section = yaml.getConfigurationSection("players." + uuid);
            if (section == null) return PlayerData.defaultData(uuid, playerName, getDefaultRank());
            return fromSection(uuid, section);
        }
    }

    public List<PlayerData> loadAll() {
        synchronized (writeLock) {
            List<PlayerData> result = new ArrayList<>();
            ConfigurationSection section = yaml.getConfigurationSection("players");
            if (section == null) return result;

            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection pSec = section.getConfigurationSection(key);
                    if (pSec != null) result.add(fromSection(uuid, pSec));
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

    public File getDataFile() {
        return dataFile;
    }

    public void savePlayer(PlayerData data) {
        requireMainThread();
        savePlayerOnMain(copyData(data), null);
    }

    public void saveAll(Collection<PlayerData> players) {
        if (players == null || players.isEmpty()) return;
        requireMainThread();
        saveAllOnMain(copyPlayers(players));
    }

    public boolean savePlayerForEmergencyFallback(PlayerData data) {
        if (data == null) return false;
        if (Bukkit.isPrimaryThread()) return saveEmergencyPlayerDirect(copyData(data));

        CountDownLatch latch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try { result[0] = saveEmergencyPlayerDirect(copyData(data)); }
                finally { latch.countDown(); }
            });
        } catch (RuntimeException e) {
            return false;
        }

        try {
            return latch.await(EMERGENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS) && result[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void beginShutdown() {
        synchronized (writeLock) {
            acceptingWrites = false;
            writeLock.notifyAll();
        }
    }

    public void awaitPendingWrites() {
        requireMainThread();
        if (!awaitPendingWritesInternal(SHUTDOWN_TIMEOUT_MS) && !recoverPendingWritesSynchronously()) {
            logger.severe("YAML writer could not drain before shutdown.");
        }
    }

    public void shutdownWriter() {
        synchronized (writeLock) {
            // Fixed: removed .isDone() since BukkitTask does not support it
            if (pendingSnapshot != null || inFlightSnapshot != null || (asyncWriterTask != null && !asyncWriterTask.isCancelled())) {
                return;
            }
            writerShutdown = true;
            asyncWriterTask = null;
            asyncWriteScheduled = false;
            writeLock.notifyAll();
        }
    }

    private boolean awaitPendingWritesInternal(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            synchronized (writeLock) {
                clearCancelledWriterLocked();
                if (pendingSnapshot == null && inFlightSnapshot == null && !asyncWriteScheduled) return true;
                if (deadline - System.nanoTime() <= 0L) break;
            }
            try { Thread.sleep(10L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    private static void write(YamlConfiguration target, PlayerData data) {
        String path = "players." + data.uuid();
        target.set(path + ".name", data.playerName());
        target.set(path + ".rank", data.rankId());
        target.set(path + ".experience", data.experience());
        target.set(path + ".money", data.money());
        target.set(path + ".language", data.language());
        target.set(path + ".block-breaks", data.blockBreaks());
        target.set(path + ".playtime-minutes", data.playTime());
        target.set(path + ".completed-requirements", new ArrayList<>(data.completedRequirements()));
    }

    private SaveOperation savePlayerOnMain(PlayerData input, Long emergencyGen) {
        requireMainThread();
        if (!isAcceptingWrites()) return null;

        PlayerData snapshot = copyData(stitchRuntimeData(input));
        synchronized (writeLock) {
            if (!acceptingWrites || writerShutdown) return null;
            long gen = emergencyGen != null ? emergencyGen : ++writeGeneration;
            if (latestPlayerWrite.getOrDefault(snapshot.uuid(), Long.MIN_VALUE) > gen) return null;

            latestPlayerWrite.put(snapshot.uuid(), gen);
            write(yaml, snapshot);
            return queueAsyncWriteLocked(List.of(snapshot.uuid()), gen);
        }
    }

    private SaveOperation saveAllOnMain(List<PlayerData> inputs) {
        requireMainThread();
        if (!isAcceptingWrites()) return null;

        List<PlayerData> snapshots = inputs.stream().map(this::stitchRuntimeData).map(YamlPlayerDataStorage::copyData).toList();
        if (snapshots.isEmpty()) return null;

        synchronized (writeLock) {
            if (!acceptingWrites || writerShutdown) return null;
            long gen = ++writeGeneration;
            List<UUID> affected = new ArrayList<>();

            for (PlayerData s : snapshots) {
                if (latestPlayerWrite.getOrDefault(s.uuid(), Long.MIN_VALUE) > gen) continue;
                latestPlayerWrite.put(s.uuid(), gen);
                write(yaml, s);
                affected.add(s.uuid());
            }

            if (affected.isEmpty()) return null;
            return queueAsyncWriteLocked(affected, gen);
        }
    }

    private SaveOperation queueAsyncWriteLocked(List<UUID> affectedUuids, long generation) {
        SaveOperation op = new SaveOperation();
        if (!acceptingWrites || writerShutdown) {
            op.complete(false);
            return op;
        }

        YamlSaveSnapshot snapshot;
        try {
            snapshot = new YamlSaveSnapshot(yaml.saveToString(), affectedUuids, List.of(op), generation);
        } catch (Exception e) {
            op.complete(false);
            return op;
        }

        pendingSnapshot = mergeSnapshots(pendingSnapshot, snapshot);
        clearCancelledWriterLocked();

        if (!asyncWriteScheduled) {
            asyncWriteScheduled = true;
            long token = ++writerToken;
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> drainAsyncWrites(token));
            } catch (RuntimeException e) {
                asyncWriteScheduled = false;
                writerToken++;
                op.complete(false);
                logger.log(Level.WARNING, "Failed to schedule async YAML writer task.", e);
            }
        }
        return op;
    }

    private void drainAsyncWrites(long token) {
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
                        writeLock.notifyAll();
                        return;
                    }
                    if (isSnapshotStaleLocked(snapshot)) {
                        completeOperations(snapshot, true);
                        continue;
                    }
                    inFlightSnapshot = snapshot;
                }

                boolean succeeded = writeSnapshot(snapshot.yamlContent(), snapshot.affectedUuids(), snapshot.snapshotGeneration());

                synchronized (writeLock) {
                    inFlightSnapshot = null;
                    if (succeeded) {
                        consecutiveWriteFailures = 0;
                        completeOperations(snapshot, true);
                        continue;
                    }

                    pendingSnapshot = mergeFailedSnapshot(snapshot, pendingSnapshot);
                    consecutiveWriteFailures++;

                    if (consecutiveWriteFailures > MAX_RETRIES) {
                        completeOperations(pendingSnapshot, false);
                        pendingSnapshot = null;
                        consecutiveWriteFailures = 0;
                        asyncWriteScheduled = false;
                        writerToken++;
                        writeLock.notifyAll();
                        logger.severe("Maximum YAML write retries exceeded.");
                        return;
                    }

                    long delay = Math.min(RETRY_MAX_DELAY, RETRY_BASE_DELAY << Math.min(consecutiveWriteFailures - 1, 30));
                    long retryToken = ++writerToken;
                    asyncWriteScheduled = true;
                    asyncWriterTask = null;
                    writeLock.notifyAll();
                    scheduleAsyncWriter(retryToken, delay);
                    return;
                }
            }
        } finally {
            synchronized (writeLock) {
                if (writerToken == token) {
                    if (inFlightSnapshot != null) pendingSnapshot = mergeSnapshots(inFlightSnapshot, pendingSnapshot);
                    inFlightSnapshot = null;
                    asyncWriterTask = null;
                    asyncWriteScheduled = pendingSnapshot != null;
                    writeLock.notifyAll();
                }
            }
        }
    }

    private void scheduleAsyncWriter(long token, long delayTicks) {
        try {
            BukkitTask task = delayTicks <= 0L
                    ? plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> drainAsyncWrites(token))
                    : plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> drainAsyncWrites(token), delayTicks);

            synchronized (writeLock) {
                if (asyncWriteScheduled && writerToken == token) asyncWriterTask = task;
                else task.cancel();
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
            failPendingWrites();
        }
    }

    private void failPendingWrites() {
        synchronized (writeLock) {
            if (pendingSnapshot != null) {
                completeOperations(pendingSnapshot, false);
                pendingSnapshot = null;
            }
            asyncWriteScheduled = false;
            asyncWriterTask = null;
            writeLock.notifyAll();
        }
    }

    private boolean writeSnapshot(String content, List<UUID> affectedUuids, long generation) {
        Path target = dataFile.toPath();
        synchronized (fileWriteLock) {
            synchronized (writeLock) {
                if (affectedUuids != null) {
                    for (UUID uuid : affectedUuids) {
                        if (latestPlayerWrite.getOrDefault(uuid, Long.MIN_VALUE) > generation) return true;
                    }
                }
            }

            Path temp = null;
            try {
                temp = Files.createTempFile(target.getParent(), "playerdata-", ".tmp");
                Files.writeString(temp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (Exception e) {
                return false;
            } finally {
                if (temp != null) {
                    try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
                }
            }
        }
    }

    private boolean persistDirect(String content) {
        return writeSnapshot(content, List.of(), Long.MIN_VALUE);
    }

    private boolean saveEmergencyPlayerDirect(PlayerData data) {
        requireMainThread();
        PlayerData live = copyData(stitchRuntimeData(data));
        long gen;
        synchronized (writeLock) {
            gen = ++writeGeneration;
            if (latestPlayerWrite.getOrDefault(live.uuid(), Long.MIN_VALUE) > gen) return false;
            latestPlayerWrite.put(live.uuid(), gen);

            if (pendingSnapshot != null) {
                completeOperations(pendingSnapshot, false);
                pendingSnapshot = null;
                asyncWriteScheduled = false;
                writerToken++;
            }
        }

        YamlConfiguration emergency = new YamlConfiguration();
        try {
            if (dataFile.exists()) emergency = YamlConfiguration.loadConfiguration(dataFile);
            emergency.set("data-version", CURRENT_DATA_VERSION);
            write(emergency, live);
            boolean success = writeSnapshot(emergency.saveToString(), List.of(live.uuid()), gen);
            if (success) {
                synchronized (writeLock) { yaml = emergency; }
            }
            return success;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean recoverPendingWritesSynchronously() {
        requireMainThread();
        YamlSaveSnapshot snapshot;
        synchronized (writeLock) {
            clearCancelledWriterLocked();
            if (inFlightSnapshot != null || asyncWriteScheduled) return false;
            snapshot = pendingSnapshot;
            if (snapshot == null) return true;
            pendingSnapshot = null;

            if (isSnapshotStaleLocked(snapshot)) {
                completeOperations(snapshot, true);
                return true;
            }
            inFlightSnapshot = snapshot;
        }

        boolean success = writeSnapshot(snapshot.yamlContent(), snapshot.affectedUuids(), snapshot.snapshotGeneration());
        synchronized (writeLock) {
            inFlightSnapshot = null;
            if (success) {
                consecutiveWriteFailures = 0;
                completeOperations(snapshot, true);
                writeLock.notifyAll();
                return pendingSnapshot == null;
            } else {
                pendingSnapshot = mergeFailedSnapshot(snapshot, pendingSnapshot);
                completeOperations(snapshot, false);
                writeLock.notifyAll();
                return false;
            }
        }
    }

    private boolean isSnapshotStaleLocked(YamlSaveSnapshot s) {
        for (UUID uuid : s.affectedUuids()) {
            if (latestPlayerWrite.getOrDefault(uuid, Long.MIN_VALUE) > s.snapshotGeneration()) return true;
        }
        return false;
    }

    private void clearCancelledWriterLocked() {
        if (asyncWriteScheduled && inFlightSnapshot == null && asyncWriterTask != null && asyncWriterTask.isCancelled()) {
            asyncWriteScheduled = false;
            asyncWriterTask = null;
            writeLock.notifyAll();
        }
    }

    private static YamlSaveSnapshot mergeSnapshots(YamlSaveSnapshot older, YamlSaveSnapshot newer) {
        if (older == null) return newer;
        return new YamlSaveSnapshot(
                newer.yamlContent(),
                mergeUuids(older.affectedUuids(), newer.affectedUuids()),
                mergeOperations(older.operations(), newer.operations()),
                Math.max(older.snapshotGeneration(), newer.snapshotGeneration())
        );
    }

    private static YamlSaveSnapshot mergeFailedSnapshot(YamlSaveSnapshot failed, YamlSaveSnapshot newer) {
        if (newer == null) return failed;
        return new YamlSaveSnapshot(
                newer.yamlContent(),
                mergeUuids(failed.affectedUuids(), newer.affectedUuids()),
                mergeOperations(failed.operations(), newer.operations()),
                Math.max(failed.snapshotGeneration(), newer.snapshotGeneration())
        );
    }

    private static List<UUID> mergeUuids(List<UUID> f, List<UUID> s) {
        LinkedHashSet<UUID> set = new LinkedHashSet<>(f);
        set.addAll(s);
        return List.copyOf(set);
    }

    private static List<SaveOperation> mergeOperations(List<SaveOperation> f, List<SaveOperation> s) {
        List<SaveOperation> list = new ArrayList<>(f.size() + s.size());
        list.addAll(f);
        list.addAll(s);
        return List.copyOf(list);
    }

    private static void completeOperations(YamlSaveSnapshot s, boolean success) {
        for (SaveOperation op : s.operations()) op.complete(success);
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

    private String getDefaultRank() {
        return plugin.getRankManager() != null ? plugin.getRankManager().getDefaultRankId() : "Guest";
    }

    private static PlayerData copyData(PlayerData d) {
        return new PlayerData(
                d.uuid(),
                d.playerName(),
                d.rankId(),
                d.experience(),
                d.money(),
                d.language(),
                d.blockBreaks(),
                d.playTime(),
                new LinkedHashSet<>(d.completedRequirements())
        );
    }

    private static List<PlayerData> copyPlayers(Collection<PlayerData> players) {
        List<PlayerData> list = new ArrayList<>(players.size());
        for (PlayerData p : players) list.add(copyData(p));
        return List.copyOf(list);
    }

    private PlayerData fromSection(UUID uuid, ConfigurationSection s) {
        return new PlayerData(
                uuid,
                s.getString("name", "Unknown"),
                s.getString("rank", getDefaultRank()),
                s.getLong("experience", 0L),
                s.getDouble("money", 0.0),
                s.getString("language", "en"),
                s.getLong("block-breaks", 0L),
                s.getLong("playtime-minutes", 0L),
                new LinkedHashSet<>(s.getStringList("completed-requirements"))
        );
    }

    private PlayerData stitchRuntimeData(PlayerData data) {
        requireMainThread();
        Player player = Bukkit.getPlayer(data.uuid());
        if (player == null || !player.isOnline()) return data;

        long xp = plugin.getExperienceManager() != null ? plugin.getExperienceManager().getXp(player) : data.experience();
        double money = data.money();
        if (plugin.getSoftDependency() != null && plugin.getSoftDependency().hasVault()) {
            try { money = plugin.getSoftDependency().getBalance(player); } catch (Exception ignored) {}
        }
        long blocks = plugin.getBlockBreakTracker() != null ? plugin.getBlockBreakTracker().getCount(player.getUniqueId()) : data.blockBreaks();
        long playtime = plugin.getPlaytimeTracker() != null ? plugin.getPlaytimeTracker().getPlayTime(player.getUniqueId()) : data.playTime();

        return new PlayerData(data.uuid(), player.getName(), data.rankId(), xp, money, data.language(), blocks, playtime, data.completedRequirements());
    }
}
