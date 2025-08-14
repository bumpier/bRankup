package net.bumpier.brankup.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.economy.IEconomyService;
import net.bumpier.brankup.progression.ProgressionCostService;
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

        ConfigurationSection rankupConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("rankup-settings");
        ConfigurationSection prestigeConfig = plugin.getConfigManager().getMainConfig().getConfigurationSection("prestige-settings");

        switch (identifier) {
            // Rank Placeholders
            case "rank_level":
                return String.valueOf(data.getRank());
            case "rank_display":
                return parseDisplay(player, rankupConfig, "rank");
            case "rank_level_next":
                long maxRank = rankupConfig != null ? rankupConfig.getLong("limit", 50) : 50;
                if (data.getRank() >= maxRank) {
                    String maxDisplay = rankupConfig.getString("display-settings.max-level-display", "&cMax");
                    return LegacyComponentSerializer.legacyAmpersand().serialize(MiniMessage.miniMessage().deserialize(maxDisplay));
                } else {
                    return String.valueOf(data.getRank() + 1);
                }
            case "rank_cost", "rank_cost_formatted":
                return getCost(data, "rankup");
            case "rank_percent":
                return getRankupEconomyPercent(player, data, rankupConfig);
            case "rank_progress_bar":
                return buildProgressBar(rankupConfig, getRankupEconomyPercent(player, data, rankupConfig));

            // Prestige Placeholders
            case "prestige_level":
                return String.valueOf(data.getPrestige());
            case "prestige_display":
                return parseDisplay(player, prestigeConfig, "prestige");
            case "prestige_level_next":
                long maxPrestige = prestigeConfig != null ? prestigeConfig.getLong("limit", 50) : 50;
                if (data.getPrestige() >= maxPrestige) {
                    String maxDisplay = prestigeConfig.getString("display-settings.max-level-display", "&cMax");
                    return LegacyComponentSerializer.legacyAmpersand().serialize(MiniMessage.miniMessage().deserialize(maxDisplay));
                } else {
                    return String.valueOf(data.getPrestige() + 1);
                }
            case "prestige_cost", "prestige_cost_formatted":
                return getCost(data, "prestige");
            case "prestige_percent": // Refactored to use rank progress
                return getRankProgressPercent(data, rankupConfig);
            case "prestige_progress_bar":
                return buildProgressBar(prestigeConfig, getRankProgressPercent(data, rankupConfig));

            default:
                return null;
        }
    }

    private String getRankProgressPercent(PlayerRankData data, ConfigurationSection rankupConfig) {
        if (rankupConfig == null) return "0";

        long maxRank = rankupConfig.getLong("limit", 1);
        if (maxRank <= 0) maxRank = 1;

        double percent = ((double) data.getRank() / maxRank) * 100.0;
        if (percent > 100.0) percent = 100.0;

        return String.valueOf((int) percent);
    }

    private String getRankupEconomyPercent(Player player, PlayerRankData data, ConfigurationSection rankupConfig) {
        if (rankupConfig == null) return "0";

        String currencyId = rankupConfig.getString("currency-settings.currency-type");
        IEconomyService economyService = plugin.getEconomyService(currencyId);
        ProgressionCostService costService = plugin.getRankupCostService();

        if (economyService == null || costService == null) return "0";

        BigDecimal balance = economyService.getBalance(player).join();
        BigDecimal cost = costService.getCost(data.getRank(), data.getPrestige());

        if (cost.compareTo(BigDecimal.ZERO) <= 0) return "100";
        if (balance.compareTo(cost) >= 0) return "100";

        BigDecimal percent = balance.divide(cost, 2, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(100));
        return String.valueOf(percent.intValue());
    }

    // ... (parseDisplay, getCost, and buildProgressBar methods are unchanged, I will include them for completeness)
    private String parseDisplay(Player player, ConfigurationSection config, String type) {
        if (config == null) return "";
        String format = config.getString("display-settings." + type + "-display", "&cInvalid Format");
        String formattedString = PlaceholderAPI.setPlaceholders(player, format);
        return LegacyComponentSerializer.legacyAmpersand().serialize(MiniMessage.miniMessage().deserialize(formattedString));
    }

    private String getCost(PlayerRankData data, String type) {
        ProgressionCostService costService;
        long currentLevel;
        long prestigeLevel = 0;

        if ("rankup".equals(type)) {
            costService = plugin.getRankupCostService();
            currentLevel = data.getRank();
            prestigeLevel = data.getPrestige();
        } else {
            costService = plugin.getPrestigeCostService();
            currentLevel = data.getPrestige();
        }

        if (costService == null) return "N/A";

        BigDecimal cost = costService.getCost(currentLevel, prestigeLevel);
        return String.format("%,.0f", cost);
    }

    private String buildProgressBar(ConfigurationSection config, String percentStr) {
        if (config == null) return "";
        ConfigurationSection barConfig = config.getConfigurationSection("display-settings.progress-bar");
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

        return LegacyComponentSerializer.legacyAmpersand().serialize(MiniMessage.miniMessage().deserialize(sb.toString()));
    }
}