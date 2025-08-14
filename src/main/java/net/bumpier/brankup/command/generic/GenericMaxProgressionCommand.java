package net.bumpier.brankup.command.generic;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.*;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GenericMaxProgressionCommand implements CommandExecutor {

    private final bRankup plugin;
    private final AdventureMessageService messageService;
    private final ProgressionType progressionType;

    public GenericMaxProgressionCommand(bRankup plugin, ProgressionType progressionType) {
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

        if (!player.hasPermission(progressionType.getMaxProgressionPermission())) {
            messageService.sendMessage(player, "error-no-permission");
            return true;
        }

        IEconomyService economyService = plugin.getEconomyService(progressionType.getCurrencyType());
        if (economyService == null) {
            messageService.sendMessage(player, "error-economy-not-found", "currency", progressionType.getCurrencyType());
            return true;
        }

        CompletableFuture.runAsync(() -> handleMaxProgression(player, economyService));
        return true;
    }

    private void handleMaxProgression(Player player, IEconomyService economyService) {
        PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
        if (data == null) {
            messageService.sendMessage(player, "error-data-not-loaded");
            return;
        }

        if (!plugin.getProgressionChainManager().canProgress(progressionType.getId(), data.getAllProgressionLevels())) {
            ProgressionType requiredType = plugin.getProgressionChainManager().getProgressionType(progressionType.getFollows());
            if (requiredType != null) {
                messageService.sendMessage(player, "progression-fail-requirements", "required_type", requiredType.getDisplayName());
            }
            return;
        }

        long startingLevel = data.getProgressionLevel(progressionType.getId());
        if (startingLevel >= progressionType.getLimit()) {
            messageService.sendMessage(player, "progression-fail-max-level", "type", progressionType.getDisplayName());
            return;
        }

        BigDecimal playerBalance = economyService.getBalance(player).join();
        ProgressionCostService costService = plugin.getCostServices().get(progressionType.getId());

        long levelsToPurchase = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long currentSimulatedLevel = startingLevel;

        while (currentSimulatedLevel < progressionType.getLimit()) {
            BigDecimal costForNext = costService.getCost(currentSimulatedLevel, data.getProgressionLevel("prestige"));
            if (playerBalance.compareTo(costForNext) >= 0) {
                playerBalance = playerBalance.subtract(costForNext);
                totalCost = totalCost.add(costForNext);
                levelsToPurchase++;
                currentSimulatedLevel++;
            } else {
                break;
            }
        }

        if (levelsToPurchase == 0) {
            messageService.sendMessage(player, "max-progression-fail-cant-afford-next");
            return;
        }

        final long finalLevelsPurchased = levelsToPurchase;
        final long finalNewLevel = startingLevel + finalLevelsPurchased;
        final BigDecimal finalTotalCost = totalCost;

        economyService.withdraw(player, finalTotalCost).thenAccept(wasSuccessful -> {
            if (wasSuccessful) {
                data.setProgressionLevel(progressionType.getId(), finalNewLevel);
                handleResets(player, data);

                List<String> allRewardCommands = new ArrayList<>();
                ProgressionRewardService rewardService = plugin.getRewardServices().get(progressionType.getId());
                for (long i = startingLevel + 1; i <= finalNewLevel; i++) {
                    allRewardCommands.addAll(rewardService.collectRewards(player, i));
                }
                new RewardDispatcher(allRewardCommands).runTaskTimer(plugin, 0L, 1L);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String formattedCost = NumberFormat.getCurrencyInstance().format(finalTotalCost);
                    messageService.sendMessage(player, "max-progression-success",
                            "type", progressionType.getDisplayName().toLowerCase(),
                            "levels_purchased", String.valueOf(finalLevelsPurchased),
                            "new_level", String.valueOf(finalNewLevel),
                            "total_cost", formattedCost
                    );
                });
            } else {
                messageService.sendMessage(player, "error-economy-withdraw-fail");
            }
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