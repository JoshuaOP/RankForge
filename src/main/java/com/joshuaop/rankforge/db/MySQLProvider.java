package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and manages MySQL table schemas for RankForge.
 *
 * Schema history:
 *   v1 — rf_players (uuid, player_name, rank_id, experience, money, language, updated_at)
 *        rf_rank_log (id, uuid, from_rank, to_rank, ranked_at)
 *   v2 — rf_players + block_breaks BIGINT column (added via ALTER TABLE IF NOT EXISTS)
 *   v3 — rf_players + playtime_minutes BIGINT column (real wall-clock time, not ticks)
 */
public class MySQLProvider {

    private final DatabaseManager db;

    public MySQLProvider(DatabaseManager db) {
        this.db = db;
    }

    public void createTables() {
        String createPlayers = """
                CREATE TABLE IF NOT EXISTS rf_players (
                    uuid              VARCHAR(36)  PRIMARY KEY,
                    player_name       VARCHAR(24)  NOT NULL,
                    rank_id           VARCHAR(64)  NOT NULL DEFAULT 'Guest',
                    experience        BIGINT       NOT NULL DEFAULT 0,
                    money             DOUBLE       NOT NULL DEFAULT 0.0,
                    language          VARCHAR(8)   NOT NULL DEFAULT 'en',
                    block_breaks      BIGINT       NOT NULL DEFAULT 0,
                    playtime_minutes  BIGINT       NOT NULL DEFAULT 0,
                    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
            RankForge.getInstance().getLogger().severe(
                    "[DB] Failed to create MySQL tables: " + e.getMessage());
        }

        // Safe column migrations for existing installations using INFORMATION_SCHEMA checks
        addColumnIfMissing("rf_players", "block_breaks",     "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("rf_players", "playtime_minutes", "BIGINT NOT NULL DEFAULT 0");
    }

    /**
     * Idempotent ALTER TABLE — only adds the column if it does not already exist.
     * Uses INFORMATION_SCHEMA for maximum MySQL version compatibility.
     */
    private void addColumnIfMissing(String table, String column, String definition) {
        String check = """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME   = ?
                  AND COLUMN_NAME  = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setString(1, table);
            ps.setString(2, column);
            var rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                String alter = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(alter);
                    RankForge.getInstance().getLogger().info(
                            "[DB] Added column '" + column + "' to table '" + table + "'.");
                }
            }
        } catch (SQLException e) {
            RankForge.getInstance().getLogger().warning(
                    "[DB] Column migration check failed (" + table + "." + column + "): "
                            + e.getMessage());
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
            RankForge.getInstance().getLogger().warning(
                    "[DB] Failed to log rank change: " + e.getMessage());
        }
    }
}
