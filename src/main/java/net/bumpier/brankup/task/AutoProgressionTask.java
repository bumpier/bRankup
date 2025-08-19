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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AutoProgressionTask extends BukkitRunnable {

    private final bRankup plugin;
    private final Map<UUID, Map<String, Integer>> progressionCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextSummaryTimes = new ConcurrentHashMap<>();
    private final boolean summaryEnabled;
    private final long summaryIntervalMillis;
    private final List<String> summaryMessage;
    private final boolean debugEnabled;

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
        this.debugEnabled = plugin.getConfigManager().getMainConfig().getBoolean("debug.command-execution", false);
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getPlayerManagerService().getOrLoadData(player.getUniqueId()).thenAccept(data -> {
                if (data == null) {
                    if (debugEnabled) plugin.getLogger().warning("[DEBUG][AutoTask] Player data was null for " + player.getName());
                    return;
                }

                if (summaryEnabled && currentTime > nextSummaryTimes.getOrDefault(player.getUniqueId(), 0L)) {
                    sendSummary(player);
                    nextSummaryTimes.put(player.getUniqueId(), currentTime + summaryIntervalMillis);
                }

                for (String typeId : plugin.getProgressionChainManager().getProgressionOrder()) {
                    ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
                    if (type == null) continue;

                    if (data.isAutoProgressionEnabled(type.getId())) {
                        tryAutoProgression(player, data, type);
                    }
                }
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "An error occurred in AutoProgressionTask for " + player.getName(), ex);
                return null;
            });
        }
    }

    private void tryAutoProgression(Player player, PlayerRankData data, ProgressionType type) {
        if (!type.isAutoProgressionEnabled()) return;

        IEconomyService economyService = plugin.getEconomyService(type.getCurrencyType());
        ProgressionCostService costService = plugin.getCostServices().get(type.getId());

        if (economyService == null || costService == null) {
            if (debugEnabled) plugin.getLogger().warning("[DEBUG][AutoTask] Economy or Cost service is null for type: " + type.getId());
            return;
        }

        if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] Checking auto-progression for " + player.getName() + " for type '" + type.getId() + "'.");

        while (true) {
            if (!player.isOnline()) {
                if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] Player " + player.getName() + " logged off. Aborting.");
                return;
            }

            if (!plugin.getProgressionChainManager().canProgress(type.getId(), data.getAllProgressionLevels())) {
                if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] EXIT: Player " + player.getName() + " cannot progress in chain '" + type.getId() + "'.");
                break;
            }

            long currentLevel = data.getProgressionLevel(type.getId());
            if (currentLevel >= type.getLimit()) {
                if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] EXIT: Player " + player.getName() + " is at max level for '" + type.getId() + "'.");
                break;
            }

            BigDecimal cost = costService.getCost(currentLevel, data);

            // This is a blocking call, but it's inside an async task, so it's safe.
            // We use .join() to get the result immediately for the next check.
            boolean hasFunds = economyService.has(player, cost).join();

            if (debugEnabled) {
                // To avoid calling getBalance again, we infer it from the 'hasFunds' check.
                plugin.getLogger().info("[DEBUG][AutoTask] Player: " + player.getName() + ", Type: " + type.getId() + ", Level: " + currentLevel + ", Cost: " + cost.toPlainString() + ", Has Funds: " + hasFunds);
            }

            if (!hasFunds) {
                if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] EXIT: Player " + player.getName() + " cannot afford next level.");
                break;
            }

            boolean withdrawSuccess = economyService.withdraw(player, cost).join();
            if (!withdrawSuccess) {
                if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] EXIT: Economy withdrawal failed for " + player.getName() + ".");
                break;
            }

            data.incrementProgressionLevel(type.getId());
            handleResets(player, data, type);
            progressionCounts.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).merge(type.getId(), 1, Integer::sum);

            long newLevel = data.getProgressionLevel(type.getId());
            if (debugEnabled) plugin.getLogger().info("[DEBUG][AutoTask] SUCCESS: Processed level up for " + player.getName() + " to " + type.getDisplayName() + " " + newLevel + ". Looping again.");

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ProgressionRewardService rewardService = plugin.getRewardServices().get(type.getId());
                if (rewardService != null) {
                    rewardService.dispatchRewards(player, newLevel);
                }
            });
        }
    }

    private void sendSummary(Player player) {
        Map<String, Integer> counts = progressionCounts.remove(player.getUniqueId());
        if (counts == null || counts.isEmpty()) return;

        long intervalMinutes = summaryIntervalMillis / 60_000;
        String intervalString = intervalMinutes + (intervalMinutes == 1 ? " minute" : " minutes");

        for (String line : summaryMessage) {
            String processedLine = line.replace("<interval>", intervalString);
            boolean isConditional = false;
            boolean conditionMet = false;

            for (ProgressionType type : plugin.getProgressionChainManager().getAllProgressionTypes()) {
                String typeId = type.getId();
                String conditionalTag = "[if_" + typeId + "]";

                if (processedLine.contains(conditionalTag)) {
                    isConditional = true;
                    int count = counts.getOrDefault(typeId, 0);

                    if (count > 0) {
                        conditionMet = true;
                        String countTag = "<count_" + typeId + ">";
                        processedLine = processedLine.replace(conditionalTag, "").replace(countTag, String.valueOf(count));
                    }
                    break;
                }
            }

            if (!isConditional || conditionMet) {
                plugin.getMessageService().sendParsedMessage(player, processedLine);
            }
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