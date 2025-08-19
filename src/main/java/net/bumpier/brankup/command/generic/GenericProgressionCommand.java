package net.bumpier.brankup.command.generic;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.*;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GenericProgressionCommand implements CommandExecutor, TabCompleter {

    private final bRankup plugin;
    private final AdventureMessageService messageService;
    private final ProgressionType progressionType;
    private final boolean debugEnabled;

    public GenericProgressionCommand(bRankup plugin, ProgressionType progressionType) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.progressionType = progressionType;
        this.debugEnabled = plugin.getConfigManager().getMainConfig().getBoolean("debug.command-execution", false);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }

        if (debugEnabled) plugin.getLogger().info("[DEBUG] Command /" + label + " " + String.join(" ", args) + " initiated by " + player.getName());

        plugin.getPlayerManagerService().getOrLoadData(player.getUniqueId()).thenAccept(data -> {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] Player data successfully loaded for " + player.getName() + ".");
            String subCommand = (args.length > 0) ? args[0].toLowerCase() : "single";
            if (debugEnabled) plugin.getLogger().info("[DEBUG] Determined subcommand: " + subCommand);

            switch (subCommand) {
                case "max" -> handleMaxProgression(player, data);
                case "auto" -> handleAutoToggle(player, data);
                case "single" -> handleSingleProgression(player, data);
                default -> handleSingleProgression(player, data);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[DEBUG] Command execution failed for " + player.getName() + " due to a data loading error.", ex);
            messageService.sendMessage(player, "error-generic");
            return null;
        });

        return true;
    }

    private void handleSingleProgression(Player player, PlayerRankData data) {
        if (debugEnabled) plugin.getLogger().info("[DEBUG] Entering handleSingleProgression for " + player.getName());

        if (!player.hasPermission("brankup." + progressionType.getId() + ".base")) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " lacks permission: " + "brankup." + progressionType.getId() + ".base");
            messageService.sendMessage(player, "error-no-permission");
            return;
        }

        IEconomyService economyService = plugin.getEconomyService(progressionType.getCurrencyType());
        if (economyService == null) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Economy service not found for currency type: " + progressionType.getCurrencyType());
            messageService.sendMessage(player, "error-economy-not-found", "currency", progressionType.getCurrencyType());
            return;
        }

        if (!plugin.getProgressionChainManager().canProgress(progressionType.getId(), data.getAllProgressionLevels())) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " does not meet progression chain requirements.");
            ProgressionType requiredType = plugin.getProgressionChainManager().getProgressionType(progressionType.getFollows());
            if (requiredType != null) {
                messageService.sendMessage(player, "progression-fail-requirements", "required_type", requiredType.getDisplayName());
            }
            return;
        }

        long currentLevel = data.getProgressionLevel(progressionType.getId());
        if (currentLevel >= progressionType.getLimit()) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " is at the max level (" + currentLevel + "/" + progressionType.getLimit() + ").");
            messageService.sendMessage(player, "progression-fail-max-level", "type", progressionType.getDisplayName());
            return;
        }

        if (debugEnabled) plugin.getLogger().info("[DEBUG] All pre-checks passed for " + player.getName() + ". Current level: " + currentLevel);

        ProgressionCostService costService = plugin.getCostServices().get(progressionType.getId());
        BigDecimal cost = costService.getCost(currentLevel, data);
        if (debugEnabled) plugin.getLogger().info("[DEBUG] Calculated cost for next level: " + cost.toPlainString());

        economyService.has(player, cost).thenAcceptAsync(hasFunds -> {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] Player " + player.getName() + " has sufficient funds: " + hasFunds);
            if (!hasFunds) {
                String formattedCost = NumberFormat.getNumberInstance(Locale.US).format(cost.toBigInteger());
                messageService.sendMessage(player, "progression-fail-money", "cost", formattedCost + " " + economyService.getCurrencyId());
                return;
            }

            economyService.withdraw(player, cost).thenAcceptAsync(wasSuccessful -> {
                if (debugEnabled) plugin.getLogger().info("[DEBUG] Fund withdrawal for " + player.getName() + " was successful: " + wasSuccessful);
                if (!wasSuccessful) {
                    messageService.sendMessage(player, "error-economy-withdraw-fail");
                    return;
                }

                data.incrementProgressionLevel(progressionType.getId());
                handleResets(player, data);

                if (debugEnabled) plugin.getLogger().info("[DEBUG] Scheduling success message and rewards for " + player.getName());

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ProgressionRewardService rewardService = plugin.getRewardServices().get(progressionType.getId());
                    if (rewardService != null) {
                        rewardService.dispatchRewards(player, data.getProgressionLevel(progressionType.getId()));
                    }

                    String newLevel = String.valueOf(data.getProgressionLevel(progressionType.getId()));
                    messageService.sendMessage(player, "progression-success", "type", progressionType.getDisplayName(), "new_level", newLevel);

                    ConfigurationSection titleConfig = progressionType.getConfig().getConfigurationSection("display-settings." + progressionType.getId() + "-title");
                    // Use new placeholder format <new_[RANKLADDER]>
                    messageService.sendTitle(player, titleConfig, "new_" + progressionType.getId(), newLevel);
                    if (debugEnabled) plugin.getLogger().info("[DEBUG] SUCCESS: Progression complete for " + player.getName());
                });
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "[DEBUG] Error withdrawing funds for " + player.getName(), ex);
                messageService.sendMessage(player, "error-generic");
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[DEBUG] Error checking balance for " + player.getName(), ex);
            messageService.sendMessage(player, "error-generic");
            return null;
        });
    }

    private void handleMaxProgression(Player player, PlayerRankData data) {
        if (debugEnabled) plugin.getLogger().info("[DEBUG] Entering handleMaxProgression for " + player.getName());

        if (!player.hasPermission(progressionType.getMaxProgressionPermission())) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " lacks permission: " + progressionType.getMaxProgressionPermission());
            messageService.sendMessage(player, "error-no-permission");
            return;
        }

        IEconomyService economyService = plugin.getEconomyService(progressionType.getCurrencyType());
        if (economyService == null) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Economy service not found for currency type: " + progressionType.getCurrencyType());
            messageService.sendMessage(player, "error-economy-not-found", "currency", progressionType.getCurrencyType());
            return;
        }

        if (!plugin.getProgressionChainManager().canProgress(progressionType.getId(), data.getAllProgressionLevels())) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " does not meet progression chain requirements for max.");
            ProgressionType requiredType = plugin.getProgressionChainManager().getProgressionType(progressionType.getFollows());
            if (requiredType != null) {
                messageService.sendMessage(player, "progression-fail-requirements", "required_type", requiredType.getDisplayName());
            }
            return;
        }

        long startingLevel = data.getProgressionLevel(progressionType.getId());
        if (startingLevel >= progressionType.getLimit()) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " is at the max level (" + startingLevel + "/" + progressionType.getLimit() + ").");
            messageService.sendMessage(player, "progression-fail-max-level", "type", progressionType.getDisplayName());
            return;
        }

        if (debugEnabled) plugin.getLogger().info("[DEBUG] All pre-checks passed for max progression. Current level: " + startingLevel);

        economyService.getBalance(player).thenAcceptAsync(playerBalance -> {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] Fetched balance for max calc: " + playerBalance.toPlainString());

            ProgressionCostService costService = plugin.getCostServices().get(progressionType.getId());
            ProgressionRewardService rewardService = plugin.getRewardServices().get(progressionType.getId());

            long levelsToPurchase = 0;
            BigDecimal totalCost = BigDecimal.ZERO;
            long currentSimulatedLevel = startingLevel;
            List<String> allRewardCommands = new ArrayList<>();

            while (currentSimulatedLevel < progressionType.getLimit()) {
                BigDecimal costForNext = costService.getCost(currentSimulatedLevel, data);
                if (playerBalance.compareTo(totalCost.add(costForNext)) >= 0) {
                    totalCost = totalCost.add(costForNext);
                    levelsToPurchase++;
                    currentSimulatedLevel++;
                    if (rewardService != null) {
                        allRewardCommands.addAll(rewardService.collectRewards(player, currentSimulatedLevel));
                    }
                } else {
                    break;
                }
            }

            if (debugEnabled) plugin.getLogger().info("[DEBUG] Max calc finished. Levels to purchase: " + levelsToPurchase + ". Total cost: " + totalCost.toPlainString());

            if (levelsToPurchase == 0) {
                messageService.sendMessage(player, "max-progression-fail-cant-afford-next");
                return;
            }

            final long finalLevelsPurchased = levelsToPurchase;
            final long finalNewLevel = startingLevel + finalLevelsPurchased;
            final BigDecimal finalTotalCost = totalCost;

            economyService.withdraw(player, finalTotalCost).thenAcceptAsync(wasSuccessful -> {
                if (debugEnabled) plugin.getLogger().info("[DEBUG] Fund withdrawal for max progression successful: " + wasSuccessful);
                if (wasSuccessful) {
                    data.setProgressionLevel(progressionType.getId(), finalNewLevel);
                    handleResets(player, data);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!allRewardCommands.isEmpty()) {
                            new RewardDispatcher(allRewardCommands).runTaskTimer(plugin, 0L, 2L);
                        }
                        String formattedCost = NumberFormat.getNumberInstance(Locale.US).format(finalTotalCost.toBigInteger());
                        messageService.sendMessage(player, "max-progression-success",
                                "type", progressionType.getDisplayName(),
                                "levels_purchased", String.valueOf(finalLevelsPurchased),
                                "new_level", String.valueOf(finalNewLevel),
                                "total_cost", formattedCost
                        );
                        if (debugEnabled) plugin.getLogger().info("[DEBUG] SUCCESS: Max progression complete for " + player.getName());
                    });
                } else {
                    messageService.sendMessage(player, "error-economy-withdraw-fail");
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[DEBUG] Error during max progression for " + player.getName(), ex);
            messageService.sendMessage(player, "error-generic");
            return null;
        });
    }

    private void handleAutoToggle(Player player, PlayerRankData data) {
        if (debugEnabled) plugin.getLogger().info("[DEBUG] Entering handleAutoToggle for " + player.getName());

        if (!player.hasPermission(progressionType.getAutoProgressionPermission())) {
            if (debugEnabled) plugin.getLogger().info("[DEBUG] FAILED: Player " + player.getName() + " lacks permission: " + progressionType.getAutoProgressionPermission());
            messageService.sendMessage(player, "error-no-permission");
            return;
        }

        boolean newState = !data.isAutoProgressionEnabled(progressionType.getId());
        data.setAutoProgressionEnabled(progressionType.getId(), newState);

        String displayName = progressionType.getDisplayName();
        messageService.sendMessage(player, newState ? "auto-progression-enabled" : "auto-progression-disabled", "type", displayName);
        if (debugEnabled) plugin.getLogger().info("[DEBUG] Toggled auto-" + progressionType.getId() + " for " + player.getName() + " to: " + newState);
    }

    private void handleResets(Player player, PlayerRankData data) {
        if (progressionType.shouldResetPrevious()) {
            String previousTypeId = progressionType.getFollows();
            if (previousTypeId != null) {
                if (debugEnabled) plugin.getLogger().info("[DEBUG] Resetting previous progression '" + previousTypeId + "' for " + player.getName());
                data.setProgressionLevel(previousTypeId, 0);
            }
        }
        for (Map.Entry<String, Boolean> entry : progressionType.getResetCurrencies().entrySet()) {
            if (entry.getValue()) {
                IEconomyService currencyToReset = plugin.getEconomyService(entry.getKey());
                if (currencyToReset != null) {
                    if (debugEnabled) plugin.getLogger().info("[DEBUG] Resetting currency '" + entry.getKey() + "' for " + player.getName());
                    currencyToReset.set(player, BigDecimal.ZERO);
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(progressionType.getMaxProgressionPermission())) {
                completions.add("max");
            }
            if (sender.hasPermission(progressionType.getAutoProgressionPermission())) {
                completions.add("auto");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return completions;
    }
}
