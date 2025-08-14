package net.bumpier.brankup.data.source;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.IDatabaseService;
import net.bumpier.brankup.data.PlayerRankData;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class SQLiteService implements IDatabaseService {

    private final bRankup plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bRankup-DB-Thread"));
    private final String tablePrefix;
    private String connectionString;

    public SQLiteService(bRankup plugin) {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfigManager().getMainConfig().getString("database.table-prefix", "brankup_");
    }

    @Override
    public void initialize() {
        File dbFile = new File(plugin.getDataFolder(), "playerdata.db");
        this.connectionString = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // New, more flexible schema
        final String createLevelsSql = "CREATE TABLE IF NOT EXISTS progression_levels (uuid VARCHAR(36) NOT NULL, progression_id VARCHAR(255) NOT NULL, level BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, progression_id));";
        final String createAutoStatesSql = "CREATE TABLE IF NOT EXISTS auto_progression_states (uuid VARCHAR(36) NOT NULL, progression_id VARCHAR(255) NOT NULL, is_enabled BOOLEAN NOT NULL DEFAULT 0, PRIMARY KEY (uuid, progression_id));";
        final String createRewardsSql = "CREATE TABLE IF NOT EXISTS claimed_rewards (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid VARCHAR(36) NOT NULL, reward_key VARCHAR(255) NOT NULL, UNIQUE(uuid, reward_key));";

        runAsync(() -> {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(createLevelsSql);
                stmt.execute(createAutoStatesSql);
                stmt.execute(createRewardsSql);
                plugin.getLogger().info("SQLite database tables initialized successfully.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerRankData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> levels = loadProgressionLevels(uuid).join();
            Set<String> rewards = loadClaimedRewards(uuid).join();
            Map<String, Boolean> autoStates = loadAutoProgressionStates(uuid).join();
            return new PlayerRankData(uuid, levels, rewards, autoStates);
        }, executor);
    }

    private CompletableFuture<Map<String, Long>> loadProgressionLevels(UUID uuid) {
        return supplyAsync(() -> {
            Map<String, Long> levels = new HashMap<>();
            String sql = String.format("SELECT progression_id, level FROM %sprogression_levels WHERE uuid = ?;", tablePrefix);
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
            String sql = String.format("SELECT progression_id, is_enabled FROM %sauto_progression_states WHERE uuid = ?;", tablePrefix);
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
            String levelUpsertSql = "INSERT INTO progression_levels (uuid, progression_id, level) VALUES (?, ?, ?) ON CONFLICT(uuid, progression_id) DO UPDATE SET level = excluded.level;";
            String stateUpsertSql = "INSERT INTO auto_progression_states (uuid, progression_id, is_enabled) VALUES (?, ?, ?) ON CONFLICT(uuid, progression_id) DO UPDATE SET is_enabled = excluded.is_enabled;";

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
            final String selectSql = String.format("SELECT reward_key FROM %sclaimed_rewards WHERE uuid = ?;", tablePrefix);
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
            final String insertSql = String.format("INSERT OR IGNORE INTO %sclaimed_rewards (uuid, reward_key) VALUES (?, ?);", tablePrefix);
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
        executor.shutdown();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString);
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}