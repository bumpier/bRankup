package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;

public class bRankupAdminCommand implements CommandExecutor {

    private final bRankup plugin;
    private final AdventureMessageService messageService;

    public bRankupAdminCommand(bRankup plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            messageService.sendMessage(sender, "admin-command-usage");
            return true;
        }

        String mainArg = args[0].toLowerCase();

        switch (mainArg) {
            case "reload" -> handleReload(sender);
            case "rankup" -> handleRankupSubcommand(sender, args);
            default -> messageService.sendMessage(sender, "admin-command-usage");
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.performReload();
        messageService.sendMessage(sender, "admin-reload-success");
        plugin.getLogger().info("Configuration files have been reloaded by " + sender.getName() + ".");
    }

    private void handleRankupSubcommand(CommandSender sender, String[] args) {
        if (args.length != 4) {
            messageService.sendMessage(sender, "admin-command-usage");
            return;
        }

        String action = args[1].toLowerCase();
        String playerName = args[2];
        long amount;

        try {
            amount = Long.parseLong(args[3]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            messageService.sendMessage(sender, "error-invalid-number", "amount", args[3]);
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            messageService.sendMessage(sender, "error-player-not-found", "player", playerName);
            return;
        }
        UUID targetUUID = targetPlayer.getUniqueId();

        Player onlinePlayer = targetPlayer.getPlayer();
        if (onlinePlayer != null) {
            PlayerRankData data = plugin.getPlayerManagerService().getData(targetUUID);
            if (data != null) {
                processRankChange(sender, onlinePlayer, data, action, amount);
            }
        } else {
            plugin.getDatabaseService().loadPlayerData(targetUUID).thenAccept(data -> {
                if (data != null) {
                    processRankChange(sender, targetPlayer, data, action, amount);
                    plugin.getDatabaseService().savePlayerData(data);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to modify offline player data for " + playerName, ex);
                return null;
            });
        }
    }

    private void processRankChange(CommandSender sender, OfflinePlayer target, PlayerRankData data, String action, long amount) {
        long maxRank = plugin.getConfigManager().getMainConfig().getLong("rankup-settings.limit", 50);
        long currentRank = data.getRank();
        long newRank = currentRank;

        switch (action) {
            case "set" -> {
                if (amount > maxRank) {
                    messageService.sendMessage(sender, "error-rank-above-max", "max_rank", String.valueOf(maxRank));
                    return;
                }
                newRank = amount;
                data.setRank(newRank);
                messageService.sendMessage(sender, "admin-rank-set", "player", target.getName(), "amount", String.valueOf(newRank));
                if (target.isOnline()) {
                    messageService.sendMessage(target.getPlayer(), "admin-rank-notify-set", "amount", String.valueOf(newRank));
                }
            }
            case "add" -> {
                newRank = currentRank + amount;
                if (newRank > maxRank) {
                    messageService.sendMessage(sender, "error-rank-above-max", "max_rank", String.valueOf(maxRank));
                    return;
                }
                data.setRank(newRank);
                messageService.sendMessage(sender, "admin-rank-add", "player", target.getName(), "amount", String.valueOf(amount));
                if (target.isOnline()) {
                    messageService.sendMessage(target.getPlayer(), "admin-rank-notify-change", "new_rank", String.valueOf(newRank));
                }
            }
            case "remove" -> {
                newRank = currentRank - amount;
                if (newRank < 0) {
                    messageService.sendMessage(sender, "error-rank-below-zero");
                    return;
                }
                data.setRank(newRank);
                messageService.sendMessage(sender, "admin-rank-remove", "player", target.getName(), "amount", String.valueOf(amount));
                if (target.isOnline()) {
                    messageService.sendMessage(target.getPlayer(), "admin-rank-notify-change", "new_rank", String.valueOf(newRank));
                }
            }
            default -> messageService.sendMessage(sender, "admin-command-usage");
        }
    }

    private void handlePrestigeSubcommand(CommandSender sender, String[] args) {
        // Usage: /bra prestige <set|add|remove> <player> <amount>
        if (args.length != 4) {
            messageService.sendMessage(sender, "admin-command-usage");
            return;
        }

        String action = args[1].toLowerCase();
        String playerName = args[2];
        long amount;

        try {
            amount = Long.parseLong(args[3]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            messageService.sendMessage(sender, "error-invalid-number", "amount", args[3]);
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            messageService.sendMessage(sender, "error-player-not-found", "player", playerName);
            return;
        }
        UUID targetUUID = targetPlayer.getUniqueId();

        Player onlinePlayer = targetPlayer.getPlayer();
        if (onlinePlayer != null) {
            PlayerRankData data = plugin.getPlayerManagerService().getData(targetUUID);
            if (data != null) {
                processPrestigeChange(sender, onlinePlayer, data, action, amount);
            }
        } else {
            plugin.getDatabaseService().loadPlayerData(targetUUID).thenAccept(data -> {
                if (data != null) {
                    processPrestigeChange(sender, targetPlayer, data, action, amount);
                    plugin.getDatabaseService().savePlayerData(data);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to modify offline player data for " + playerName, ex);
                return null;
            });
        }
    }

    private void processPrestigeChange(CommandSender sender, OfflinePlayer target, PlayerRankData data, String action, long amount) {
        long maxPrestige = plugin.getConfigManager().getMainConfig().getLong("prestige-settings.limit", 50);
        long currentPrestige = data.getPrestige();
        long newPrestige = currentPrestige;

        switch (action) {
            case "set" -> {
                if (amount > maxPrestige) {
                    messageService.sendMessage(sender, "error-rank-above-max", "max_rank", String.valueOf(maxPrestige)); // Reusing error message
                    return;
                }
                newPrestige = amount;
                data.setPrestige(newPrestige);
                messageService.sendMessage(sender, "admin-prestige-set", "player", target.getName(), "amount", String.valueOf(newPrestige));
                if (target.isOnline()) {
                    messageService.sendMessage(target.getPlayer(), "admin-prestige-notify-set", "amount", String.valueOf(newPrestige));
                }
            }
            case "add" -> {
                newPrestige = currentPrestige + amount;
                if (newPrestige > maxPrestige) {
                    messageService.sendMessage(sender, "error-rank-above-max", "max_rank", String.valueOf(maxPrestige)); // Reusing error message
                    return;
                }
                data.setPrestige(newPrestige);
                messageService.sendMessage(sender, "admin-prestige-add", "player", target.getName(), "amount", String.valueOf(amount));
                if (target.isOnline()) {
                    messageService.sendMessage(target.getPlayer(), "admin-prestige-notify-change", "new_prestige", String.valueOf(newPrestige));
                }
            }
            case "remove" -> {
                newPrestige = currentPrestige - amount;
                if (newPrestige < 0) {
                    messageService.sendMessage(sender, "error-rank-below-zero");
                    return;
                }
                data.setPrestige(newPrestige);
                messageService.sendMessage(sender, "admin-prestige-remove", "player", target.getName(), "amount", String.valueOf(amount));
                if (target.isOnline()) {
                    messageService.sendMessage(target.getPlayer(), "admin-prestige-notify-change", "new_prestige", String.valueOf(newPrestige));
                }
            }
            default -> messageService.sendMessage(sender, "admin-command-usage");
        }

    }
}