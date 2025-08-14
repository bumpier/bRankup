package net.bumpier.brankup.task;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AutoProgressionTask extends BukkitRunnable {

    private final bRankup plugin;
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> progressionCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextSummaryTimes = new ConcurrentHashMap<>();
    private final boolean summaryEnabled;
    private final long summaryIntervalMillis;
    private final List<String> summaryMessage;

    public AutoProgressionTask(bRankup plugin) {
        this.plugin = plugin;
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
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (currentTime < playerCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
                continue;
            }

            if (summaryEnabled && currentTime > nextSummaryTimes.getOrDefault(player.getUniqueId(), 0L)) {
                sendSummary(player);
                nextSummaryTimes.put(player.getUniqueId(), currentTime + summaryIntervalMillis);
            }

            PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
            if (data == null) continue;

            // Generic loop through all progression types
            for (ProgressionType type : plugin.getProgressionChainManager().getProgressionOrder()) {
                if (data.isAutoProgressionEnabled(type.getId())) {
                    processAutoProgression(player, data, type);
                    // Process one auto-progression per cycle to respect priority
                    break;
                }
            }
        }
    }

    private void processAutoProgression(Player player, PlayerRankData data, ProgressionType type) {
        if (!type.isAutoProgressionEnabled()) return;

        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (type.getAutoProgressionDelay() * 50));
        CompletableFuture.runAsync(() -> tryAutoProgression(player, data, type));
    }

    private void tryAutoProgression(Player player, PlayerRankData data, ProgressionType type) {
        if (!plugin.getProgressionChainManager().canProgress(type.getId(), data.getAllProgressionLevels())) return;
        if (data.getProgressionLevel(type.getId()) >= type.getLimit()) return;

        ProgressionCostService costService = plugin.getCostServices().get(type.getId());
        IEconomyService economyService = plugin.getEconomyService(type.getCurrencyType());
        ProgressionRewardService rewardService = plugin.getRewardServices().get(type.getId());
        if (costService == null || economyService == null || rewardService == null) return;

        BigDecimal cost = costService.getCost(data.getProgressionLevel(type.getId()), data.getProgressionLevel("prestige"));

        economyService.has(player, cost).thenAccept(hasFunds -> {
            if (hasFunds) {
                economyService.withdraw(player, cost).thenAccept(wasSuccessful -> {
                    if (wasSuccessful) {
                        data.incrementProgressionLevel(type.getId());
                        progressionCounts.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).merge(type.getId(), 1, Integer::sum);
                        handleResets(player, data, type);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            rewardService.dispatchRewards(player, data.getProgressionLevel(type.getId()));
                        });
                    }
                });
            }
        });
    }

    private void sendSummary(Player player) {
        Map<String, Integer> counts = progressionCounts.remove(player.getUniqueId());
        if (counts == null || counts.isEmpty()) return;

        long intervalMinutes = summaryIntervalMillis / 60_000;
        String intervalString = intervalMinutes + (intervalMinutes == 1 ? " minute" : " minutes");

        for (String line : summaryMessage) {
            // A more dynamic summary message is needed here, this is a basic implementation
            plugin.getMessageService().sendParsedMessage(player, line, "interval", intervalString);
        }
    }

    private void handleResets(Player player, PlayerRankData data, ProgressionType type) {
        if (type.shouldResetPrevious()) {
            String previousTypeId = type.getFollows();
            if (previousTypeId != null) {
                data.setProgressionLevel(previousTypeId, 0);
            }
        }
        for (Map.Entry<String, Boolean> entry : type.getResetCurrencies().entrySet()) {
            if (entry.getValue()) {
                IEconomyService currencyToReset = plugin.getEconomyService(entry.getKey());
                if (currencyToReset != null) {
                    currencyToReset.set(player, BigDecimal.ZERO);
                }
            }
        }
    }
}