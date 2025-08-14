package net.bumpier.brankup;

import com.edwardbelt.edprison.utils.CurrencyUtils;
import net.bumpier.brankup.command.*;
import net.bumpier.brankup.config.ConfigManager;
import net.bumpier.brankup.data.IDatabaseService;
import net.bumpier.brankup.data.PlayerManagerService;
import net.bumpier.brankup.data.source.SQLiteService;
import net.bumpier.brankup.economy.EdPrisonEconomyService;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.papi.bRankupExpansion;
import net.bumpier.brankup.progression.ProgressionCostService;
import net.bumpier.brankup.progression.ProgressionRewardService;
import net.bumpier.brankup.progression.ProgressionChainManager;
import net.bumpier.brankup.task.AutoProgressionTask;
import net.bumpier.brankup.util.AdventureMessageService;
import net.bumpier.brankup.util.PerformanceMonitor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class bRankup extends JavaPlugin {

    private ConfigManager configManager;
    private IDatabaseService databaseService;
    private PlayerManagerService playerManagerService;

    private ProgressionCostService rankupCostService;
    private ProgressionCostService prestigeCostService;
    private ProgressionRewardService rankupRewardService;
    private ProgressionRewardService prestigeRewardService;
    private ProgressionChainManager progressionChainManager;

    private BukkitAudiences adventure;
    private AdventureMessageService messageService;
    private PerformanceMonitor performanceMonitor;

    private final Map<String, IEconomyService> economyServices = new HashMap<>();

    @Override
    public void onEnable() {
        final Logger logger = getLogger();
        logger.info("Initializing bRankup...");

        if (!getServer().getPluginManager().isPluginEnabled("EdPrison")) {
            logger.severe("Disabling bRankup: EdPrison dependency not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.adventure = BukkitAudiences.create(this);
        this.configManager = new ConfigManager(this);
        configManager.loadConfigs();
        this.messageService = new AdventureMessageService(this.adventure, configManager);
        this.performanceMonitor = new PerformanceMonitor(this);

        setupEconomyServices();
        setupDatabase();
        setupProgressionServices();
        setupProgressionChainManager();

        this.playerManagerService = new PlayerManagerService(this, databaseService);

        Objects.requireNonNull(getCommand("rankup")).setExecutor(new RankupCommand(this));
        Objects.requireNonNull(getCommand("maxrankup")).setExecutor(new MaxRankupCommand(this));
        Objects.requireNonNull(getCommand("brankupadmin")).setExecutor(new bRankupAdminCommand(this));
        Objects.requireNonNull(getCommand("prestige")).setExecutor(new PrestigeCommand(this));
        Objects.requireNonNull(getCommand("maxprestige")).setExecutor(new MaxPrestigeCommand(this));
        Objects.requireNonNull(getCommand("autorankup")).setExecutor(new AutoRankupCommand(this));
        Objects.requireNonNull(getCommand("autoprestige")).setExecutor(new AutoPrestigeCommand(this));
        Objects.requireNonNull(getCommand("performance")).setExecutor(new PerformanceCommand(this));
        Objects.requireNonNull(getCommand("progression")).setExecutor(new ProgressionCommand(this));

        // OPTIMIZATION: Run auto-progression task based on performance configuration
        // This allows server admins to fine-tune performance vs responsiveness
        long frequency = configManager.getMainConfig().getLong("performance.auto-progression-frequency", 100L);
        new AutoProgressionTask(this).runTaskTimerAsynchronously(this, frequency, frequency);


        // Register PlaceholderAPI Expansion
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new bRankupExpansion(this).register();
            logger.info("Successfully registered PlaceholderAPI expansion.");
        } else {
            logger.warning("PlaceholderAPI not found. Placeholders will not be available.");
        }

        logger.info("bRankup has been enabled successfully.");
    }

    /**
     * Performs a deep reload of the plugin's configuration and services.
     */
    public void performReload() {
        getLogger().info("Performing a deep reload of configurations and services...");

        configManager.loadConfigs();

        economyServices.clear();
        setupEconomyServices();
        setupProgressionServices();
        
        // OPTIMIZATION: Clear all caches during reload to ensure consistency
        if (playerManagerService != null) {
            playerManagerService.clearCache();
        }
        
        // OPTIMIZATION: Clear cost calculation caches
        if (rankupCostService != null) {
            rankupCostService.clearCache();
        }
        if (prestigeCostService != null) {
            prestigeCostService.clearCache();
        }
        
        // OPTIMIZATION: Reload system-specific configurations
        configManager.reloadConfig("rankup");
        configManager.reloadConfig("prestige");
        
        // OPTIMIZATION: Reload progression chain manager
        if (progressionChainManager != null) {
            progressionChainManager.loadProgressionTypes();
        }

        getLogger().info("Reload complete.");
    }

    @Override
    public void onDisable() {
        getLogger().info("bRankup is disabling...");
        if (playerManagerService != null) {
            playerManagerService.saveAll();
        }
        if (databaseService != null) {
            databaseService.shutdown();
        }
        if(this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        getLogger().info("bRankup has been disabled.");
    }

    private void setupProgressionServices() {
        // Use the new separate configuration files
        ConfigurationSection rankupCurrencyConfig = configManager.getRankupConfig().getConfigurationSection("currency-settings");
        if (rankupCurrencyConfig != null) {
            this.rankupCostService = new ProgressionCostService(rankupCurrencyConfig);
        }
        ConfigurationSection rankupRewardsConfig = configManager.getRankupConfig().getConfigurationSection("rewards");
        this.rankupRewardService = new ProgressionRewardService(this, rankupRewardsConfig);

        ConfigurationSection prestigeCurrencyConfig = configManager.getPrestigeConfig().getConfigurationSection("currency-settings");
        if (prestigeCurrencyConfig != null) {
            this.prestigeCostService = new ProgressionCostService(prestigeCurrencyConfig);
        }
        ConfigurationSection prestigeRewardsConfig = configManager.getPrestigeConfig().getConfigurationSection("rewards");
        this.prestigeRewardService = new ProgressionRewardService(this, prestigeRewardsConfig);
    }
    
    private void setupProgressionChainManager() {
        this.progressionChainManager = new ProgressionChainManager(this);
        this.progressionChainManager.loadProgressionTypes();
    }

    private void setupEconomyServices() {
        final Logger logger = getLogger();
        logger.info("Registering currencies from config.yml...");

        ConfigurationSection currencySection = configManager.getMainConfig().getConfigurationSection("currencies");

        if (currencySection == null) {
            logger.warning("No 'currencies' section found in config.yml. No economy services will be registered.");
            return;
        }

        for (String currencyKey : currencySection.getKeys(false)) {
            registerEconomyService(currencyKey);
        }
    }

    private void registerEconomyService(String currencyId) {
        if (economyServices.containsKey(currencyId)) {
            return;
        }

        if (!CurrencyUtils.isCurrency(currencyId)) {
            getLogger().severe("The currency '" + currencyId + "' specified in config.yml does not exist in EdPrison!");
            return;
        }

        economyServices.put(currencyId, new EdPrisonEconomyService(currencyId));
        getLogger().info("Successfully registered economy service for currency: " + currencyId);
    }

    private void setupDatabase() {
        String dbType = configManager.getMainConfig().getString("database.type", "SQLite").toUpperCase();
        if (dbType.equals("SQLITE")) {
            this.databaseService = new SQLiteService(this);
        } else {
            getLogger().log(Level.WARNING, "MySQL not implemented yet. Defaulting to SQLite.");
            this.databaseService = new SQLiteService(this);
        }
        databaseService.initialize();
    }

    // --- Service Getters ---
    public ConfigManager getConfigManager() { return configManager; }
    public PlayerManagerService getPlayerManagerService() { return playerManagerService; }
    public IDatabaseService getDatabaseService() { return databaseService; }
    public AdventureMessageService getMessageService() { return messageService; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public IEconomyService getEconomyService(String currencyId) { return economyServices.get(currencyId); }
    public ProgressionCostService getRankupCostService() { return rankupCostService; }
    public ProgressionCostService getPrestigeCostService() { return prestigeCostService; }
    public ProgressionRewardService getRankupRewardService() { return rankupRewardService; }
    public ProgressionRewardService getPrestigeRewardService() { return prestigeRewardService; }
    public ProgressionChainManager getProgressionChainManager() { return progressionChainManager; }
}