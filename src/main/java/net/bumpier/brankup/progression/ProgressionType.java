package net.bumpier.brankup.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Represents a progression type (rankup, prestige, rebirth, etc.)
 * This class encapsulates all configuration and behavior for a single progression type.
 */
public class ProgressionType {
    
    private final String id;
    private final String configFile;
    private final String follows;
    private final String displayName;
    private final String command;
    private final FileConfiguration config;
    private final String commandAlias;
    
    // Core settings
    private final boolean enabled;
    private final long limit;
    
    // Requirements
    private final boolean requiresMaxLevel;
    private final List<String> customRequirements;
    
    // Reset settings
    private final boolean resetPrevious;
    private final Map<String, Boolean> resetCurrencies;
    private final List<String> customResets;
    
    // Features
    private final boolean autoProgressionEnabled;
    private final String autoProgressionPermission;
    private final long autoProgressionDelay;
    private final boolean maxProgressionEnabled;
    private final String maxProgressionPermission;
    
    // Currency settings
    private final String currencyType;
    private final String calculationMode;
    private final Map<String, Object> currencySettings;
    
    // Display settings
    private final String progressionDisplay;
    private final String maxLevelDisplay;
    
    // Rewards
    private final List<String> everyProgressionRewards;
    private final Map<String, List<String>> firstTimeRewards;
    private final Map<String, List<String>> intervalRewards;
    
    public ProgressionType(String id, String configFile, String follows, String displayName, 
                          String command, FileConfiguration config) {
        this.id = id;
        this.configFile = configFile;
        this.follows = follows;
        this.displayName = displayName;
        this.command = command;
        this.config = config;
        
        // Load all configuration values
        this.enabled = config.getBoolean("enabled", true);
        this.limit = config.getLong("limit", 50);
        this.commandAlias = config.getString("command-alias");
        
        // Requirements
        ConfigurationSection requirementsSection = config.getConfigurationSection("requirements");
        this.requiresMaxLevel = requirementsSection != null ? 
            requirementsSection.getBoolean("requires-max-level", true) : true;
        this.customRequirements = requirementsSection != null ? 
            requirementsSection.getStringList("custom-requirements") : new ArrayList<>();
        
        // Reset settings
        ConfigurationSection resetSection = config.getConfigurationSection("resets");
        this.resetPrevious = resetSection != null ? 
            resetSection.getBoolean("reset-previous", true) : true;
        
        ConfigurationSection currencyResetSection = resetSection != null ? 
            resetSection.getConfigurationSection("reset-currencies") : null;
        this.resetCurrencies = new HashMap<>();
        if (currencyResetSection != null) {
            for (String currency : currencyResetSection.getKeys(false)) {
                resetCurrencies.put(currency, currencyResetSection.getBoolean(currency, false));
            }
        }
        
        this.customResets = resetSection != null ? 
            resetSection.getStringList("custom-resets") : new ArrayList<>();
        
        // Features
        ConfigurationSection featuresSection = config.getConfigurationSection("features");
        ConfigurationSection autoSection = featuresSection != null ? 
            featuresSection.getConfigurationSection("auto-progression") : null;
        this.autoProgressionEnabled = autoSection != null ? 
            autoSection.getBoolean("enabled", false) : false;
        this.autoProgressionPermission = autoSection != null ? 
            autoSection.getString("permission", "brankup." + id + ".auto") : "brankup." + id + ".auto";
        this.autoProgressionDelay = autoSection != null ? 
            autoSection.getLong("delay", 20) : 20;
        
        ConfigurationSection maxSection = featuresSection != null ? 
            featuresSection.getConfigurationSection("max-progression") : null;
        this.maxProgressionEnabled = maxSection != null ? 
            maxSection.getBoolean("enabled", false) : false;
        this.maxProgressionPermission = maxSection != null ? 
            maxSection.getString("permission", "brankup." + id + ".max") : "brankup." + id + ".max";
        
        // Currency settings
        ConfigurationSection currencySection = config.getConfigurationSection("currency-settings");
        this.currencyType = currencySection != null ? 
            currencySection.getString("currency-type", "money") : "money";
        this.calculationMode = currencySection != null ? 
            currencySection.getString("calculation-mode", "EXPONENTIAL") : "EXPONENTIAL";
        
        this.currencySettings = new HashMap<>();
        if (currencySection != null) {
            ConfigurationSection exponentialSection = currencySection.getConfigurationSection("exponential");
            if (exponentialSection != null) {
                currencySettings.put("exponential.base-cost", exponentialSection.get("base-cost"));
                currencySettings.put("exponential.cost-multiplier", exponentialSection.get("cost-multiplier"));
            }
            
            ConfigurationSection linearSection = currencySection.getConfigurationSection("linear");
            if (linearSection != null) {
                currencySettings.put("linear.base-cost", linearSection.get("base-cost"));
                currencySettings.put("linear.cost-per-level", linearSection.get("cost-per-level"));
            }
        }
        
        // Display settings
        ConfigurationSection displaySection = config.getConfigurationSection("display-settings");
        this.progressionDisplay = displaySection != null ? 
            displaySection.getString("progression-display", "&8[&b" + displayName + " %brankup_" + id + "_level%&8]") : 
            "&8[&b" + displayName + " %brankup_" + id + "_level%&8]";
        this.maxLevelDisplay = displaySection != null ? 
            displaySection.getString("max-level-display", "&c&lMax " + displayName) : 
            "&c&lMax " + displayName;
        
        // Rewards
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        this.everyProgressionRewards = rewardsSection != null ? 
            rewardsSection.getStringList("every-progression") : new ArrayList<>();
        
        ConfigurationSection firstTimeSection = rewardsSection != null ? 
            rewardsSection.getConfigurationSection("first-time-rewards") : null;
        this.firstTimeRewards = new HashMap<>();
        if (firstTimeSection != null) {
            for (String level : firstTimeSection.getKeys(false)) {
                firstTimeRewards.put(level, firstTimeSection.getStringList(level));
            }
        }
        
        ConfigurationSection intervalSection = rewardsSection != null ? 
            rewardsSection.getConfigurationSection("interval-rewards") : null;
        this.intervalRewards = new HashMap<>();
        if (intervalSection != null) {
            for (String interval : intervalSection.getKeys(false)) {
                intervalRewards.put(interval, intervalSection.getStringList(interval));
            }
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getConfigFile() { return configFile; }
    public String getFollows() { return follows; }
    public String getDisplayName() { return displayName; }
    public String getCommand() { return command; }
    public FileConfiguration getConfig() { return config; }
    public boolean isEnabled() { return enabled; }
    public long getLimit() { return limit; }
    public boolean requiresMaxLevel() { return requiresMaxLevel; }
    public List<String> getCustomRequirements() { return customRequirements; }
    public boolean shouldResetPrevious() { return resetPrevious; }
    public Map<String, Boolean> getResetCurrencies() { return resetCurrencies; }
    public List<String> getCustomResets() { return customResets; }
    public boolean isAutoProgressionEnabled() { return autoProgressionEnabled; }
    public String getAutoProgressionPermission() { return autoProgressionPermission; }
    public long getAutoProgressionDelay() { return autoProgressionDelay; }
    public boolean isMaxProgressionEnabled() { return maxProgressionEnabled; }
    public String getMaxProgressionPermission() { return maxProgressionPermission; }
    public String getCurrencyType() { return currencyType; }
    public String getCalculationMode() { return calculationMode; }
    public Map<String, Object> getCurrencySettings() { return currencySettings; }
    public String getProgressionDisplay() { return progressionDisplay; }
    public String getMaxLevelDisplay() { return maxLevelDisplay; }
    public List<String> getEveryProgressionRewards() { return everyProgressionRewards; }
    public Map<String, List<String>> getFirstTimeRewards() { return firstTimeRewards; }
    public Map<String, List<String>> getIntervalRewards() { return intervalRewards; }
    public String getCommandAlias() {return commandAlias;}
    
    /**
     * Check if this progression type can be unlocked by a player
     */
    public boolean canUnlock(String previousProgressionType, long previousLevel, long previousLimit) {
        if (follows == null) {
            return true; // Base progression type
        }
        
        if (!follows.equals(previousProgressionType)) {
            return false; // Wrong progression type
        }
        
        if (requiresMaxLevel && previousLevel < previousLimit) {
            return false; // Requires max level but not reached
        }
        
        return true;
    }
    
    /**
     * Get the progression chain that leads to this type
     */
    public List<String> getProgressionChain() {
        List<String> chain = new ArrayList<>();
        if (follows != null) {
            chain.add(follows);
        }
        return chain;
    }
    
    @Override
    public String toString() {
        return "ProgressionType{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", follows='" + follows + '\'' +
                ", enabled=" + enabled +
                ", limit=" + limit +
                '}';
    }
} 