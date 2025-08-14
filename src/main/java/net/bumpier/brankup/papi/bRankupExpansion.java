package net.bumpier.brankup.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.ProgressionCostService;
import net.bumpier.brankup.progression.ProgressionType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class bRankupExpansion extends PlaceholderExpansion {

    private final bRankup plugin;

    public bRankupExpansion(bRankup plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "brankup";
    }

    @Override
    public @NotNull String getAuthor() {
        return "bumpier.dev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
        if (data == null) return "Loading...";

        // Dynamically parse the placeholder identifier
        String[] parts = identifier.split("_", 2);
        if (parts.length < 2) return null;

        String progressionId = parts[0];
        String requestedInfo = parts[1];

        ProgressionType type = plugin.getProgressionChainManager().getProgressionType(progressionId);
        if (type == null) {
            return null; // Not a valid progression type, not our placeholder
        }

        switch (requestedInfo) {
            case "level":
                return String.valueOf(data.getProgressionLevel(progressionId));
            case "display":
                return parseDisplay(player, type);
            case "level_next":
                long currentLevel = data.getProgressionLevel(progressionId);
                if (currentLevel >= type.getLimit()) {
                    return colorize(type.getMaxLevelDisplay());
                } else {
                    return String.valueOf(currentLevel + 1);
                }
            case "next_display":
                return getNextDisplay(data.getProgressionLevel(progressionId), type);
            case "cost":
            case "cost_formatted":
                return getCost(data, type);
            case "percent":
                // Special handling for prestige percent to be rank-based
                if ("prestige".equals(progressionId)) {
                    return getPrestigeRankPercent(data);
                }
                return getEconomyPercent(player, data, type);
            case "progress_bar":
                // Special handling for prestige progress bar
                if ("prestige".equals(progressionId)) {
                    return buildProgressBar(type, getPrestigeRankPercent(data));
                }
                return buildProgressBar(type, getEconomyPercent(player, data, type));
            default:
                return null;
        }
    }

    private String colorize(String message) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(MiniMessage.miniMessage().deserialize(message));
    }

    private String parseDisplay(Player player, ProgressionType type) {
        String format = type.getProgressionDisplay();
        return colorize(PlaceholderAPI.setPlaceholders(player, format));
    }

    private String getNextDisplay(long currentLevel, ProgressionType type) {
        if (currentLevel >= type.getLimit()) {
            return colorize(type.getMaxLevelDisplay());
        } else {
            String format = type.getProgressionDisplay();
            String placeholderToReplace = "%brankup_" + type.getId() + "_level%";
            format = format.replace(placeholderToReplace, String.valueOf(currentLevel + 1));
            return colorize(format);
        }
    }

    private String getCost(PlayerRankData data, ProgressionType type) {
        ProgressionCostService costService = plugin.getCostServices().get(type.getId());
        if (costService == null) return "N/A";

        long prestigeLevel = data.getProgressionLevel("prestige");
        BigDecimal cost = costService.getCost(data.getProgressionLevel(type.getId()), prestigeLevel);
        return String.format("%,.0f", cost);
    }

    private String getEconomyPercent(Player player, PlayerRankData data, ProgressionType type) {
        IEconomyService economyService = plugin.getEconomyService(type.getCurrencyType());
        ProgressionCostService costService = plugin.getCostServices().get(type.getId());
        if (economyService == null || costService == null) return "0";

        BigDecimal balance = economyService.getBalance(player).join();
        long prestigeLevel = data.getProgressionLevel("prestige");
        BigDecimal cost = costService.getCost(data.getProgressionLevel(type.getId()), prestigeLevel);

        if (cost.compareTo(BigDecimal.ZERO) <= 0) return "100";
        if (balance.compareTo(cost) >= 0) return "100";

        BigDecimal percent = balance.divide(cost, 2, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(100));
        return String.valueOf(percent.intValue());
    }

    private String getPrestigeRankPercent(PlayerRankData data) {
        ProgressionType rankupType = plugin.getProgressionChainManager().getProgressionType("rankup");
        if (rankupType == null) return "0";
        long maxRank = rankupType.getLimit();
        if (maxRank <= 0) maxRank = 1;
        double percent = ((double) data.getProgressionLevel("rankup") / maxRank) * 100.0;
        if (percent > 100.0) percent = 100.0;
        return String.valueOf((int) percent);
    }

    private String buildProgressBar(ProgressionType type, String percentStr) {
        if (type == null) return "";
        ConfigurationSection barConfig = type.getConfig().getConfigurationSection("display-settings.progress-bar");
        if (barConfig == null || !barConfig.getBoolean("enabled", true)) {
            return "";
        }
        String barChar = barConfig.getString("character", "■");
        int totalChars = barConfig.getInt("amount", 20);
        String colorAchieved = barConfig.getString("color-achieved", "&a");
        String colorCurrent = barConfig.getString("color-current", "&e");
        String colorNeeded = barConfig.getString("color-needed", "&7");
        double percent = 0;
        try {
            percent = Double.parseDouble(percentStr);
        } catch (NumberFormatException ignored) {}
        int achievedChars = (int) Math.floor((percent / 100.0) * totalChars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalChars; i++) {
            if (i < achievedChars) {
                sb.append(colorAchieved).append(barChar);
            } else if (i == achievedChars && percent < 100) {
                sb.append(colorCurrent).append(barChar);
            } else {
                sb.append(colorNeeded).append(barChar);
            }
        }
        return colorize(sb.toString());
    }
}