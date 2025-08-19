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
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class bRankupExpansion extends PlaceholderExpansion {

    private final bRankup plugin;
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;

    public bRankupExpansion(bRankup plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
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
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        PlayerRankData data = plugin.getPlayerManagerService().getDataSynchronously(player.getUniqueId());
        return parsePlaceholder(player, data, identifier);
    }

    private String parsePlaceholder(OfflinePlayer player, PlayerRankData data, String identifier) {
        String[] parts = identifier.split("_", 2);
        if (parts.length < 2) return null;

        String typeId = parts[0];
        String key = parts[1];

        ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
        if (type == null) return null;

        long currentLevel = data.getProgressionLevel(type.getId());
        boolean isMaxLevel = currentLevel >= type.getLimit();

        return switch (key) {
            case "level" -> String.valueOf(currentLevel);
            case "display" -> formatDisplay(player, type.getProgressionDisplay());
            case "level_next" -> isMaxLevel ? legacySerializer.serialize(miniMessage.deserialize(type.getMaxLevelDisplay())) : String.valueOf(currentLevel + 1);
            case "next_display" -> {
                if (isMaxLevel) {
                    yield legacySerializer.serialize(miniMessage.deserialize(type.getMaxLevelDisplay()));
                }
                String displayFormat = type.getProgressionDisplay();
                String tempPlaceholder = "%brankup_" + type.getId() + "_level%";
                String nextLevelDisplay = displayFormat.replace(tempPlaceholder, String.valueOf(currentLevel + 1));
                yield formatDisplay(player, nextLevelDisplay);
            }
            case "cost", "cost_formatted" -> getCost(data, type);
            case "percent" -> getPercent(player, data, type);
            case "progress_bar" -> buildProgressBar(type, getPercent(player, data, type));
            default -> null;
        };
    }

    private String getCost(PlayerRankData data, ProgressionType type) {
        ProgressionCostService costService = plugin.getCostServices().get(type.getId());
        if (costService == null) return "N/A";

        if (data.getProgressionLevel(type.getId()) >= type.getLimit()) {
            return "Maxed";
        }

        // REFACTORED: Pass the entire data object for dynamic cost scaling
        BigDecimal cost = costService.getCost(data.getProgressionLevel(type.getId()), data);
        return NumberFormat.getNumberInstance(Locale.US).format(cost.toBigInteger());
    }

    private String getPercent(OfflinePlayer player, PlayerRankData data, ProgressionType type) {
        if (type.getFollows() != null) {
            ProgressionType prerequisiteType = plugin.getProgressionChainManager().getProgressionType(type.getFollows());
            if (prerequisiteType != null) {
                long prerequisiteLevel = data.getProgressionLevel(prerequisiteType.getId());
                long prerequisiteLimit = prerequisiteType.getLimit();
                if (prerequisiteLimit <= 0) return "100";
                double percent = ((double) prerequisiteLevel / prerequisiteLimit) * 100.0;
                return String.valueOf(Math.min((int) percent, 100));
            }
        }

        if (!player.isOnline()) return "0";
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) return "0";

        IEconomyService economyService = plugin.getEconomyService(type.getCurrencyType());
        ProgressionCostService costService = plugin.getCostServices().get(type.getId());

        if (economyService == null || costService == null || data.getProgressionLevel(type.getId()) >= type.getLimit()) {
            return "100";
        }

        BigDecimal balance = economyService.getBalance(onlinePlayer).join();
        // REFACTORED: Pass the entire data object for dynamic cost scaling
        BigDecimal cost = costService.getCost(data.getProgressionLevel(type.getId()), data);

        if (cost.compareTo(BigDecimal.ZERO) <= 0) return "100";
        if (balance.compareTo(cost) >= 0) return "100";

        BigDecimal percent = balance.divide(cost, 2, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(100));
        return String.valueOf(percent.intValue());
    }

    private String buildProgressBar(ProgressionType type, String percentStr) {
        ConfigurationSection barConfig = type.getConfig().getConfigurationSection("display-settings.progress-bar");
        if (barConfig == null || !barConfig.getBoolean("enabled", true)) {
            return "";
        }

        double percent;
        try {
            percent = Double.parseDouble(percentStr);
        } catch (NumberFormatException ignored) {
            return "";
        }

        String barChar = barConfig.getString("character", "■");
        int totalChars = barConfig.getInt("amount", 10);
        String colorAchieved = barConfig.getString("color-achieved", "&a");
        String colorCurrent = barConfig.getString("color-current", "&e");
        String colorNeeded = barConfig.getString("color-needed", "&7");

        int achievedChars = (int) Math.floor((percent / 100.0) * totalChars);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalChars; i++) {
            if (i < achievedChars) {
                sb.append(colorAchieved);
            } else if (i == achievedChars && percent > 0 && percent < 100) {
                sb.append(colorCurrent);
            } else {
                sb.append(colorNeeded);
            }
            sb.append(barChar);
        }

        return legacySerializer.serialize(miniMessage.deserialize(sb.toString()));
    }

    private String formatDisplay(OfflinePlayer player, String text) {
        String papiParsed = PlaceholderAPI.setPlaceholders(player, text);
        return legacySerializer.serialize(miniMessage.deserialize(papiParsed));
    }
}