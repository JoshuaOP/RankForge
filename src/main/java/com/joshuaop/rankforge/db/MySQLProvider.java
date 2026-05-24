package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and manages MySQL table schemas for RankForge.
 */
public class MySQLProvider {

    private final DatabaseManager db;

    public MySQLProvider(DatabaseManager db) {
        this.db = db;
    }

    public void createTables() {
        String createPlayers = """
                CREATE TABLE IF NOT EXISTS rf_players (
                    uuid        VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(24) NOT NULL,
                    rank_id     VARCHAR(64) NOT NULL DEFAULT 'Guest',
                    experience  BIGINT      NOT NULL DEFAULT 0,
                    money       DOUBLE      NOT NULL DEFAULT 0.0,
                    language    VARCHAR(8)  NOT NULL DEFAULT 'en',
                    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        String createRankLog = """
                CREATE TABLE IF NOT EXISTS rf_rank_log (
                    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid      VARCHAR(36) NOT NULL,
                    from_rank VARCHAR(64) NOT NULL,
                    to_rank   VARCHAR(64) NOT NULL,
                    ranked_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_uuid (uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayers);
            stmt.execute(createRankLog);
            RankForge.getInstance().getLogger().info("[DB] MySQL tables verified/created.");
        } catch (SQLException e) {
            RankForge.getInstance().getLogger().severe("[DB] Failed to create MySQL tables: " + e.getMessage());
        }
    }

    public void logRankChange(String uuid, String fromRank, String toRank) {
        String sql = "INSERT INTO rf_rank_log (uuid, from_rank, to_rank) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, fromRank);
            ps.setString(3, toRank);
            ps.executeUpdate();
        } catch (SQLException e) {
            RankForge.getInstance().getLogger().warning("[DB] Failed to log rank change: " + e.getMessage());
        }
    }
}
