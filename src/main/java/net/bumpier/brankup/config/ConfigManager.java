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
    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration rankupConfig;
    private FileConfiguration prestigeConfig;
    
    // Cache for all configuration files
    private final Map<String, FileConfiguration> configCache = new HashMap<>();

    public ConfigManager(bRankup plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads all configuration files from disk.
     */
    public void loadConfigs() {
        // Load main configuration files
        this.mainConfig = loadConfig("config.yml");
        this.messagesConfig = loadConfig("messages.yml");
        
        // Load system-specific configuration files
        this.rankupConfig = loadConfig("rankup.yml");
        this.prestigeConfig = loadConfig("prestige.yml");
        
        // Cache all configurations for easy access
        configCache.clear();
        configCache.put("main", mainConfig);
        configCache.put("messages", messagesConfig);
        configCache.put("rankup", rankupConfig);
        configCache.put("prestige", prestigeConfig);
        
        plugin.getLogger().info("All configuration files loaded successfully");
    }

    /**
     * A robust method to load a specific YAML file.
     * It creates the file from resources if it doesn't exist.
     *
     * @param fileName The name of the file to load (e.g., "config.yml").
     * @return The loaded FileConfiguration object.
     */
    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
                plugin.getLogger().info("Created configuration file: " + fileName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create configuration file: " + fileName, e);
            }
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Validate configuration
        if (config != null) {
            plugin.getLogger().fine("Successfully loaded configuration: " + fileName);
        } else {
            plugin.getLogger().warning("Failed to load configuration: " + fileName);
        }
        
        return config;
    }

    /**
     * Get the main configuration file.
     */
    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    /**
     * Get the messages configuration file.
     */
    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }
    
    /**
     * Get the rankup configuration file.
     */
    public FileConfiguration getRankupConfig() {
        return rankupConfig;
    }
    
    /**
     * Get the prestige configuration file.
     */
    public FileConfiguration getPrestigeConfig() {
        return prestigeConfig;
    }
    
    /**
     * Get a specific configuration file by name.
     * 
     * @param configName The name of the configuration (main, messages, rankup, prestige)
     * @return The FileConfiguration or null if not found
     */
    public FileConfiguration getConfig(String configName) {
        return configCache.get(configName.toLowerCase());
    }
    
    /**
     * Reload a specific configuration file.
     * 
     * @param configName The name of the configuration to reload
     * @return true if successful, false otherwise
     */
    public boolean reloadConfig(String configName) {
        try {
            switch (configName.toLowerCase()) {
                case "main":
                    this.mainConfig = loadConfig("config.yml");
                    configCache.put("main", mainConfig);
                    break;
                case "messages":
                    this.messagesConfig = loadConfig("messages.yml");
                    configCache.put("messages", messagesConfig);
                    break;
                case "rankup":
                    this.rankupConfig = loadConfig("rankup.yml");
                    configCache.put("rankup", rankupConfig);
                    break;
                case "prestige":
                    this.prestigeConfig = loadConfig("prestige.yml");
                    configCache.put("prestige", prestigeConfig);
                    break;
                default:
                    plugin.getLogger().warning("Unknown configuration file: " + configName);
                    return false;
            }
            
            plugin.getLogger().info("Reloaded configuration: " + configName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration: " + configName, e);
            return false;
        }
    }
    
    /**
     * Get all loaded configuration names.
     * 
     * @return Array of configuration names
     */
    public String[] getLoadedConfigs() {
        return configCache.keySet().toArray(new String[0]);
    }
    
    /**
     * Check if a configuration file is loaded.
     * 
     * @param configName The name of the configuration to check
     * @return true if loaded, false otherwise
     */
    public boolean isConfigLoaded(String configName) {
        return configCache.containsKey(configName.toLowerCase());
    }
}