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

public class MaxRankupCommand implements CommandExecutor {

    private final bRankup plugin;
    private final PlayerManagerService playerManager;
    private final ProgressionCostService costService;
    private final AdventureMessageService messageService;
    private final IEconomyService economyService;
    private final ProgressionRewardService rewardService;
    
    // OPTIMIZATION: Cache frequently accessed config values
    private final long maxRank;
    private final String permission;

    public MaxRankupCommand(bRankup plugin) {
        this.plugin = plugin;
        this.playerManager = plugin.getPlayerManagerService();
        this.costService = plugin.getRankupCostService();
        this.messageService = plugin.getMessageService();
        this.rewardService = plugin.getRankupRewardService();

        String currencyId = plugin.getConfigManager().getRankupConfig().getString("currency-settings.currency-type");
        this.economyService = plugin.getEconomyService(currencyId);
        
        // OPTIMIZATION: Cache config values to avoid repeated lookups
        this.maxRank = plugin.getConfigManager().getRankupConfig().getLong("limit", 50);
        this.permission = plugin.getConfigManager().getRankupConfig().getString("features.max-rankup.permission", "brankup.rank.max");
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

        if (economyService == null) {
            String currencyId = plugin.getConfigManager().getMainConfig().getString("rankup-settings.currency-settings.currency-type", "N/A");
            messageService.sendMessage(player, "error-economy-not-found", "currency", currencyId);
            return true;
        }

        CompletableFuture.runAsync(() -> handleMaxRankup(player));
        return true;
    }

    private void handleMaxRankup(Player player) {
        // OPTIMIZATION: Use cached max rank value
        PlayerRankData data = playerManager.getData(player.getUniqueId());
        if (data == null) {
            messageService.sendMessage(player, "error-data-not-loaded");
            return;
        }

        final long startingRank = data.getRank();
        if (startingRank >= maxRank) {
            messageService.sendMessage(player, "rankup-fail-max-rank");
            return;
        }

        final long currentPrestige = data.getPrestige();
        
        // OPTIMIZATION: Get balance once and use it for all calculations
        BigDecimal playerBalance = economyService.getBalance(player).join();

        // OPTIMIZATION: Pre-calculate all costs in a single loop for better performance
        List<BigDecimal> costs = new ArrayList<>();
        long ranksToPurchase = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long currentSimulatedRank = startingRank;

        // OPTIMIZATION: Use more efficient cost calculation loop
        while (currentSimulatedRank < maxRank) {
            BigDecimal costForNextRank = costService.getCost(currentSimulatedRank, currentPrestige);
            if (playerBalance.compareTo(costForNextRank) >= 0) {
                costs.add(costForNextRank);
                playerBalance = playerBalance.subtract(costForNextRank);
                totalCost = totalCost.add(costForNextRank);
                ranksToPurchase++;
                currentSimulatedRank++;
            } else {
                break;
            }
        }

        if (ranksToPurchase == 0) {
            messageService.sendMessage(player, "maxrankup-fail-cant-afford-next");
            return;
        }

        final long finalRanksPurchased = ranksToPurchase;
        final long finalNewRank = startingRank + finalRanksPurchased;
        final BigDecimal finalTotalCost = totalCost;

        // OPTIMIZATION: Single economy transaction for all ranks
        economyService.withdraw(player, finalTotalCost).thenAccept(wasSuccessful -> {
            if (wasSuccessful) {
                // OPTIMIZATION: Update rank once instead of multiple times
                data.setRank(finalNewRank);

                // OPTIMIZATION: Collect all rewards in a single operation
                List<String> allRewardCommands = new ArrayList<>();
                for (long i = startingRank + 1; i <= finalNewRank; i++) {
                    allRewardCommands.addAll(rewardService.collectRewards(player, i));
                }

                // OPTIMIZATION: Use optimized reward dispatcher
                if (!allRewardCommands.isEmpty()) {
                    new RewardDispatcher(allRewardCommands).runTaskTimer(plugin, 0L, 1L);
                }

                // OPTIMIZATION: Send success message and title on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String formattedCost = NumberFormat.getCurrencyInstance().format(finalTotalCost);
                    messageService.sendMessage(player, "maxrankup-success",
                            "ranks_purchased", String.valueOf(finalRanksPurchased),
                            "new_rank", String.valueOf(finalNewRank),
                            "total_cost", formattedCost
                    );

                    ConfigurationSection titleConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("rankup-settings.display-settings.rankup-title");
                    if (titleConfig != null && titleConfig.getBoolean("enabled", true)) {
                        messageService.sendTitle(player, titleConfig, "new_rank", String.valueOf(finalNewRank));
                    }
                });

            } else {
                messageService.sendMessage(player, "error-economy-withdraw-fail");
            }
        });
    }
}