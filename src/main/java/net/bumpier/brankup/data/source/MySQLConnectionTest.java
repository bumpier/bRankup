package net.bumpier.brankup.data.source;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.bumpier.brankup.bRankup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Utility class to test MySQL connection and operations.
 * This class can be used to verify that the MySQL connection is working properly.
 */
public class MySQLConnectionTest {
    private final bRankup plugin;
    
    public MySQLConnectionTest(bRankup plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Tests the MySQL connection and basic operations.
     * 
     * @return true if the connection test was successful, false otherwise
     */
    public boolean testConnection() {
        plugin.getLogger().info("Testing MySQL connection...");
        
        try {
            // Create a temporary connection pool for testing
            HikariConfig config = new HikariConfig();
            
            String host = plugin.getConfigManager().getMainConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfigManager().getMainConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfigManager().getMainConfig().getString("database.mysql.database", "brankup");
            String username = plugin.getConfigManager().getMainConfig().getString("database.mysql.username", "user");
            String password = plugin.getConfigManager().getMainConfig().getString("database.mysql.password", "password");
            
            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database;
            
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            
            // Minimal connection pool for testing
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(5000); // 5 seconds
            
            try (HikariDataSource dataSource = new HikariDataSource(config)) {
                // Test getting a connection
                try (Connection connection = dataSource.getConnection()) {
                    plugin.getLogger().info("Successfully connected to MySQL database!");
                    
                    // Test creating a table
                    try (PreparedStatement statement = connection.prepareStatement(
                            "CREATE TABLE IF NOT EXISTS mysql_test (id INT PRIMARY KEY, test_value VARCHAR(255))")) {
                        statement.executeUpdate();
                        plugin.getLogger().info("Successfully created test table!");
                    }
                    
                    // Test inserting data
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO mysql_test (id, test_value) VALUES (1, 'Test successful') ON DUPLICATE KEY UPDATE test_value = 'Test successful'")) {
                        statement.executeUpdate();
                        plugin.getLogger().info("Successfully inserted test data!");
                    }
                    
                    // Test querying data
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT test_value FROM mysql_test WHERE id = 1")) {
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                String testValue = resultSet.getString("test_value");
                                plugin.getLogger().info("Successfully queried test data: " + testValue);
                            } else {
                                plugin.getLogger().warning("Failed to query test data!");
                                return false;
                            }
                        }
                    }
                    
                    // Test dropping the table
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DROP TABLE mysql_test")) {
                        statement.executeUpdate();
                        plugin.getLogger().info("Successfully dropped test table!");
                    }
                    
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL connection test failed!", e);
            return false;
        }
    }
    
    /**
     * Runs the connection test and logs the result.
     */
    public void runTest() {
        boolean success = testConnection();
        if (success) {
            plugin.getLogger().info("MySQL connection test completed successfully!");
        } else {
            plugin.getLogger().severe("MySQL connection test failed! Please check your configuration.");
        }
    }
}