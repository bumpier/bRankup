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
    private final String summaryHeader;
    private final String summaryPerTypeFormat;
    private final String summaryFooter;

    public AutoProgressionTask(bRankup plugin) {
        this.plugin = plugin;
        ConfigurationSection summaryConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("auto-progression-summary");
        if (summaryConfig != null) {
            this.summaryEnabled = summaryConfig.getBoolean("enabled", true);
            this.summaryIntervalMillis = summaryConfig.getLong("interval-seconds", 120) * 1000;
            this.summaryHeader = summaryConfig.getString("header", "");
            this.summaryPerTypeFormat = summaryConfig.getString("per-type-format", "");
            this.summaryFooter = summaryConfig.getString("footer", "");
        } else {
            this.summaryEnabled = false;
            this.summaryIntervalMillis = 120_000;
            this.summaryHeader = "";
            this.summaryPerTypeFormat = "";
            this.summaryFooter = "";
        }
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (summaryEnabled && currentTime > nextSummaryTimes.getOrDefault(uuid, 0L)) {
                sendSummary(player);
                nextSummaryTimes.put(uuid, currentTime + summaryIntervalMillis);
            }

            if (currentTime < playerCooldowns.getOrDefault(uuid, 0L)) {
                continue;
            }

            PlayerRankData data = plugin.getPlayerManagerService().getData(uuid);
            if (data == null) continue;

            for (String typeId : plugin.getProgressionChainManager().getProgressionOrder()) {
                if (data.isAutoProgressionEnabled(typeId)) {
                    ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
                    if (type != null) {
                        processAutoProgression(player, data, type);
                        break;
                    }
                }
            }
        }
    }

    private void sendSummary(Player player) {
        Map<String, Integer> counts = progressionCounts.remove(player.getUniqueId());
        if (counts == null || counts.isEmpty()) return;

        long intervalMinutes = summaryIntervalMillis / 60_000;
        String intervalString = intervalMinutes + (intervalMinutes == 1 ? " minute" : " minutes");

        if (!summaryHeader.isBlank()) {
            plugin.getMessageService().sendParsedMessage(player, summaryHeader, "interval", intervalString);
        }

        if (!summaryPerTypeFormat.isBlank()) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String typeId = entry.getKey();
                Integer count = entry.getValue();
                ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
                if (type != null && count > 0) {
                    plugin.getMessageService().sendParsedMessage(player, summaryPerTypeFormat,
                            "type_display_name", type.getDisplayName(),
                            "type_count", String.valueOf(count)
                    );
                }
            }
        }

        if (!summaryFooter.isBlank()) {
            plugin.getMessageService().sendParsedMessage(player, summaryFooter);
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