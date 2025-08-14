package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerManagerService;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.ProgressionCostService;
import net.bumpier.brankup.progression.ProgressionRewardService;
import net.bumpier.brankup.progression.RewardDispatcher;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MaxPrestigeCommand implements CommandExecutor {

    private final bRankup plugin;
    private final PlayerManagerService playerManager;
    private final AdventureMessageService messageService;
    private final ProgressionCostService prestigeCostService;
    private final ProgressionRewardService prestigeRewardService;
    private final IEconomyService prestigeEconomyService;
    private final ConfigurationSection prestigeConfig;
    
    // OPTIMIZATION: Cache frequently accessed config values
    private final String permission;
    private final long maxRankRequired;
    private final long maxPrestige;
    private final boolean requiresMaxLevel;
    private final boolean resetRankup;
    private final List<String> currenciesToReset;

    public MaxPrestigeCommand(bRankup plugin) {
        this.plugin = plugin;
        this.playerManager = plugin.getPlayerManagerService();
        this.messageService = plugin.getMessageService();
        this.prestigeCostService = plugin.getPrestigeCostService();
        this.prestigeRewardService = plugin.getPrestigeRewardService();

        this.prestigeConfig = plugin.getConfigManager().getPrestigeConfig();
        String currencyId = prestigeConfig.getString("currency-settings.currency-type");
        this.prestigeEconomyService = plugin.getEconomyService(currencyId);

        // OPTIMIZATION: Cache all config values to avoid repeated lookups
        this.permission = prestigeConfig.getString("features.max-prestige.permission", "brankup.prestige.max");
        this.maxRankRequired = plugin.getConfigManager().getRankupConfig().getLong("limit", 50);
        this.maxPrestige = prestigeConfig.getLong("limit", 50);
        this.requiresMaxLevel = prestigeConfig.getBoolean("requirements.requires-max-level", true);
        this.resetRankup = prestigeConfig.getBoolean("reset-settings.reset-rankup", true);
        
        // OPTIMIZATION: Pre-process currency reset configuration
        this.currenciesToReset = new ArrayList<>();
        ConfigurationSection currencyResetConfig = prestigeConfig.getConfigurationSection("reset-settings.reset-currencies");
        if (currencyResetConfig != null) {
            for (String currencyKey : currencyResetConfig.getKeys(false)) {
                if (currencyResetConfig.getBoolean(currencyKey)) {
                    currenciesToReset.add(currencyKey);
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }

        // OPTIMIZATION: Use cached permission value
        if (!player.hasPermission(permission)) {
            messageService.sendMessage(player, "error-no-permission");
            return true;
        }

        if (prestigeEconomyService == null) {
            String currencyId = prestigeConfig.getString("currency-settings.currency-type", "N/A");
            messageService.sendMessage(player, "error-economy-not-found", "currency", currencyId);
            return true;
        }

        CompletableFuture.runAsync(() -> handleMaxPrestige(player));
        return true;
    }

    private void handleMaxPrestige(Player player) {
        PlayerRankData data = playerManager.getData(player.getUniqueId());
        if (data == null) {
            messageService.sendMessage(player, "error-data-not-loaded");
            return;
        }

        // OPTIMIZATION: Use cached values for prerequisite checks
        if (requiresMaxLevel && data.getRank() < maxRankRequired) {
            messageService.sendMessage(player, "prestige-fail-rank", "max_rank", String.valueOf(maxRankRequired));
            return;
        }

        final long startingPrestige = data.getPrestige();
        if (startingPrestige >= maxPrestige) {
            messageService.sendMessage(player, "prestige-fail-max-prestige");
            return;
        }

        // OPTIMIZATION: Get balance once and use it for all calculations
        BigDecimal playerBalance = prestigeEconomyService.getBalance(player).join();

        // OPTIMIZATION: Pre-calculate all costs in a single loop for better performance
        List<BigDecimal> costs = new ArrayList<>();
        long prestigesToPurchase = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long currentSimulatedPrestige = startingPrestige;

        // OPTIMIZATION: Use more efficient cost calculation loop
        while (currentSimulatedPrestige < maxPrestige) {
            BigDecimal costForNextPrestige = prestigeCostService.getCost(currentSimulatedPrestige, 0);
            if (playerBalance.compareTo(costForNextPrestige) >= 0) {
                costs.add(costForNextPrestige);
                playerBalance = playerBalance.subtract(costForNextPrestige);
                totalCost = totalCost.add(costForNextPrestige);
                prestigesToPurchase++;
                currentSimulatedPrestige++;
            } else {
                break;
            }
        }

        if (prestigesToPurchase == 0) {
            messageService.sendMessage(player, "maxprestige-fail-cant-afford-next");
            return;
        }

        final long finalPrestigesPurchased = prestigesToPurchase;
        final long finalNewPrestige = startingPrestige + finalPrestigesPurchased;
        final BigDecimal finalTotalCost = totalCost;

        // OPTIMIZATION: Single economy transaction for all prestiges
        prestigeEconomyService.withdraw(player, finalTotalCost).thenAccept(wasSuccessful -> {
            if (wasSuccessful) {
                // OPTIMIZATION: Update prestige level once
                data.setPrestige(finalNewPrestige);

                // OPTIMIZATION: Handle resets efficiently using cached values
                if (resetRankup) {
                    data.setRank(0);
                }
                
                // OPTIMIZATION: Batch currency resets for better performance
                if (!currenciesToReset.isEmpty()) {
                    for (String currencyKey : currenciesToReset) {
                        IEconomyService currencyToReset = plugin.getEconomyService(currencyKey);
                        if (currencyToReset != null) {
                            currencyToReset.set(player, BigDecimal.ZERO);
                        }
                    }
                }

                // OPTIMIZATION: Collect all rewards in a single operation
                List<String> allRewardCommands = new ArrayList<>();
                for (long i = startingPrestige + 1; i <= finalNewPrestige; i++) {
                    allRewardCommands.addAll(prestigeRewardService.collectRewards(player, i));
                }
                
                // OPTIMIZATION: Use optimized reward dispatcher
                if (!allRewardCommands.isEmpty()) {
                    new RewardDispatcher(allRewardCommands).runTaskTimer(plugin, 0L, 1L);
                }

                // OPTIMIZATION: Send success message and title on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String formattedCost = NumberFormat.getCurrencyInstance().format(finalTotalCost);
                    messageService.sendMessage(player, "maxprestige-success",
                            "prestiges_purchased", String.valueOf(finalPrestigesPurchased),
                            "new_prestige", String.valueOf(finalNewPrestige),
                            "total_cost", formattedCost
                    );

                    ConfigurationSection titleConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("prestige-settings.display-settings.prestige-title");
                    if (titleConfig != null && titleConfig.getBoolean("enabled", true)) {
                        messageService.sendTitle(player, titleConfig, "new_prestige", String.valueOf(finalNewPrestige));
                    }
                });

            } else {
                messageService.sendMessage(player, "error-economy-withdraw-fail");
            }
        });
    }
}