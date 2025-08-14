package net.bumpier.brankup;

import com.edwardbelt.edprison.utils.CurrencyUtils;
import net.bumpier.brankup.command.PerformanceCommand;
import net.bumpier.brankup.command.ProgressionCommand;
import net.bumpier.brankup.command.bRankupAdminCommand;
import net.bumpier.brankup.command.generic.GenericAutoToggleCommand;
import net.bumpier.brankup.command.generic.GenericMaxProgressionCommand;
import net.bumpier.brankup.command.generic.GenericProgressionCommand;
import net.bumpier.brankup.config.ConfigManager;
import net.bumpier.brankup.data.IDatabaseService;
import net.bumpier.brankup.data.PlayerManagerService;
import net.bumpier.brankup.data.source.SQLiteService;
import net.bumpier.brankup.economy.EdPrisonEconomyService;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.papi.bRankupExpansion;
import net.bumpier.brankup.progression.ProgressionChainManager;
import net.bumpier.brankup.progression.ProgressionCostService;
import net.bumpier.brankup.progression.ProgressionRewardService;
import net.bumpier.brankup.progression.ProgressionType;
import net.bumpier.brankup.task.AutoProgressionTask;
import net.bumpier.brankup.util.AdventureMessageService;
import net.bumpier.brankup.util.PerformanceMonitor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class bRankup extends JavaPlugin {

    private ConfigManager configManager;
    private IDatabaseService databaseService;
    private PlayerManagerService playerManagerService;
    private ProgressionChainManager progressionChainManager;
    private BukkitAudiences adventure;
    private AdventureMessageService messageService;
    private PerformanceMonitor performanceMonitor;

    private final Map<String, IEconomyService> economyServices = new HashMap<>();
    private final Map<String, ProgressionCostService> costServices = new HashMap<>();
    private final Map<String, ProgressionRewardService> rewardServices = new HashMap<>();

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

        this.progressionChainManager = new ProgressionChainManager(this);
        progressionChainManager.loadProgressionTypes();

        setupProgressionServices();
        this.playerManagerService = new PlayerManagerService(this, databaseService);

        registerCommands();

        long frequency = configManager.getMainConfig().getLong("performance.auto-progression-frequency", 100L);
        new AutoProgressionTask(this).runTaskTimerAsynchronously(this, frequency, frequency);

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new bRankupExpansion(this).register();
            logger.info("Successfully registered PlaceholderAPI expansion.");
        } else {
            logger.warning("PlaceholderAPI not found. Placeholders will not be available.");
        }

        logger.info("bRankup has been enabled successfully.");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("brankupadmin")).setExecutor(new bRankupAdminCommand(this));
        Objects.requireNonNull(getCommand("performance")).setExecutor(new PerformanceCommand(this));
        Objects.requireNonNull(getCommand("progression")).setExecutor(new ProgressionCommand(this));

        for (ProgressionType type : progressionChainManager.getAllProgressionTypes()) {
            if (!type.isEnabled()) continue;

            registerCommand(type.getCommand(), new GenericProgressionCommand(this, type), type.getCommandAlias());

            if (type.isMaxProgressionEnabled()) {
                registerCommand("max" + type.getId(), new GenericMaxProgressionCommand(this, type), "m" + type.getId());
            }

            if (type.isAutoProgressionEnabled()) {
                registerCommand("auto" + type.getId(), new GenericAutoToggleCommand(this, type), "a" + type.getId());
            }
        }
    }

    private void registerCommand(String name, CommandExecutor executor, String... aliases) {
        try {
            final Field bukkitCommandMap = SimplePluginManager.class.getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer().getPluginManager());

            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            PluginCommand command = c.newInstance(name, this);

            command.setExecutor(executor);
            if (aliases != null && aliases.length > 0 && aliases[0] != null) {
                command.setAliases(List.of(aliases));
            }
            commandMap.register(this.getDescription().getName(), command);
            getLogger().info("Dynamically registered command: /" + name);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to dynamically register command: /" + name, e);
        }
    }

    public void performReload() {
        getLogger().info("Performing a deep reload of configurations and services...");
        configManager.loadConfigs();

        progressionChainManager.loadProgressionTypes();

        economyServices.clear();
        setupEconomyServices();
        setupProgressionServices();

        if (playerManagerService != null) {
            playerManagerService.clearCache();
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
        costServices.clear();
        rewardServices.clear();
        for (ProgressionType type : progressionChainManager.getAllProgressionTypes()) {
            ConfigurationSection currencyConfig = type.getConfig().getConfigurationSection("currency-settings");
            if (currencyConfig != null) {
                costServices.put(type.getId(), new ProgressionCostService(currencyConfig));
            }

            ConfigurationSection rewardsConfig = type.getConfig().getConfigurationSection("rewards");
            if (rewardsConfig != null) {
                rewardServices.put(type.getId(), new ProgressionRewardService(this, rewardsConfig));
            }
        }
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
        if (economyServices.containsKey(currencyId)) return;
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
    public ProgressionChainManager getProgressionChainManager() { return progressionChainManager; }
    public Map<String, ProgressionCostService> getCostServices() { return costServices; }
    public Map<String, ProgressionRewardService> getRewardServices() { return rewardServices; }
}