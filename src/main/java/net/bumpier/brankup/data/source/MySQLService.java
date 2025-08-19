package net.bumpier.brankup.data.source;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.IDatabaseService;
import net.bumpier.brankup.data.PlayerRankData;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class MySQLService implements IDatabaseService {

    private final bRankup plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> new Thread(r, "bRankup-DB-Thread"));
    private final String tablePrefix;
    private HikariDataSource dataSource;

    public MySQLService(bRankup plugin) {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfigManager().getMainConfig().getString("database.table-prefix", "brankup_");
    }

    @Override
    public void initialize() {
        String host = plugin.getConfigManager().getMainConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfigManager().getMainConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfigManager().getMainConfig().getString("database.mysql.database", "brankup");
        String username = plugin.getConfigManager().getMainConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfigManager().getMainConfig().getString("database.mysql.password", "");
        
        // Configure HikariCP
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(plugin.getConfigManager().getMainConfig().getInt("database.mysql.pool.maximum-pool-size", 10));
        config.setMinimumIdle(plugin.getConfigManager().getMainConfig().getInt("database.mysql.pool.minimum-idle", 2));
        config.setConnectionTimeout(plugin.getConfigManager().getMainConfig().getLong("database.mysql.pool.connection-timeout", 30000)); // 30 seconds
        config.setIdleTimeout(plugin.getConfigManager().getMainConfig().getLong("database.mysql.pool.idle-timeout", 600000)); // 10 minutes
        config.setMaxLifetime(plugin.getConfigManager().getMainConfig().getLong("database.mysql.pool.max-lifetime", 1800000)); // 30 minutes
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("MySQL connection pool initialized successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL connection pool", e);
            return;
        }

        final String createLevelsSql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "progression_levels (uuid VARCHAR(36) NOT NULL, progression_id VARCHAR(255) NOT NULL, level BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, progression_id));";
        final String createAutoStatesSql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "auto_progression_states (uuid VARCHAR(36) NOT NULL, progression_id VARCHAR(255) NOT NULL, is_enabled BOOLEAN NOT NULL DEFAULT 0, PRIMARY KEY (uuid, progression_id));";
        final String createRewardsSql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "claimed_rewards (id INT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL, reward_key VARCHAR(255) NOT NULL, UNIQUE INDEX idx_unique_reward (uuid, reward_key));";

        // Create indices for faster lookups
        final String createLevelsIndexSql = "CREATE INDEX IF NOT EXISTS " + tablePrefix + "idx_levels_uuid ON " + tablePrefix + "progression_levels (uuid);";
        final String createAutoStatesIndexSql = "CREATE INDEX IF NOT EXISTS " + tablePrefix + "idx_auto_states_uuid ON " + tablePrefix + "auto_progression_states (uuid);";
        final String createRewardsIndexSql = "CREATE INDEX IF NOT EXISTS " + tablePrefix + "idx_rewards_uuid ON " + tablePrefix + "claimed_rewards (uuid);";

        runAsync(() -> {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Create tables
                stmt.execute(createLevelsSql);
                stmt.execute(createAutoStatesSql);
                stmt.execute(createRewardsSql);

                // Create indices
                try {
                    stmt.execute(createLevelsIndexSql);
                    stmt.execute(createAutoStatesIndexSql);
                    stmt.execute(createRewardsIndexSql);
                } catch (SQLException e) {
                    // MySQL may not support IF NOT EXISTS for indices, so handle this gracefully
                    if (!e.getMessage().contains("Duplicate key")) {
                        throw e;
                    }
                }

                plugin.getLogger().info("MySQL database tables and indices initialized successfully.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL database", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerRankData> loadPlayerData(UUID uuid) {
        CompletableFuture<Map<String, Long>> levelsFuture = loadProgressionLevels(uuid);
        CompletableFuture<Set<String>> rewardsFuture = loadClaimedRewards(uuid);
        CompletableFuture<Map<String, Boolean>> autoStatesFuture = loadAutoProgressionStates(uuid);

        return CompletableFuture.allOf(levelsFuture, rewardsFuture, autoStatesFuture)
                .thenApplyAsync(v -> {
                    Map<String, Long> levels = levelsFuture.join();
                    Set<String> rewards = rewardsFuture.join();
                    Map<String, Boolean> autoStates = autoStatesFuture.join();
                    return new PlayerRankData(uuid, levels, rewards, autoStates);
                }, executor);
    }

    private CompletableFuture<Map<String, Long>> loadProgressionLevels(UUID uuid) {
        return supplyAsync(() -> {
            Map<String, Long> levels = new HashMap<>();
            String sql = "SELECT progression_id, level FROM " + tablePrefix + "progression_levels WHERE uuid = ?;";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    levels.put(rs.getString("progression_id"), rs.getLong("level"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load progression levels for " + uuid, e);
            }
            return levels;
        });
    }

    private CompletableFuture<Map<String, Boolean>> loadAutoProgressionStates(UUID uuid) {
        return supplyAsync(() -> {
            Map<String, Boolean> states = new HashMap<>();
            String sql = "SELECT progression_id, is_enabled FROM " + tablePrefix + "auto_progression_states WHERE uuid = ?;";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    states.put(rs.getString("progression_id"), rs.getBoolean("is_enabled"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load auto-progression states for " + uuid, e);
            }
            return states;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerRankData data) {
        return runAsync(() -> {
            // MySQL uses different syntax for upsert compared to SQLite
            String levelUpsertSql = "INSERT INTO " + tablePrefix + "progression_levels (uuid, progression_id, level) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE level = VALUES(level);";
            String stateUpsertSql = "INSERT INTO " + tablePrefix + "auto_progression_states (uuid, progression_id, is_enabled) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE is_enabled = VALUES(is_enabled);";

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement levelPs = conn.prepareStatement(levelUpsertSql);
                     PreparedStatement statePs = conn.prepareStatement(stateUpsertSql)) {

                    for (Map.Entry<String, Long> entry : data.getAllProgressionLevels().entrySet()) {
                        levelPs.setString(1, data.getUuid().toString());
                        levelPs.setString(2, entry.getKey());
                        levelPs.setLong(3, entry.getValue());
                        levelPs.addBatch();
                    }
                    levelPs.executeBatch();

                    for (Map.Entry<String, Boolean> entry : data.getAutoProgressionStates().entrySet()) {
                        statePs.setString(1, data.getUuid().toString());
                        statePs.setString(2, entry.getKey());
                        statePs.setBoolean(3, entry.getValue());
                        statePs.addBatch();
                    }
                    statePs.executeBatch();

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getUuid(), e);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database transaction error for " + data.getUuid(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Set<String>> loadClaimedRewards(UUID uuid) {
        return supplyAsync(() -> {
            Set<String> claimedKeys = new HashSet<>();
            final String selectSql = "SELECT reward_key FROM " + tablePrefix + "claimed_rewards WHERE uuid = ?;";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    claimedKeys.add(rs.getString("reward_key"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load claimed rewards for " + uuid, e);
            }
            return claimedKeys;
        });
    }

    @Override
    public CompletableFuture<Void> saveClaimedReward(UUID uuid, String rewardKey) {
        return runAsync(() -> {
            final String insertSql = "INSERT IGNORE INTO " + tablePrefix + "claimed_rewards (uuid, reward_key) VALUES (?, ?);";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, rewardKey);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save claimed reward '" + rewardKey + "' for " + uuid, e);
            }
        });
    }

    @Override
    public void shutdown() {
        // Close the connection pool
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed successfully.");
        }

        // Shutdown the executor service
        executor.shutdown();
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}