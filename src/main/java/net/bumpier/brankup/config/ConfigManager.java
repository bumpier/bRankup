package net.bumpier.brankup.config;

import net.bumpier.brankup.bRankup;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    private final bRankup plugin;
    private final Map<String, FileConfiguration> configCache = new HashMap<>();

    public ConfigManager(bRankup plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads all essential configuration files and clears the cache.
     * Dynamic configs like progression files are loaded on-demand.
     */
    public void loadConfigs() {
        configCache.clear();
        // Pre-load essential configs. Progression configs will be loaded as needed.
        getConfig("config.yml");
        getConfig("messages.yml");
        plugin.getLogger().info("Essential configurations have been loaded.");
    }

    /**
     * A robust method to get a configuration file.
     * If the file is not in the cache, it loads it from disk.
     * If the file does not exist, it's created.
     *
     * @param fileName The name of the file to load (e.g., "config.yml", "rebirth.yml").
     * @return The loaded FileConfiguration object, or null on failure.
     */
    public FileConfiguration getConfig(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        String lowerCaseFileName = fileName.toLowerCase();

        // Return from cache if it exists
        if (configCache.containsKey(lowerCaseFileName)) {
            return configCache.get(lowerCaseFileName);
        }

        // Not in cache, so load it
        plugin.getLogger().info("Loading configuration file: " + fileName);
        FileConfiguration config = loadConfigFromFile(fileName);

        if (config != null) {
            configCache.put(lowerCaseFileName, config);
        } else {
            plugin.getLogger().warning("Failed to load configuration: " + fileName);
        }
        return config;
    }

    /**
     * Handles the file loading logic. If a file doesn't exist, it attempts to
     * copy it from the plugin's resources. If that fails (as it will for custom
     * progression files), it creates a new empty file.
     *
     * @param fileName The file to load.
     * @return The loaded FileConfiguration.
     */
    private FileConfiguration loadConfigFromFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.getLogger().info("Configuration file not found, attempting to create: " + fileName);
            try {
                // This will fail with IllegalArgumentException if the resource is not in the JAR
                plugin.saveResource(fileName, false);
                plugin.getLogger().info("Created '" + fileName + "' from plugin resources.");
            } catch (IllegalArgumentException e) {
                // This is the expected path for custom, user-defined progression files like 'rebirth.yml'
                plugin.getLogger().warning("Resource '" + fileName + "' not found in JAR. Creating an empty file. Please configure it and reload.");
                try {
                    if (file.createNewFile()) {
                        plugin.getLogger().info("Successfully created empty file: " + fileName);
                    }
                } catch (IOException ioException) {
                    plugin.getLogger().log(Level.SEVERE, "Fatal: Could not create new configuration file: " + fileName, ioException);
                    return null; // Return null on critical failure
                }
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Gets the main configuration file from the cache.
     */
    public FileConfiguration getMainConfig() {
        return getConfig("config.yml");
    }

    /**
     * Gets the messages configuration file from the cache.
     */
    public FileConfiguration getMessagesConfig() {
        return getConfig("messages.yml");
    }

    /**
     * Forces a reload of a specific configuration file by clearing it from the cache
     * and loading it again from disk.
     * @param fileName The name of the configuration to reload.
     */
    public void reloadConfig(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();
        configCache.remove(lowerCaseFileName);
        getConfig(lowerCaseFileName); // This will load it again and put it back in the cache
        plugin.getLogger().info("Reloaded configuration: " + fileName);
    }
}