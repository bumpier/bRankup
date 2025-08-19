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
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class bRankupAdminCommand implements CommandExecutor, TabCompleter {

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

        if ("reload".equals(mainArg)) {
            if (args.length == 1) {
                handleReload(sender);
                return true;
            }
        }

        if (args.length == 4) {
            handleProgressionSubcommand(sender, args);
        } else {
            messageService.sendMessage(sender, "admin-command-usage");
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.performReload();
        messageService.sendMessage(sender, "admin-reload-success");
        plugin.getLogger().info("Configuration files have been reloaded by " + sender.getName() + ".");
    }

    private void handleProgressionSubcommand(CommandSender sender, String[] args) {
        String typeId = args[0].toLowerCase();
        ProgressionType progressionType = plugin.getProgressionChainManager().getProgressionType(typeId);

        if (progressionType == null) {
            messageService.sendMessage(sender, "error-invalid-progression-type");
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

        // CORRECTED: Use the getOrLoadData method, which exists and is async.
        plugin.getPlayerManagerService().getOrLoadData(targetUUID).thenAccept(data -> {
            if (data != null) {
                processLevelChange(sender, targetPlayer, data, progressionType, action, amount);
                if (!targetPlayer.isOnline()) {
                    plugin.getDatabaseService().savePlayerData(data);
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to modify offline player data for " + playerName, ex);
            return null;
        });
    }

    private void processLevelChange(CommandSender sender, OfflinePlayer target, PlayerRankData data, ProgressionType type, String action, long amount) {
        long maxLevel = type.getLimit();
        long currentLevel = data.getProgressionLevel(type.getId());
        long newLevel = currentLevel;

        switch (action) {
            case "set" -> {
                if (amount > maxLevel) {
                    messageService.sendMessage(sender, "error-level-above-max", "max_level", String.valueOf(maxLevel));
                    return;
                }
                newLevel = amount;
                messageService.sendMessage(sender, "admin-generic-set", "player", target.getName(), "type", type.getDisplayName(), "amount", String.valueOf(newLevel));
                if (target.isOnline() && target.getPlayer() != null) {
                    messageService.sendMessage(target.getPlayer(), "admin-generic-notify-set", "type", type.getDisplayName(), "amount", String.valueOf(newLevel));
                }
            }
            case "add" -> {
                newLevel = currentLevel + amount;
                if (newLevel > maxLevel) {
                    messageService.sendMessage(sender, "error-level-above-max", "max_level", String.valueOf(maxLevel));
                    return;
                }
                messageService.sendMessage(sender, "admin-generic-add", "player", target.getName(), "type", type.getDisplayName(), "amount", String.valueOf(amount));
                if (target.isOnline() && target.getPlayer() != null) {
                    messageService.sendMessage(target.getPlayer(), "admin-generic-notify-change", "type", type.getDisplayName(), "new_level", String.valueOf(newLevel));
                }
            }
            case "remove" -> {
                newLevel = currentLevel - amount;
                if (newLevel < 0) {
                    messageService.sendMessage(sender, "error-level-below-zero");
                    return;
                }
                messageService.sendMessage(sender, "admin-generic-remove", "player", target.getName(), "type", type.getDisplayName(), "amount", String.valueOf(amount));
                if (target.isOnline() && target.getPlayer() != null) {
                    messageService.sendMessage(target.getPlayer(), "admin-generic-notify-change", "type", type.getDisplayName(), "new_level", String.valueOf(newLevel));
                }
            }
            default -> {
                messageService.sendMessage(sender, "admin-command-usage");
                return;
            }
        }
        data.setProgressionLevel(type.getId(), newLevel);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();
        final String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            completions.add("reload");
            plugin.getProgressionChainManager().getAllProgressionTypes().stream()
                    .map(ProgressionType::getId)
                    .forEach(completions::add);
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        ProgressionType progressionType = plugin.getProgressionChainManager().getProgressionType(args[0].toLowerCase());

        if (args.length == 2 && progressionType != null) {
            completions.addAll(Arrays.asList("set", "add", "remove"));
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && progressionType != null && Arrays.asList("set", "add", "remove").contains(args[1].toLowerCase())) {
            Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}