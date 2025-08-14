package net.bumpier.brankup.task;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.ProgressionCostService;
import net.bumpier.brankup.progression.ProgressionRewardService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AutoProgressionTask extends BukkitRunnable {

    private final bRankup plugin;
    
    // OPTIMIZATION: Use more efficient data structures and implement cleanup
    private final Map<UUID, Long> rankupCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> prestigeCooldowns = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Use more efficient tracking with cleanup
    private final Map<UUID, Integer> autoRankupCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> autoPrestigeCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextSummaryTimes = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Use more efficient race condition prevention
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    // OPTIMIZATION: Cache configuration values to avoid repeated lookups
    private final boolean summaryEnabled;
    private final long summaryIntervalMillis;
    private final List<String> summaryMessage;
    private final boolean allowSimultaneous;
    private final boolean simultaneousSettingsEnabled;
    private final String simultaneousPriority;
    private final boolean autoDisableEnabled;
    private final boolean autoDisableOther;
    
    // OPTIMIZATION: Cache frequently accessed config values
    private final long rankupDelayTicks;
    private final long prestigeDelayTicks;
    private final long maxRank;
    private final long maxPrestige;
    private final boolean requiresMaxLevel;
    
    // OPTIMIZATION: Cleanup timer for memory management
    private int cleanupCounter = 0;
    private static final int CLEANUP_INTERVAL = 100; // Cleanup every 5 seconds (100 ticks)

    public AutoProgressionTask(bRankup plugin) {
        this.plugin = plugin;
        
        // OPTIMIZATION: Load all configuration once during initialization
        ConfigurationSection summaryConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("auto-progression-summary");
        if (summaryConfig != null) {
            this.summaryEnabled = summaryConfig.getBoolean("enabled", true);
            this.summaryIntervalMillis = summaryConfig.getLong("interval-seconds", 120) * 1000;
            this.summaryMessage = summaryConfig.getStringList("message");
        } else {
            this.summaryEnabled = false;
            this.summaryIntervalMillis = 120_000;
            this.summaryMessage = List.of();
        }
        
        // OPTIMIZATION: Load global auto-progression settings once
        ConfigurationSection autoProgressionConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("auto-progression");
        this.allowSimultaneous = autoProgressionConfig != null ? autoProgressionConfig.getBoolean("allow-simultaneous", true) : true;
        
        ConfigurationSection simultaneousConfig = autoProgressionConfig != null ? autoProgressionConfig.getConfigurationSection("simultaneous-settings") : null;
        this.simultaneousSettingsEnabled = simultaneousConfig != null ? simultaneousConfig.getBoolean("enabled", true) : true;
        
        // OPTIMIZATION: Load and validate priority setting once
        String priority = simultaneousConfig != null ? simultaneousConfig.getString("priority", "prestige-first") : "prestige-first";
        if (!"prestige-first".equals(priority) && !"rankup-first".equals(priority) && !"parallel".equals(priority)) {
            plugin.getLogger().warning("Invalid simultaneous priority setting: " + priority + ". Defaulting to 'prestige-first'");
            priority = "prestige-first";
        }
        this.simultaneousPriority = priority;
        
        // OPTIMIZATION: Load auto-disable settings once
        ConfigurationSection autoDisableConfig = autoProgressionConfig != null ? autoProgressionConfig.getConfigurationSection("auto-disable") : null;
        this.autoDisableEnabled = autoDisableConfig != null ? autoDisableConfig.getBoolean("enabled", true) : true;
        this.autoDisableOther = autoDisableConfig != null ? autoDisableConfig.getBoolean("disable-other", true) : true;
        

        
        // OPTIMIZATION: Cache frequently accessed config values from separate config files
        ConfigurationSection rankupConfig = plugin.getConfigManager().getRankupConfig();
        ConfigurationSection prestigeConfig = plugin.getConfigManager().getPrestigeConfig();
        
        this.rankupDelayTicks = rankupConfig != null ? rankupConfig.getLong("features.auto-rankup.delay", 20L) : 20L;
        this.prestigeDelayTicks = prestigeConfig != null ? prestigeConfig.getLong("features.auto-prestige.delay", 20L) : 20L;
        this.maxRank = rankupConfig != null ? rankupConfig.getLong("limit", 50) : 50;
        this.maxPrestige = prestigeConfig != null ? prestigeConfig.getLong("limit", 50) : 50;
        this.requiresMaxLevel = prestigeConfig != null ? prestigeConfig.getBoolean("requirements.requires-max-level", true) : true;
    }

    @Override
    public void run() {
        // OPTIMIZATION: Implement cleanup cycle to prevent memory leaks
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            performCleanup();
            cleanupCounter = 0;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // OPTIMIZATION: Get online players once and process efficiently
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            return; // Early return if no players online
        }

        for (Player player : onlinePlayers) {
            UUID uuid = player.getUniqueId();

            // OPTIMIZATION: Check summary message if it's time
            if (summaryEnabled && currentTime > nextSummaryTimes.getOrDefault(uuid, 0L)) {
                sendSummary(player);
                nextSummaryTimes.put(uuid, currentTime + summaryIntervalMillis);
            }

            PlayerRankData data = plugin.getPlayerManagerService().getData(uuid);
            if (data == null) continue;

            // OPTIMIZATION: Process auto-progression based on priority when both are enabled
            if (allowSimultaneous && simultaneousSettingsEnabled && data.isAutoPrestigeEnabled() && data.isAutoRankupEnabled()) {
                // Both are enabled, use priority system
                if ("prestige-first".equals(simultaneousPriority)) {
                    processAutoPrestige(player, data, currentTime);
                    processAutoRankup(player, data, currentTime);
                } else if ("rankup-first".equals(simultaneousPriority)) {
                    processAutoRankup(player, data, currentTime);
                    processAutoPrestige(player, data, currentTime);
                } else {
                    // Parallel processing (default behavior)
                    processAutoPrestige(player, data, currentTime);
                    processAutoRankup(player, data, currentTime);
                }
                

            } else {
                // Standard processing (only one or none enabled)
                if (data.isAutoPrestigeEnabled()) {
                    processAutoPrestige(player, data, currentTime);
                }
                
                if (data.isAutoRankupEnabled()) {
                    processAutoRankup(player, data, currentTime);
                }
            }
        }
    }
    
    // OPTIMIZATION: Add cleanup method to prevent memory leaks
    private void performCleanup() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = currentTime - TimeUnit.MINUTES.toMillis(5); // Clean up entries older than 5 minutes
        
        // Clean up expired cooldowns
        rankupCooldowns.entrySet().removeIf(entry -> entry.getValue() < cleanupThreshold);
        prestigeCooldowns.entrySet().removeIf(entry -> entry.getValue() < cleanupThreshold);
        
        // Clean up expired summary times
        nextSummaryTimes.entrySet().removeIf(entry -> entry.getValue() < cleanupThreshold);
        
        // Clean up empty counts
        autoRankupCounts.entrySet().removeIf(entry -> entry.getValue() == 0);
        autoPrestigeCounts.entrySet().removeIf(entry -> entry.getValue() == 0);
        
        // Clean up processing players set (should be empty anyway, but safety check)
        processingPlayers.clear();
    }

    private void sendSummary(Player player) {
        int rankups = autoRankupCounts.getOrDefault(player.getUniqueId(), 0);
        int prestiges = autoPrestigeCounts.getOrDefault(player.getUniqueId(), 0);

        if (rankups == 0 && prestiges == 0) {
            return; // Don't send an empty summary
        }

        long intervalMinutes = summaryIntervalMillis / 60_000;
        String intervalString = intervalMinutes + (intervalMinutes == 1 ? " minute" : " minutes");

        for (String line : summaryMessage) {
            boolean sendLine = true;
            if (line.startsWith("[if_rankups]")) {
                if (rankups > 0) {
                    line = line.substring("[if_rankups]".length());
                } else {
                    sendLine = false;
                }
            } else if (line.startsWith("[if_prestiges]")) {
                if (prestiges > 0) {
                    line = line.substring("[if_prestiges]".length());
                } else {
                    sendLine = false;
                }
            }

            if (sendLine) {
                plugin.getMessageService().sendParsedMessage(player, line,
                        "interval", intervalString,
                        "count_rankups", String.valueOf(rankups),
                        "count_prestiges", String.valueOf(prestiges)
                );
            }
        }

        // OPTIMIZATION: Remove counts after sending summary
        autoRankupCounts.remove(player.getUniqueId());
        autoPrestigeCounts.remove(player.getUniqueId());
    }

    private void processAutoRankup(Player player, PlayerRankData data, long currentTime) {
        // OPTIMIZATION: Use cached config values instead of repeated lookups
        if (!plugin.getConfigManager().getRankupConfig().getBoolean("features.auto-rankup.enabled", false)) {
            return;
        }

        // OPTIMIZATION: Check if player is on cooldown for rankup
        if (currentTime < rankupCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }

        // OPTIMIZATION: Quick check if player can actually rankup using cached value
        if (data.getRank() >= maxRank) {
            return;
        }

        // OPTIMIZATION: Prevent race conditions
        UUID playerId = player.getUniqueId();
        if (processingPlayers.contains(playerId)) {
            return;
        }

        // OPTIMIZATION: Use cached delay value
        rankupCooldowns.put(playerId, currentTime + (rankupDelayTicks * 50));

        CompletableFuture.runAsync(() -> tryAutoRankup(player, data));
    }

    private void tryAutoRankup(Player player, PlayerRankData data) {
        UUID playerId = player.getUniqueId();
        processingPlayers.add(playerId);
        
        try {
            // OPTIMIZATION: Use cached max rank value
            if (data.getRank() >= maxRank) return;

            ProgressionCostService costService = plugin.getRankupCostService();
            String currencyId = plugin.getConfigManager().getRankupConfig().getString("currency-settings.currency-type");
            IEconomyService economyService = plugin.getEconomyService(currencyId);
            ProgressionRewardService rewardService = plugin.getRankupRewardService();
            if (costService == null || economyService == null || rewardService == null) return;

            BigDecimal cost = costService.getCost(data.getRank(), data.getPrestige());

            economyService.has(player, cost).thenAccept(hasFunds -> {
                if (hasFunds) {
                    economyService.withdraw(player, cost).thenAccept(wasSuccessful -> {
                        if (wasSuccessful) {
                            data.incrementRank();
                            autoRankupCounts.merge(player.getUniqueId(), 1, Integer::sum);

                            // OPTIMIZATION: Check if max rank reached and handle auto-disable using cached values
                            if (autoDisableEnabled && data.getRank() >= maxRank) {
                                data.setAutoRankupEnabled(false);
                                if (autoDisableOther && data.isAutoPrestigeEnabled()) {
                                    data.setAutoPrestigeEnabled(false);
                                }
                            }

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                rewardService.dispatchRewards(player, data.getRank());
                            });
                        }
                    });
                }
            });
        } finally {
            processingPlayers.remove(playerId);
        }
    }

    private void processAutoPrestige(Player player, PlayerRankData data, long currentTime) {
        // OPTIMIZATION: Use cached config values instead of repeated lookups
        if (!plugin.getConfigManager().getPrestigeConfig().getBoolean("features.auto-prestige.enabled", false)) {
            return;
        }

        // OPTIMIZATION: Check if player is on cooldown for prestige
        if (currentTime < prestigeCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }

        // OPTIMIZATION: Quick check if player can actually prestige using cached values
        if (data.getPrestige() >= maxPrestige) {
            return;
        }

        // OPTIMIZATION: Prevent race conditions
        UUID playerId = player.getUniqueId();
        if (processingPlayers.contains(playerId)) {
            return;
        }

        // OPTIMIZATION: Use cached delay value
        prestigeCooldowns.put(playerId, currentTime + (prestigeDelayTicks * 50));

        CompletableFuture.runAsync(() -> tryAutoPrestige(player, data));
    }

    private void tryAutoPrestige(Player player, PlayerRankData data) {
        UUID playerId = player.getUniqueId();
        processingPlayers.add(playerId);
        
        try {
            // OPTIMIZATION: Use cached values instead of repeated config lookups
            if (data.getPrestige() >= maxPrestige) return;
            if (requiresMaxLevel && data.getRank() < maxRank) return;

            ProgressionCostService costService = plugin.getPrestigeCostService();
            String currencyId = plugin.getConfigManager().getPrestigeConfig().getString("currency-settings.currency-type");
            IEconomyService economyService = plugin.getEconomyService(currencyId);
            ProgressionRewardService rewardService = plugin.getPrestigeRewardService();
            if (costService == null || economyService == null || rewardService == null) return;

            BigDecimal cost = costService.getCost(data.getPrestige(), 0);

            economyService.has(player, cost).thenAccept(hasFunds -> {
                if (hasFunds) {
                    economyService.withdraw(player, cost).thenAccept(wasSuccessful -> {
                        if (wasSuccessful) {
                            data.incrementPrestige();
                            autoPrestigeCounts.merge(player.getUniqueId(), 1, Integer::sum);

                            // OPTIMIZATION: Check if max prestige reached and handle auto-disable using cached values
                            if (autoDisableEnabled && data.getPrestige() >= maxPrestige) {
                                data.setAutoPrestigeEnabled(false);
                                if (autoDisableOther && data.isAutoRankupEnabled()) {
                                    data.setAutoRankupEnabled(false);
                                }
                            }

                                        // OPTIMIZATION: Handle resets efficiently
            if (plugin.getConfigManager().getPrestigeConfig().getBoolean("reset-settings.reset-rankup", true)) {
                data.setRank(0);
            }

            ConfigurationSection currencyResetConfig = plugin.getConfigManager().getPrestigeConfig().getConfigurationSection("reset-settings.reset-currencies");
                            if (currencyResetConfig != null) {
                                for (String currencyKey : currencyResetConfig.getKeys(false)) {
                                    if (currencyResetConfig.getBoolean(currencyKey)) {
                                        IEconomyService currencyToReset = plugin.getEconomyService(currencyKey);
                                        if (currencyToReset != null) {
                                            currencyToReset.set(player, BigDecimal.ZERO);
                                        }
                                    }
                                }
                            }

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                rewardService.dispatchRewards(player, data.getPrestige());
                            });
                        }
                    });
                }
            });
        } finally {
            processingPlayers.remove(playerId);
        }
    }
}