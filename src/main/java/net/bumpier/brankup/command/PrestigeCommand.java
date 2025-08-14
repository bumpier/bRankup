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
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PrestigeCommand implements CommandExecutor {

    private final bRankup plugin;
    private final PlayerManagerService playerManager;
    private final AdventureMessageService messageService;
    private final ProgressionCostService prestigeCostService;
    private final ProgressionRewardService prestigeRewardService;
    private final IEconomyService prestigeEconomyService;

    public PrestigeCommand(bRankup plugin) {
        this.plugin = plugin;
        this.playerManager = plugin.getPlayerManagerService();
        this.messageService = plugin.getMessageService();
        this.prestigeCostService = plugin.getPrestigeCostService();
        this.prestigeRewardService = plugin.getPrestigeRewardService();
        String currencyId = plugin.getConfigManager().getMainConfig().getString("prestige-settings.currency-settings.currency-type");
        this.prestigeEconomyService = plugin.getEconomyService(currencyId);
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }
        if (prestigeEconomyService == null) {
            String currencyId = plugin.getConfigManager().getMainConfig().getString("prestige-settings.currency-settings.currency-type", "N/A");
            messageService.sendMessage(player, "error-economy-not-found", "currency", currencyId);
            return true;
        }
        CompletableFuture.runAsync(() -> handlePrestige(player));
        return true;
    }

    private void handlePrestige(Player player) {
        ConfigurationSection prestigeConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("prestige-settings");
        if (prestigeConfig == null) {
            plugin.getLogger().severe("Prestige settings are missing from config.yml!");
            messageService.sendMessage(player, "error-generic");
            return;
        }
        long maxRankRequired = plugin.getConfigManager().getMainConfig().getLong("rankup-settings.limit", 50);
        long maxPrestige = prestigeConfig.getLong("limit", 50);

        try {
            PlayerRankData data = playerManager.getData(player.getUniqueId());
            if (data == null) {
                messageService.sendMessage(player, "error-data-not-loaded");
                return;
            }
            if (prestigeConfig.getBoolean("requirements.requires-max-level", true) && data.getRank() < maxRankRequired) {
                messageService.sendMessage(player, "prestige-fail-rank", "max_rank", String.valueOf(maxRankRequired));
                return;
            }
            if (data.getPrestige() >= maxPrestige) {
                messageService.sendMessage(player, "prestige-fail-max-prestige");
                return;
            }

            // ** THIS LINE IS THE FIX **
            // We now pass '0' as the second argument for prestige level, as cost scaling does not apply here.
            BigDecimal cost = prestigeCostService.getCost(data.getPrestige(), 0);

            prestigeEconomyService.has(player, cost).thenAccept(hasFunds -> {
                if (!hasFunds) {
                    String formattedCost = NumberFormat.getCurrencyInstance().format(cost) + " " + prestigeEconomyService.getCurrencyId();
                    messageService.sendMessage(player, "prestige-fail-money", "cost", formattedCost);
                    return;
                }
                prestigeEconomyService.withdraw(player, cost).thenAccept(wasSuccessful -> {
                    if (!wasSuccessful) {
                        messageService.sendMessage(player, "error-economy-withdraw-fail");
                        return;
                    }
                    data.incrementPrestige();
                    if (prestigeConfig.getBoolean("reset-settings.reset-rankup", true)) {
                        data.setRank(0);
                    }
                    ConfigurationSection currencyResetConfig = prestigeConfig.getConfigurationSection("reset-settings.reset-currencies");
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
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        List<String> rewards = prestigeRewardService.collectRewards(player, data.getPrestige());
                        new RewardDispatcher(rewards).runTaskTimer(plugin, 0L, 1L);
                        messageService.sendMessage(player, "prestige-success", "new_prestige", String.valueOf(data.getPrestige()));
                        ConfigurationSection titleConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("prestige-settings.display-settings.prestige-title");
                        messageService.sendTitle(player, titleConfig, "new_prestige", String.valueOf(data.getPrestige()));
                    });
                });
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "An error occurred during the prestige transaction for " + player.getName(), ex);
                messageService.sendMessage(player, "error-generic");
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "A synchronous error occurred during the prestige process for " + player.getName(), e);
            messageService.sendMessage(player, "error-generic");
        }
    }
}