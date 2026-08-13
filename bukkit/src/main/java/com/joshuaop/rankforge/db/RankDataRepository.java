package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;

import java.sql.*;
import java.util.*;

/**
 * Handles all read/write operations for player rank data.
 * Routes to MySQL when connected, otherwise delegates to YamlPlayerDataStorage.
 * Works alongside CacheManager for minimal I/O calls.
 */
public class RankDataRepository {

    private final RankForge            plugin;
    private final DatabaseManager      db;
    private final CacheManager         cache;

    public RankDataRepository(RankForge plugin, CacheManager cache) {
        this.plugin = plugin;
        this.db     = plugin.getDatabaseManager();
        this.cache  = cache;
    }

    public PlayerData load(UUID uuid, String playerName) {
        return loadOrCreate(uuid, playerName);
    }

    /**
     * Loads a joining player's record from the configured backend, replacing
     * any stale cache entry only after the storage read has completed.
     *
     * <p>Join handling uses this path because a cache entry may be a
     * short-lived offline snapshot. The per-player lock also makes this wait
     * for an in-flight quit save instead of racing it.</p>
     */
    public PlayerData loadFresh(UUID uuid, String playerName) {
        return cache.withPlayerLock(uuid, () -> {
            PlayerData loaded = loadFromStorage(uuid, playerName);
            if (loaded != null) return loaded;

            // A storage failure is not a new player. Preserve an existing
            // cache snapshot rather than replacing it with Default.
            PlayerData cached = cache.getRaw(uuid);
            if (cached != null) return cached;
            return makeDefault(uuid, playerName);
        });
    }

    /**
     * Returns the cached/backend record, creating and persisting Default only
     * when neither backend contains a record.
     */
    public PlayerData loadOrCreate(UUID uuid, String playerName) {
        return cache.withPlayerLock(uuid, () -> {
            // The cache is authoritative for a loaded session. Reading storage
            // first here allowed a stale pre-save record to replace a newer
            // rank (for example VIP) with the previous Default rank.
            PlayerData cached = cache.get(uuid);
            if (cached != null) return cached;

            PlayerData loaded = loadFromStorage(uuid, playerName);
            if (loaded != null) return loaded;

            PlayerData raw = cache.getRaw(uuid);
            return raw != null ? raw : makeDefault(uuid, playerName);
        });
    }

    private PlayerData loadFromStorage(UUID uuid, String playerName) {
        if (db.isConnected()) return loadFromMySQL(uuid, playerName);
        return loadFromYaml(uuid, playerName);
    }

    private PlayerData loadFromMySQL(UUID uuid, String playerName) {
        String sql = "SELECT * FROM rf_players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerData data = fromResultSet(rs);
                cache.put(uuid, data);
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL load failed for " + uuid + ": " + e.getMessage());
            // A backend error is not evidence that the player is new. Never
            // manufacture and persist Default after a failed query.
            return null;
        }
        PlayerData def = makeDefault(uuid, playerName);
        save(def);
        return def;
    }

    private PlayerData loadFromYaml(UUID uuid, String playerName) {
        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml == null) {
            return null;
        }
        PlayerData data = yaml.loadPlayer(uuid, playerName);
        cache.put(uuid, data);
        return data;
    }

    public void save(PlayerData data) {
        if (data == null || data.uuid() == null) return;
        cache.withPlayerLock(data.uuid(), () -> {
            // Prefer the newest cache record over an older async snapshot.
            PlayerData current = cache.getRaw(data.uuid());
            PlayerData toPersist = current != null ? current : data;
            if (db.isConnected()) saveToMySQL(toPersist);
            else saveToYaml(toPersist);
            return null;
        });
    }

    /**
     * Persists a snapshot collection without allowing an older snapshot to
     * overwrite a newer record in the shared cache.
     */
    public void saveAll(Collection<PlayerData> players) {
        if (players == null) return;
        for (PlayerData data : players) save(data);
    }

    private void saveToMySQL(PlayerData data) {
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
            ps.setLong(4,   data.experience());
            ps.setDouble(5, data.money());
            ps.setString(6, data.language());
            ps.setLong(7,   data.blockBreaks());
            ps.setLong(8,   data.playTime());
            ps.setString(9, String.join(",", data.completedRequirements()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL save failed for " + data.uuid() + ": " + e.getMessage());
        }
    }

    private void saveToYaml(PlayerData data) {
        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml != null) yaml.savePlayer(data);
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
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(fromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().warning("getTopPlayers failed: " + e.getMessage());
        }
        return result;
    }

    private PlayerData makeDefault(UUID uuid, String playerName) {
        String defaultRankId = plugin.getRankManager() != null
                ? plugin.getRankManager().getDefaultRankId()
                : "Guest";
        PlayerData data = PlayerData.defaultData(uuid, playerName, defaultRankId);
        cache.put(uuid, data);
        return data;
    }

    private PlayerData fromResultSet(ResultSet rs) throws SQLException {
        // block_breaks and playtime_minutes may be absent in older databases before migration runs.
        long blockBreaks = 0L;
        try { blockBreaks = rs.getLong("block_breaks"); }
        catch (SQLException ignored) {}

        long playTime = 0L;
        try { playTime = rs.getLong("playtime_minutes"); }
        catch (SQLException ignored) {}

        // completed_requirements may be absent in older databases before migration runs.
        Set<String> completedRequirements = Set.of();
        try {
            String raw = rs.getString("completed_requirements");
            if (raw != null && !raw.isBlank()) {
                completedRequirements = new LinkedHashSet<>(Arrays.asList(raw.split(",")));
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
