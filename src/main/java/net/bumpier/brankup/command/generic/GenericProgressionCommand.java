package net.bumpier.brankup.command.generic;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.*;
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

public class GenericProgressionCommand implements CommandExecutor {

    private final bRankup plugin;
    private final AdventureMessageService messageService;
    private final ProgressionType progressionType;

    public GenericProgressionCommand(bRankup plugin, ProgressionType progressionType) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.progressionType = progressionType;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }

        IEconomyService economyService = plugin.getEconomyService(progressionType.getCurrencyType());
        if (economyService == null) {
            messageService.sendMessage(player, "error-economy-not-found", "currency", progressionType.getCurrencyType());
            return true;
        }

        CompletableFuture.runAsync(() -> handleProgression(player, economyService));
        return true;
    }

    private void handleProgression(Player player, IEconomyService economyService) {
        PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
        if (data == null) {
            messageService.sendMessage(player, "error-data-not-loaded");
            return;
        }

        if (!plugin.getProgressionChainManager().canProgress(progressionType.getId(), data.getAllProgressionLevels())) {
            ProgressionType requiredType = plugin.getProgressionChainManager().getProgressionType(progressionType.getFollows());
            if (requiredType != null) {
                messageService.sendMessage(player, "progression-fail-requirements",
                        "required_type", requiredType.getDisplayName());
            }
            return;
        }

        long currentLevel = data.getProgressionLevel(progressionType.getId());
        if (currentLevel >= progressionType.getLimit()) {
            messageService.sendMessage(player, "progression-fail-max-level", "type", progressionType.getDisplayName());
            return;
        }

        ProgressionCostService costService = plugin.getCostServices().get(progressionType.getId());
        BigDecimal cost = costService.getCost(currentLevel, data.getProgressionLevel("prestige"));

        economyService.has(player, cost).thenAccept(hasFunds -> {
            if (!hasFunds) {
                String formattedCost = NumberFormat.getCurrencyInstance().format(cost) + " " + economyService.getCurrencyId();
                messageService.sendMessage(player, "progression-fail-money", "cost", formattedCost);
                return;
            }

            economyService.withdraw(player, cost).thenAccept(wasSuccessful -> {
                if (!wasSuccessful) {
                    messageService.sendMessage(player, "error-economy-withdraw-fail");
                    return;
                }

                data.incrementProgressionLevel(progressionType.getId());
                handleResets(player, data);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ProgressionRewardService rewardService = plugin.getRewardServices().get(progressionType.getId());
                    List<String> rewards = rewardService.collectRewards(player, data.getProgressionLevel(progressionType.getId()));
                    new RewardDispatcher(rewards).runTaskTimer(plugin, 0L, 1L);

                    String newLevel = String.valueOf(data.getProgressionLevel(progressionType.getId()));
                    messageService.sendMessage(player, "progression-success", "type", progressionType.getDisplayName(), "new_level", newLevel);

                    ConfigurationSection titleConfig = progressionType.getConfig().getConfigurationSection("display-settings." + progressionType.getId() + "-title");
                    messageService.sendTitle(player, titleConfig, "new_level", newLevel);
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during progression for " + player.getName(), ex);
            messageService.sendMessage(player, "error-generic");
            return null;
        });
    }

    private void handleResets(Player player, PlayerRankData data) {
        if (progressionType.shouldResetPrevious()) {
            String previousTypeId = progressionType.getFollows();
            if (previousTypeId != null) {
                data.setProgressionLevel(previousTypeId, 0);
            }
        }
        for (String currencyKey : progressionType.getResetCurrencies().keySet()) {
            if (progressionType.getResetCurrencies().get(currencyKey)) {
                IEconomyService currencyToReset = plugin.getEconomyService(currencyKey);
                if (currencyToReset != null) {
                    currencyToReset.set(player, BigDecimal.ZERO);
                }
            }
        }
    }
}