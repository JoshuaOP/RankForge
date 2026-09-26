package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Manages the MySQL database connection pool using HikariCP.
 *
 * Priority:
 *   1. MySQL  — if configured and reachable
 *   2. YAML file storage — plugins/RankForge/data/playerdata.yml (automatic fallback)
 */
public class DatabaseManager {

    private final RankForge       plugin;
    private volatile HikariDataSource dataSource;
    private volatile boolean          available = false;
    private volatile boolean          recovering = false;
    private volatile boolean          mysqlConfigured;
    private final Object              connectionLock = new Object();

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
        mysqlConfigured = cfgType.equals("mysql");

        if (mysqlConfigured) {
            return tryMySQL(cfg);
        }

        plugin.getLogger().info("Database type is not 'mysql' — using YAML file storage.");
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

        // Suppress HikariCP's own connection-fail logging during our probe attempt
        java.util.logging.Logger hikariLogger = java.util.logging.Logger.getLogger("com.zaxxer.hikari");
        Level previousLevel = hikariLogger.getLevel();
        hikariLogger.setLevel(Level.OFF);

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

        synchronized (connectionLock) {
            try {
                HikariDataSource candidate = new HikariDataSource(config);
                dataSource = candidate;
                new MySQLProvider(this).createTables();
                try (Connection connection = candidate.getConnection()) {
                    if (!connection.isValid((int) Math.max(1L, timeout / 1000L))) {
                        throw new SQLException("MySQL validation query failed.");
                    }
                }
                available = true;
                recovering = false;
                plugin.getLogger().info("Connected to MySQL successfully.");
                return true;
            } catch (Exception e) {
                available = false;
                String reason = e.getMessage() != null
                        ? e.getMessage().split("\\.")[0]
                        : e.getClass().getSimpleName();
                plugin.getLogger().info("MySQL not available — using YAML file storage. ("
                        + reason + ")");
                closeDataSource();
                return false;
            } finally {
                hikariLogger.setLevel(previousLevel != null ? previousLevel : Level.WARNING);
            }
        }
    }

    public void disconnect() {
        synchronized (connectionLock) {
            available = false;
            recovering = false;
            closeDataSource();
        }
    }

    /** Mark the pool unusable after a failed operation and immediately enter YAML mode. */
    public void markUnavailable(Throwable cause) {
        synchronized (connectionLock) {
            if (available && cause != null) {
                plugin.getLogger().warning("MySQL operation failed; switching to YAML storage: "
                        + cause.getMessage());
            }
            available = false;
            recovering = false;
            closeDataSource();
        }
    }

    /** Reconnect only when MySQL is configured; tryMySQL also verifies the pool. */
    public boolean reconnect() {
        if (!mysqlConfigured || isConnected()) return isConnected();
        boolean connected = tryMySQL(plugin.getConfig());
        if (connected) recovering = true;
        return connected;
    }

    public boolean isReadyForReads() {
        return isConnected() && !recovering;
    }

    public void finishRecovery() {
        if (isConnected()) recovering = false;
    }

    public boolean isMysqlConfigured() {
        return mysqlConfigured;
    }

    private void closeDataSource() {
        HikariDataSource current = dataSource;
        dataSource = null;
        if (current != null && !current.isClosed()) {
            try {
                current.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Could not close MySQL pool cleanly: " + e.getMessage());
            }
        }
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource current = dataSource;
        if (!available || current == null || current.isClosed()) {
            throw new SQLException("MySQL DataSource is not initialized or has been closed.");
        }
        return current.getConnection();
    }

    public boolean isConnected() {
        return available && dataSource != null && !dataSource.isClosed();
    }
}
