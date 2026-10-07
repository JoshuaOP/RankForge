package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles all player-data reads and writes.
 *
 * <p>A failed SQL read is not a "new player" read. It enters YAML mode and
 * returns the last valid YAML/cache record instead. Writes are serialized per
 * UUID (via striped locks) and always prefer the newest cache snapshot, which prevents an older
 * asynchronous flush from replacing a newer rank change.</p>
 */
public class RankDataRepository {

    private static final int LOCK_COUNT = 128;
    private final Object[] lockStripes = new Object[LOCK_COUNT];

    private final RankForge plugin;
    private final DatabaseManager db;
    private final CacheManager cache;
    
    // Dedicated asynchronous executor to prevent main-thread blocking during database operations
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "RankForge-AsyncSave-Worker"));
    
    // Dedicated asynchronous executor for non-blocking player data loading (I/O)
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "RankForge-AsyncIO-Worker"));

    public RankDataRepository(RankForge plugin, CacheManager cache) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.cache = cache;

        for (int i = 0; i < LOCK_COUNT; i++) {
            lockStripes[i] = new Object();
        }
    }

    private Object getLock(UUID uuid) {
        // Use Math.floorMod to correctly handle negative hash codes (e.g., Integer.MIN_VALUE)
        return lockStripes[Math.floorMod(uuid.hashCode(), LOCK_COUNT)];
    }

    /**
     * Synchronously loads player data. (Used internally or for fallback scenarios).
     */
    public PlayerData load(UUID uuid, String playerName) {
        PlayerData cached = cache.getRaw(uuid);
        if (cached != null && cached.isValidFor(uuid)) {
            return cached;
        }

        if (db.isReadyForReads()) {
            try {
                PlayerData loaded = loadFromMySQL(uuid);
                if (loaded != null) {
                    cache.put(uuid, loaded);
                    return loaded;
                }

                // A successful, validated query with no row is a legitimate new player.
                PlayerData created = makeDefault(uuid, playerName, true);
                cache.put(uuid, created);
                return created;
            } catch (Exception e) {
                db.logMySQLOperationFailure(
                        "MySQL load failed for " + uuid
                                + "; preserving existing YAML/cache data.", e);
                db.markUnavailable(e);
            }
        }

        PlayerData yamlData = loadFromYaml(uuid, playerName);
        if (yamlData != null) return yamlData;

        // There is no valid persisted record to preserve. Keep a new-player
        // default in memory only; it must not be written over an invalid file.
        return makeDefault(uuid, playerName, true);
    }

    /**
     * Asynchronously loads player data. Checks the cache synchronously first;
     * if absent, offloads blocking database/YAML I/O to a background worker thread.
     */
    public CompletableFuture<PlayerData> loadAsync(UUID uuid, String playerName) {
        PlayerData cached = cache.getRaw(uuid);
        if (cached != null && cached.isValidFor(uuid)) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> load(uuid, playerName), ioExecutor);
    }

    /**
     * Returns null only for a successful query that found no row. Every returned
     * row has been validated before it can reach the cache.
     */
    private PlayerData loadFromMySQL(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM rf_players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PlayerData data = fromResultSet(rs);
                if (!data.isValidFor(uuid)) {
                    throw new SQLException("MySQL returned invalid player data for " + uuid);
                }
                return data;
            }
        }
    }

    private PlayerData loadFromYaml(UUID uuid, String playerName) {
        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml == null) return null;
        try {
            PlayerData data = yaml.loadPlayer(uuid, playerName);
            if (data != null && data.isValidFor(uuid)) {
                cache.put(uuid, data);
                return data;
            }
            plugin.getLogger().warning("YAML player data for " + uuid
                    + " is missing or invalid; it was not used as a save source.");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "YAML load failed for " + uuid + "; existing cache was preserved.", e);
        }
        return null;
    }

    /**
     * Persists a complete immutable snapshot synchronously. The return value is authoritative:
     * true means the selected active backend confirmed the write.
     */
    public boolean save(PlayerData requested) {
        if (requested == null || !requested.isValidFor(requested.uuid())) {
            plugin.getLogger().warning("Refusing to persist null or invalid player data.");
            return false;
        }

        UUID uuid = requested.uuid();
        synchronized (getLock(uuid)) {
            // A delayed task may carry an old snapshot. The cache is the newest
            // in-memory source of truth for an active player.
            PlayerData current = cache.getRaw(uuid);
            PlayerData data = current != null && current.isValidFor(uuid) ? current : requested;
            if (!data.isValidFor(uuid)) return false;

            // Checked against isReadyForReads() so normal saves do not execute against MySQL during recovery
            if (db.isReadyForReads()) {
                if (saveToMySQL(data)) return true;
                db.markUnavailable(new SQLException("MySQL player save was not confirmed."));
            }

            PlayerData newest = cache.getRaw(uuid);
            if (newest != null && newest.isValidFor(uuid)) data = newest;
            boolean yamlSaved = saveToYaml(data);
            if (!yamlSaved) {
                plugin.getLogger().severe("Could not persist player data for " + uuid
                        + " to MySQL or YAML. The valid in-memory snapshot was retained.");
            } else {
                plugin.getLogger().info("Player data for " + uuid
                        + " was saved to YAML while MySQL is unavailable.");
            }
            return yamlSaved;
        }
    }

    /**
     * Asynchronously persists a player snapshot, shielding the main thread from database latency.
     */
    public void saveAsync(PlayerData requested) {
        if (requested == null || !requested.isValidFor(requested.uuid())) return;
        saveExecutor.submit(() -> save(requested));
    }

    /**
     * Persists a collection of player snapshots in a single high-performance batch operation.
     */
    public boolean saveAll(Collection<PlayerData> players) {
        if (players == null || players.isEmpty()) return true;

        if (db.isReadyForReads()) {
            if (saveAllToMySQL(players)) return true;
            db.markUnavailable(new SQLException("MySQL batch player save was not confirmed."));
        }

        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml != null) {
            try {
                yaml.saveAll(players);
                plugin.getLogger().info("Player data batch saved to YAML while MySQL is unavailable.");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not persist player data batch to YAML fallback.", e);
            }
        }
        return false;
    }

    /**
     * Writes one recovery snapshot to the verified MySQL pool.
     *
     * <p>This intentionally uses {@link DatabaseManager#isConnected()}, not
     * {@link DatabaseManager#isReadyForReads()}: the pool is marked as recovering
     * while these writes are being restored, and reads must remain on YAML until
     * recovery has completed. Unlike a normal save, this method never falls back
     * to YAML or performs another backend write.</p>
     */
    public boolean saveForRecovery(PlayerData requested) {
        if (requested == null || !requested.isValidFor(requested.uuid())
                || !db.isConnected()) return false;

        UUID uuid = requested.uuid();
        synchronized (getLock(uuid)) {
            PlayerData current = cache.getRaw(uuid);
            PlayerData data = current != null && current.isValidFor(uuid)
                    ? current : requested;
            return saveToMySQL(data);
        }
    }

    /**
     * Writes a recovery collection of snapshots to the verified MySQL pool using batching.
     */
    public boolean saveAllForRecovery(Collection<PlayerData> players) {
        if (players == null || players.isEmpty() || !db.isConnected()) return false;
        return saveAllToMySQL(players);
    }

    private boolean saveToMySQL(PlayerData data) {
        String sql = """
                INSERT INTO rf_players
                    (uuid, player_name, rank_id, experience, money, language, block_breaks, playtime_minutes, completed_requirements)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name            = VALUES(player_name),
                    rank_id                = VALUES(rank_id),
                    experience             = VALUES(experience),
                    money                  = VALUES(money),
                    language               = VALUES(language),
                    block_breaks           = VALUES(block_breaks),
                    playtime_minutes       = VALUES(playtime_minutes),
                    completed_requirements = VALUES(completed_requirements)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.uuid().toString());
            ps.setString(2, data.playerName());
            ps.setString(3, data.rankId());
            ps.setLong(4, data.experience());
            ps.setDouble(5, data.money());
            ps.setString(6, data.language());
            ps.setLong(7, data.blockBreaks());
            ps.setLong(8, data.playTime());
            ps.setString(9, String.join(",", data.completedRequirements()));
            int updated = ps.executeUpdate();
            return updated >= 1;
        } catch (SQLException e) {
            db.logMySQLOperationFailure(
                    "MySQL save failed for " + data.uuid() + ".", e);
            return false;
        }
    }

    private boolean saveAllToMySQL(Collection<PlayerData> players) {
        String sql = """
                INSERT INTO rf_players
                    (uuid, player_name, rank_id, experience, money, language, block_breaks, playtime_minutes, completed_requirements)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name            = VALUES(player_name),
                    rank_id                = VALUES(rank_id),
                    experience             = VALUES(experience),
                    money                  = VALUES(money),
                    language               = VALUES(language),
                    block_breaks           = VALUES(block_breaks),
                    playtime_minutes       = VALUES(playtime_minutes),
                    completed_requirements = VALUES(completed_requirements)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            try {
                int validCount = 0;
                for (PlayerData data : players) {
                    if (data == null || !data.isValidFor(data.uuid())) {
                        continue;
                    }
                    ps.setString(1, data.uuid().toString());
                    ps.setString(2, data.playerName());
                    ps.setString(3, data.rankId());
                    ps.setLong(4, data.experience());
                    ps.setDouble(5, data.money());
                    ps.setString(6, data.language());
                    ps.setLong(7, data.blockBreaks());
                    ps.setLong(8, data.playTime());
                    ps.setString(9, String.join(",", data.completedRequirements()));
                    ps.addBatch();
                    validCount++;
                }

                if (validCount == 0) {
                    conn.rollback();
                    return false;
                }

                ps.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            db.logMySQLOperationFailure("MySQL batch save failed.", e);
            return false;
        }
    }

    private boolean saveToYaml(PlayerData data) {
        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml == null) {
            plugin.getLogger().warning("YAML fallback is unavailable for " + data.uuid() + ".");
            return false;
        }
        return yaml.savePlayerForEmergencyFallback(data);
    }

    public List<PlayerData> getTopPlayers(int limit) {
        if (db.isReadyForReads()) {
            String sql = "SELECT * FROM rf_players ORDER BY experience DESC LIMIT ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PlayerData> result = new ArrayList<>();
                    while (rs.next()) {
                        PlayerData data = fromResultSet(rs);
                        if (data.isValidFor(data.uuid())) result.add(data);
                    }
                    return result;
                }
            } catch (Exception e) {
                db.logMySQLOperationFailure("getTopPlayers failed; using YAML/Cache.", e);
                db.markUnavailable(e);
            }
        }

        return getTopPlayersFromCacheAndYaml(limit);
    }

    private List<PlayerData> getTopPlayersFromCacheAndYaml(int limit) {
        Map<UUID, PlayerData> merged = new HashMap<>();

        // 1. Load persisted records from YAML safely
        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml != null) {
            Collection<PlayerData> yamlPlayers = yaml.loadAll();
            if (yamlPlayers != null) {
                for (PlayerData p : yamlPlayers) {
                    if (p != null && p.isValidFor(p.uuid())) {
                        merged.put(p.uuid(), p);
                    }
                }
            }
        }

        // 2. Overlay live cache data which contains newest active player XP
        for (PlayerData cached : cache.all()) {
            if (cached != null && cached.isValidFor(cached.uuid())) {
                merged.put(cached.uuid(), cached);
            }
        }

        return merged.values().stream()
                .sorted((a, b) -> Long.compare(b.experience(), a.experience()))
                .limit(limit)
                .toList();
    }

    private PlayerData makeDefault(UUID uuid, String playerName, boolean cacheIt) {
        String defaultRankId = plugin.getRankManager() != null
                ? plugin.getRankManager().getDefaultRankId() : "Guest";
        PlayerData data = PlayerData.defaultData(uuid,
                playerName == null || playerName.isBlank() ? "Unknown" : playerName,
                defaultRankId);
        if (cacheIt) cache.put(uuid, data);
        return data;
    }

    private PlayerData fromResultSet(ResultSet rs) throws SQLException {
        long blockBreaks = 0L;
        try { blockBreaks = rs.getLong("block_breaks"); } catch (SQLException ignored) {}
        long playTime = 0L;
        try { playTime = rs.getLong("playtime_minutes"); } catch (SQLException ignored) {}

        Set<String> completedRequirements = new LinkedHashSet<>();
        try {
            String raw = rs.getString("completed_requirements");
            if (raw != null && !raw.isBlank()) {
                for (String value : raw.split(",")) {
                    if (!value.isBlank()) completedRequirements.add(value.trim().toLowerCase());
                }
            }
        } catch (SQLException ignored) {}

        return new PlayerData(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("rank_id"),
                rs.getLong("experience"),
                rs.getDouble("money"),
                rs.getString("language"),
                blockBreaks,
                playTime,
                completedRequirements
        );
    }

    /**
     * Cleanly shuts down asynchronous save and load executors.
     */
    public void shutdown() {
        saveExecutor.shutdown();
        ioExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
            if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
