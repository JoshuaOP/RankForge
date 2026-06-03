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
        if (cache.contains(uuid)) return cache.get(uuid);

        if (db.isConnected()) {
            return loadFromMySQL(uuid, playerName);
        }

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
            plugin.getLogger().warning("[DB] MySQL load failed for " + uuid + ": " + e.getMessage());
        }
        PlayerData def = makeDefault(uuid, playerName);
        save(def);
        return def;
    }

    private PlayerData loadFromYaml(UUID uuid, String playerName) {
        YamlPlayerDataStorage yaml = plugin.getYamlPlayerDataStorage();
        if (yaml == null) {
            PlayerData def = makeDefault(uuid, playerName);
            cache.put(uuid, def);
            return def;
        }
        PlayerData data = yaml.loadPlayer(uuid, playerName);
        cache.put(uuid, data);
        return data;
    }

    public void save(PlayerData data) {
        if (db.isConnected()) {
            saveToMySQL(data);
        } else {
            saveToYaml(data);
        }
    }

    private void saveToMySQL(PlayerData data) {
        String sql = """
                INSERT INTO rf_players
                    (uuid, player_name, rank_id, experience, money, language, block_breaks)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name  = VALUES(player_name),
                    rank_id      = VALUES(rank_id),
                    experience   = VALUES(experience),
                    money        = VALUES(money),
                    language     = VALUES(language),
                    block_breaks = VALUES(block_breaks)
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
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] MySQL save failed for " + data.uuid() + ": " + e.getMessage());
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
            plugin.getLogger().warning("[DB] getTopPlayers failed: " + e.getMessage());
        }
        return result;
    }

    private PlayerData makeDefault(UUID uuid, String playerName) {
        String defaultRankId = plugin.getConfig().getString("ranks.default-rank",
                plugin.getRankManager() != null ? plugin.getRankManager().getDefaultRankId() : "Guest");
        PlayerData data = PlayerData.defaultData(uuid, playerName, defaultRankId);
        cache.put(uuid, data);
        return data;
    }

    private PlayerData fromResultSet(ResultSet rs) throws SQLException {
        // block_breaks column may be absent in very old databases (before migration runs).
        long blockBreaks = 0L;
        try { blockBreaks = rs.getLong("block_breaks"); }
        catch (SQLException ignored) {}

        return new PlayerData(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("rank_id"),
                rs.getLong("experience"),
                rs.getDouble("money"),
                rs.getString("language"),
                blockBreaks
        );
    }
}
