package net.bumpier.brankup.data.source;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.IDatabaseService;
import net.bumpier.brankup.data.PlayerRankData;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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

        final String createPlayerDataSql = String.format("""
            CREATE TABLE IF NOT EXISTS %splayer_data (
                uuid VARCHAR(36) PRIMARY KEY,
                rank BIGINT NOT NULL DEFAULT 0,
                prestige BIGINT NOT NULL DEFAULT 0,
                auto_rankup_enabled BOOLEAN NOT NULL DEFAULT 0,
                auto_prestige_enabled BOOLEAN NOT NULL DEFAULT 0
            );
            """, tablePrefix);

        final String createRewardsSql = String.format("""
            CREATE TABLE IF NOT EXISTS %sclaimed_rewards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid VARCHAR(36) NOT NULL,
                reward_key VARCHAR(255) NOT NULL,
                UNIQUE(uuid, reward_key)
            );
            """, tablePrefix);

        runAsync(() -> {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(createPlayerDataSql);
                stmt.execute(createRewardsSql);
                plugin.getLogger().info("SQLite database and tables initialized successfully.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerRankData> loadPlayerData(UUID uuid) {
        return loadClaimedRewards(uuid).thenCompose(claimedRewards -> supplyAsync(() -> {
            final String selectSql = String.format("SELECT * FROM %splayer_data WHERE uuid = ?;", tablePrefix);
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return new PlayerRankData(
                            uuid,
                            rs.getLong("rank"),
                            rs.getLong("prestige"),
                            rs.getBoolean("auto_rankup_enabled"),
                            rs.getBoolean("auto_prestige_enabled"),
                            claimedRewards
                    );
                } else {
                    return PlayerRankData.newPlayer(uuid);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
                return null;
            }
        }));
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerRankData data) {
        return runAsync(() -> {
            final String upsertSql = String.format("""
                INSERT INTO %splayer_data (uuid, rank, prestige, auto_rankup_enabled, auto_prestige_enabled)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    rank = excluded.rank,
                    prestige = excluded.prestige,
                    auto_rankup_enabled = excluded.auto_rankup_enabled,
                    auto_prestige_enabled = excluded.auto_prestige_enabled;
                """, tablePrefix);

            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
                pstmt.setString(1, data.getUuid().toString());
                pstmt.setLong(2, data.getRank());
                pstmt.setLong(3, data.getPrestige());
                pstmt.setBoolean(4, data.isAutoRankupEnabled());
                pstmt.setBoolean(5, data.isAutoPrestigeEnabled());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getUuid(), e);
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