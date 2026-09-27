package com.joshuaop.rankforge.db;

import com.joshuaop.rankforge.RankForge;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Manages the MySQL database connection pool using HikariCP.
 *
 * Priority:
 *   1. MySQL — if configured and reachable
 *   2. YAML file storage — plugins/RankForge/data/playerdata.yml (automatic fallback)
 */
public class DatabaseManager {

    private final RankForge          plugin;
    private volatile HikariDataSource dataSource;
    private volatile boolean         available = false;
    private volatile boolean         recovering = false;
    private volatile boolean         mysqlConfigured = false;
    private volatile boolean         fallbackMessageLogged = false;
    private volatile String          lastLoggedOperationFailure;
    private final Object             connectionLock = new Object();

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
        this.mysqlConfigured = cfgType.equals("mysql");

        if (mysqlConfigured) {
            return tryMySQL(cfg, false);
        }

        plugin.getLogger().info("Database type is not 'mysql' — using YAML file storage.");
        return false;
    }

    private boolean tryMySQL(FileConfiguration cfg, boolean isRecoveryAttempt) {
        String host     = cfg.getString("database.host",     "localhost");
        int    port     = cfg.getInt("database.port",        3306);
        String dbName   = cfg.getString("database.name",     "rankforge");
        String user     = cfg.getString("database.user",     "root");
        String password = cfg.getString("database.password", "password");
        int    poolSize = cfg.getInt("database.pool-size",   10);
        long   timeout  = cfg.getLong("database.timeout",    5000);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=utf8&serverTimezone=UTC";

        synchronized (connectionLock) {
            if (isConnected()) return true;

            // Ensure MySQL JDBC driver class is loaded into memory
            String driverClass = "com.mysql.cj.jdbc.Driver";
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e1) {
                driverClass = "com.mysql.jdbc.Driver";
                try {
                    Class.forName(driverClass);
                } catch (ClassNotFoundException ignored) {
                    driverClass = null; // Rely on standard JDBC auto-discovery
                }
            }

            // 1. Pre-flight JDBC connection probe to avoid spinning up Hikari pools when database is unreachable
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            props.setProperty("connectTimeout", String.valueOf(timeout));

            try (Connection testConn = DriverManager.getConnection(jdbcUrl, props)) {
                if (!testConn.isValid((int) Math.max(1L, timeout / 1000L))) {
                    throw new SQLException("Pre-flight MySQL connection test validation failed.");
                }
            } catch (Exception e) {
                available = false;
                logMySQLUnavailable();

                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().log(Level.FINE, "Pre-flight MySQL probe failed:", e);
                }

                closeDataSource();
                return false;
            }

            // 2. Pre-flight succeeded; safely initialize HikariCP pool
            try {
                closeDataSource();

                HikariConfig config = new HikariConfig();
                if (driverClass != null) {
                    config.setDriverClassName(driverClass);
                }
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(user);
                config.setPassword(password);
                config.setMaximumPoolSize(poolSize);
                config.setConnectionTimeout(timeout);
                config.setInitializationFailTimeout(-1);
                config.setPoolName("RankForge-MySQL");
                config.addDataSourceProperty("cachePrepStmts",        "true");
                config.addDataSourceProperty("prepStmtCacheSize",     "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

                dataSource = new HikariDataSource(config);
                available  = true;

                // Initialize database tables/schema
                new MySQLProvider(this).createTables();

                fallbackMessageLogged = false;
                lastLoggedOperationFailure = null;

                if (!isRecoveryAttempt) {
                    plugin.getLogger().info("Connected to MySQL successfully.");
                }
                return true;
            } catch (Exception e) {
                available = false;
                logMySQLUnavailable();

                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().log(Level.FINE, "Detailed MySQL initialization exception:", e);
                }

                closeDataSource();
                return false;
            }
        }
    }

    public boolean reconnect() {
        synchronized (connectionLock) {
            if (!mysqlConfigured || isConnected()) return isConnected();
            boolean connected = tryMySQL(plugin.getConfig(), true);
            if (connected) {
                recovering = true;
            }
            return connected;
        }
    }

    public void markUnavailable(Throwable cause) {
        synchronized (connectionLock) {
            available = false;
            recovering = false;
            closeDataSource();
            logMySQLUnavailable();
            if (cause != null) {
                logMySQLOperationFailure("MySQL encountered an exception and was marked unavailable.", cause);
            }
        }
    }

    public void finishRecovery() {
        synchronized (connectionLock) {
            if (available && dataSource != null && !dataSource.isClosed()) {
                recovering = false;
            }
        }
    }

    /**
     * Checks if MySQL is connected AND not currently undergoing data recovery.
     * Lock-free check to prevent blocking read ops.
     */
    public boolean isReadyForReads() {
        return isConnected() && !recovering;
    }

    public boolean isMysqlConfigured() {
        return mysqlConfigured;
    }

    public boolean isRecovering() {
        return recovering;
    }

    public void logMySQLOperationFailure(String message, Throwable cause) {
        if (cause == null) {
            plugin.getLogger().warning(message);
            return;
        }

        String fingerprint = cause.getClass().getName() + ":" + cause.getMessage();
        synchronized (connectionLock) {
            if (fingerprint.equals(lastLoggedOperationFailure)) return;
            lastLoggedOperationFailure = fingerprint;
            plugin.getLogger().log(Level.WARNING, message, cause);
        }
    }

    private void logMySQLUnavailable() {
        if (fallbackMessageLogged) return;
        plugin.getLogger().warning("MySQL unavailable. Falling back to YAML storage.");
        fallbackMessageLogged = true;
    }

    public void disconnect() {
        synchronized (connectionLock) {
            available = false;
            recovering = false;
            closeDataSource();
        }
    }

    private void closeDataSource() {
        if (dataSource != null) {
            try {
                if (!dataSource.isClosed()) {
                    dataSource.close();
                }
            } catch (Exception ignored) {
            } finally {
                dataSource = null;
            }
        }
    }

    /**
     * Lock-free connection retrieval directly from HikariCP pool.
     */
    public Connection getConnection() throws SQLException {
        if (!available) {
            throw new SQLException("MySQL DataSource is marked unavailable.");
        }
        HikariDataSource ds = this.dataSource;
        if (ds == null || ds.isClosed()) {
            throw new SQLException("MySQL DataSource is not initialized or has been closed.");
        }
        return ds.getConnection();
    }

    /**
     * Lock-free status check.
     */
    public boolean isConnected() {
        HikariDataSource ds = this.dataSource;
        return available && ds != null && !ds.isClosed();
    }
}
