package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.progression.ProgressionType;
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

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "progression" -> handleProgressionSubcommand(sender, args);
            default -> messageService.sendMessage(sender, "admin-command-usage");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.performReload();
        messageService.sendMessage(sender, "admin-reload-success");
    }

    private void handleProgressionSubcommand(CommandSender sender, String[] args) {
        if (args.length != 5) {
            messageService.sendMessage(sender, "admin-command-usage");
            return;
        }

        String action = args[1].toLowerCase();
        String typeId = args[2].toLowerCase();
        String playerName = args[3];
        long amount;

        try {
            amount = Long.parseLong(args[4]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            messageService.sendMessage(sender, "error-invalid-number", "amount", args[4]);
            return;
        }

        ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
        if (type == null) {
            messageService.sendMessage(sender, "error-invalid-progression-type", "type", typeId);
            return;
        }

        // ** THIS IS THE FIX **
        // Replaced the Paper-specific method with the universal Bukkit/Spigot method.
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        // Added a check to ensure the player has actually joined the server before.
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            messageService.sendMessage(sender, "error-player-not-found", "player", playerName);
            return;
        }
        UUID targetUUID = targetPlayer.getUniqueId();

        if (targetPlayer.isOnline()) {
            PlayerRankData data = plugin.getPlayerManagerService().getData(targetUUID);
            if (data != null) processProgressionChange(sender, targetPlayer, data, type, action, amount);
        } else {
            plugin.getDatabaseService().loadPlayerData(targetUUID).thenAccept(data -> {
                if (data != null) {
                    processProgressionChange(sender, targetPlayer, data, type, action, amount);
                    plugin.getDatabaseService().savePlayerData(data);
                }
            });
        }
    }

    private void processProgressionChange(CommandSender sender, OfflinePlayer target, PlayerRankData data, ProgressionType type, String action, long amount) {
        long maxLevel = type.getLimit();
        long currentLevel = data.getProgressionLevel(type.getId());
        long newLevel = currentLevel;

        switch (action) {
            case "set" -> newLevel = amount;
            case "add" -> newLevel = currentLevel + amount;
            case "remove" -> newLevel = currentLevel - amount;
            default -> {
                messageService.sendMessage(sender, "admin-command-usage");
                return;
            }
        }

        if (newLevel > maxLevel) {
            messageService.sendMessage(sender, "error-rank-above-max", "max_rank", String.valueOf(maxLevel));
            return;
        }
        if (newLevel < 0) {
            messageService.sendMessage(sender, "error-rank-below-zero");
            return;
        }

        data.setProgressionLevel(type.getId(), newLevel);

        String typeName = type.getDisplayName();
        String targetName = target.getName();

        if (target.isOnline() && target.getPlayer() != null) {
            if (action.equals("set")) {
                messageService.sendMessage(target.getPlayer(), "admin-progression-notify-set", "type_name", typeName, "amount", String.valueOf(newLevel));
            } else {
                messageService.sendMessage(target.getPlayer(), "admin-progression-notify-change", "type_name", typeName, "new_level", String.valueOf(newLevel));
            }
        }

        messageService.sendMessage(sender, "admin-progression-" + action,
                "player", targetName, "type_name", typeName,
                "amount", String.valueOf(action.equals("set") ? newLevel : amount));
    }
}