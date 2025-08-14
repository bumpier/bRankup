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
import java.util.List;
import java.util.logging.Level;

public class RankupCommand implements CommandExecutor {

    private final bRankup plugin;
    private final PlayerManagerService playerManager;
    private final ProgressionCostService costService;
    private final AdventureMessageService messageService;
    private final IEconomyService economyService;
    private final ProgressionRewardService rewardService;

    public RankupCommand(bRankup plugin) {
        this.plugin = plugin;
        this.playerManager = plugin.getPlayerManagerService();
        this.costService = plugin.getRankupCostService();
        this.messageService = plugin.getMessageService();
        this.rewardService = plugin.getRankupRewardService();

        String currencyId = plugin.getConfigManager().getMainConfig().getString("rankup-settings.currency-settings.currency-type");
        this.economyService = plugin.getEconomyService(currencyId);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }

        if (economyService == null) {
            String currencyId = plugin.getConfigManager().getMainConfig().getString("rankup-settings.currency-settings.currency-type", "N/A");
            messageService.sendMessage(player, "error-economy-not-found", "currency", currencyId);
            return true;
        }

        handleRankup(player);
        return true;
    }

    private void handleRankup(Player player) {
        long maxRank = plugin.getConfigManager().getMainConfig().getLong("rankup-settings.limit", 50);

        try {
            PlayerRankData data = playerManager.getData(player.getUniqueId());
            if (data == null) {
                messageService.sendMessage(player, "error-data-not-loaded");
                return;
            }

            if (data.getRank() >= maxRank) {
                messageService.sendMessage(player, "rankup-fail-max-rank");
                return;
            }

            BigDecimal cost = costService.getCost(data.getRank(), data.getPrestige());
            String formattedCost = NumberFormat.getCurrencyInstance().format(cost) + " " + economyService.getCurrencyId();

            economyService.has(player, cost).thenAccept(hasFunds -> {
                if (!hasFunds) {
                    messageService.sendMessage(player, "rankup-fail-money", "cost", formattedCost);
                    return;
                }

                economyService.withdraw(player, cost).thenAccept(wasSuccessful -> {
                    if (wasSuccessful) {
                        data.incrementRank();
                        long newRank = data.getRank();

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String newRankStr = String.valueOf(newRank);
                            messageService.sendMessage(player, "rankup-success", "new_rank", newRankStr);
                            ConfigurationSection titleConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("rankup-settings.display-settings.rankup-title");
                            messageService.sendTitle(player, titleConfig, "new_rank", newRankStr);


                            List<String> rewards = rewardService.collectRewards(player, newRank);
                            new RewardDispatcher(rewards).runTaskTimer(plugin, 0L, 1L);
                        });
                    } else {
                        messageService.sendMessage(player, "error-economy-withdraw-fail");
                    }
                });
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "An error occurred during the rankup transaction for " + player.getName(), ex);
                messageService.sendMessage(player, "error-generic");
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "A synchronous error occurred during the rankup process for " + player.getName(), e);
            messageService.sendMessage(player, "error-generic");
        }
    }
}