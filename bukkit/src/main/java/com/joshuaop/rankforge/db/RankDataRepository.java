package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles all player-data reads and writes.
 *
 * <p>A failed SQL read is not a "new player" read.  It enters YAML mode and
 * returns the last valid YAML/cache record instead.  Writes are serialized per
 * UUID and always prefer the newest cache snapshot, which prevents an older
 * asynchronous flush from replacing a newer rank change.</p>
 */
public class RankDataRepository {

    private final RankForge plugin;
    private final DatabaseManager db;
    private final CacheManager cache;
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public RankDataRepository(RankForge plugin, CacheManager cache) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.cache = cache;
    }

    public PlayerData load(UUID uuid, String playerName) {
        PlayerData cached = cache.getRaw(uuid);
        if (cached != null && cached.isValidFor(uuid)) return cached;

        if (db.isReadyForReads()) {
            try {
                PlayerData loaded = loadFromMySQL(uuid);
                if (loaded != null) {
                    cache.put(uuid, loaded);
                    return loaded;
                }

                // A successful, validated query with no row is a legitimate new
                // player. It is safe to create a default, unlike a failed query.
                PlayerData created = makeDefault(uuid, playerName, true);
                cache.put(uuid, created);
                return created;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
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
     * Persists a complete immutable snapshot. The return value is authoritative:
     * true means the selected active backend confirmed the write.
     */
    public boolean save(PlayerData requested) {
        if (requested == null || !requested.isValidFor(requested.uuid())) {
            plugin.getLogger().warning("Refusing to persist null or invalid player data.");
            return false;
        }

        UUID uuid = requested.uuid();
        synchronized (playerLocks.computeIfAbsent(uuid, ignored -> new Object())) {
            // A delayed task may carry an old snapshot. The cache is the newest
            // in-memory source of truth for an active player.
            PlayerData current = cache.getRaw(uuid);
            PlayerData data = current != null && current.isValidFor(uuid) ? current : requested;
            if (!data.isValidFor(uuid)) return false;

            if (db.isConnected()) {
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
            plugin.getLogger().log(Level.WARNING,
                    "MySQL save failed for " + data.uuid() + ".", e);
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
        List<PlayerData> result = new ArrayList<>();
        if (!db.isConnected()) {
            YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
            return yaml != null ? yaml.loadAll().stream().limit(limit).toList() : result;
        }
        String sql = "SELECT * FROM rf_players ORDER BY experience DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlayerData data = fromResultSet(rs);
                    if (data.isValidFor(data.uuid())) result.add(data);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "getTopPlayers failed; using YAML.", e);
            db.markUnavailable(e);
            YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
            if (yaml != null) return yaml.loadAll().stream().limit(limit).toList();
        }
        return result;
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
}