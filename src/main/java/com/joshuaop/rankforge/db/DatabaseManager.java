package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the MySQL database connection pool using HikariCP.
 *
 * Priority:
 *   1. MySQL  — if configured and reachable
 *   2. YAML file storage — plugins/RankForge/data/playerdata.yml (automatic fallback)
 */
public class DatabaseManager {

    private final RankForge       plugin;
    private HikariDataSource      dataSource;
    private boolean               available = false;

    public DatabaseManager(RankForge plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts MySQL connection. Returns false if unavailable — YAML fallback is used instead.
     * Never throws.
     */
    public boolean connect() {
        FileConfiguration cfg     = plugin.getConfig();
        String            cfgType = cfg.getString("database.type", "mysql").toLowerCase();

        if (cfgType.equals("mysql")) {
            return tryMySQL(cfg);
        }

        plugin.getLogger().info("[DB] Database type is not 'mysql' — using YAML file storage.");
        return false;
    }

    private boolean tryMySQL(FileConfiguration cfg) {
        String host     = cfg.getString("database.host",     "localhost");
        int    port     = cfg.getInt("database.port",        3306);
        String dbName   = cfg.getString("database.name",     "rankforge");
        String user     = cfg.getString("database.user",     "root");
        String password = cfg.getString("database.password", "password");
        int    poolSize = cfg.getInt("database.pool-size",   10);
        long   timeout  = cfg.getLong("database.timeout",    5000);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTimeout(timeout);
        config.setInitializationFailTimeout(1);
        config.setPoolName("RankForge-MySQL");
        config.addDataSourceProperty("cachePrepStmts",        "true");
        config.addDataSourceProperty("prepStmtCacheSize",     "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            new MySQLProvider(this).createTables();
            available = true;
            plugin.getLogger().info("[DB] Connected to MySQL successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[DB] MySQL unavailable: " + e.getMessage());
            plugin.getLogger().info("[DB] Falling back to YAML file storage (plugins/RankForge/data/playerdata.yml).");
            if (dataSource != null && !dataSource.isClosed()) dataSource.close();
            dataSource = null;
            return false;
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[DB] MySQL connection pool closed.");
        }
        available = false;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("MySQL DataSource is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return available && dataSource != null && !dataSource.isClosed();
    }
}
