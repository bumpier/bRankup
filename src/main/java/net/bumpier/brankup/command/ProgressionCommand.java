package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.progression.ProgressionType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command to manage and view progression types
 * Usage: /progression [subcommand] [args]
 */
public class ProgressionCommand implements CommandExecutor, TabCompleter {
    
    private final bRankup plugin;
    
    public ProgressionCommand(bRankup plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("brankup.admin.progression")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "list":
                showProgressionTypes(sender);
                break;
            case "info":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /progression info <type>");
                    return true;
                }
                showProgressionInfo(sender, args[1]);
                break;
            case "reload":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /progression reload <type>");
                    return true;
                }
                reloadProgressionType(sender, args[1]);
                break;
            case "stats":
                showProgressionStats(sender);
                break;
            case "help":
            default:
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== Progression Management ===");
        sender.sendMessage("§e/progression list §7- Show all progression types");
        sender.sendMessage("§e/progression info <type> §7- Show detailed info about a type");
        sender.sendMessage("§e/progression reload <type> §7- Reload a specific progression type");
        sender.sendMessage("§e/progression stats §7- Show progression system statistics");
        sender.sendMessage("§e/progression help §7- Show this help message");
    }
    
    private void showProgressionTypes(CommandSender sender) {
        sender.sendMessage("§6§l=== Progression Types ===");
        
        List<String> progressionOrder = plugin.getProgressionChainManager().getProgressionOrder();
        
        if (progressionOrder.isEmpty()) {
            sender.sendMessage("§cNo progression types loaded.");
            return;
        }
        
        for (int i = 0; i < progressionOrder.size(); i++) {
            String typeId = progressionOrder.get(i);
            ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
            
            if (type == null) continue;
            
            String status = type.isEnabled() ? "§a✓" : "§c✗";
            String follows = type.getFollows() != null ? "§7→ " + type.getFollows() : "§7(Base)";
            
            if (i == progressionOrder.size() - 1) {
                sender.sendMessage(String.format("§e└─ %s %s §7- %s %s", 
                    status, type.getDisplayName(), follows, type.getConfigFile()));
            } else {
                sender.sendMessage(String.format("§e├─ %s %s §7- %s %s", 
                    status, type.getDisplayName(), follows, type.getConfigFile()));
            }
        }
        
        sender.sendMessage("§7Total: §e" + progressionOrder.size() + " §7types");
    }
    
    private void showProgressionInfo(CommandSender sender, String typeId) {
        ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
        
        if (type == null) {
            sender.sendMessage("§cProgression type '" + typeId + "' not found.");
            return;
        }
        
        sender.sendMessage("§6§l=== " + type.getDisplayName() + " Info ===");
        sender.sendMessage("§7ID: §e" + type.getId());
        sender.sendMessage("§7Config File: §e" + type.getConfigFile());
        sender.sendMessage("§7Follows: §e" + (type.getFollows() != null ? type.getFollows() : "None (Base)"));
        sender.sendMessage("§7Command: §e/" + type.getCommand());
        sender.sendMessage("§7Enabled: " + (type.isEnabled() ? "§aYes" : "§cNo"));
        sender.sendMessage("§7Limit: §e" + type.getLimit());
        
        sender.sendMessage("§7§lFeatures:");
        sender.sendMessage("  §7Auto-Progression: " + (type.isAutoProgressionEnabled() ? "§aYes" : "§cNo"));
        if (type.isAutoProgressionEnabled()) {
            sender.sendMessage("    §7Permission: §e" + type.getAutoProgressionPermission());
            sender.sendMessage("    §7Delay: §e" + type.getAutoProgressionDelay() + " ticks");
        }
        
        sender.sendMessage("  §7Max Progression: " + (type.isMaxProgressionEnabled() ? "§aYes" : "§cNo"));
        if (type.isMaxProgressionEnabled()) {
            sender.sendMessage("    §7Permission: §e" + type.getMaxProgressionPermission());
        }
        
        sender.sendMessage("§7§lRequirements:");
        sender.sendMessage("  §7Requires Max Level: " + (type.requiresMaxLevel() ? "§aYes" : "§cNo"));
        
        if (!type.getCustomRequirements().isEmpty()) {
            sender.sendMessage("  §7Custom Requirements:");
            for (String req : type.getCustomRequirements()) {
                sender.sendMessage("    §7- §e" + req);
            }
        }
        
        sender.sendMessage("§7§lResets:");
        sender.sendMessage("  §7Reset Previous: " + (type.shouldResetPrevious() ? "§aYes" : "§cNo"));
        
        if (!type.getResetCurrencies().isEmpty()) {
            sender.sendMessage("  §7Reset Currencies:");
            for (Map.Entry<String, Boolean> entry : type.getResetCurrencies().entrySet()) {
                String status = entry.getValue() ? "§aYes" : "§cNo";
                sender.sendMessage("    §7- §e" + entry.getKey() + ": " + status);
            }
        }
        
        if (!type.getCustomResets().isEmpty()) {
            sender.sendMessage("  §7Custom Resets:");
            for (String reset : type.getCustomResets()) {
                sender.sendMessage("    §7- §e" + reset);
            }
        }
        
        sender.sendMessage("§7§lCurrency:");
        sender.sendMessage("  §7Type: §e" + type.getCurrencyType());
        sender.sendMessage("  §7Mode: §e" + type.getCalculationMode());
        
        sender.sendMessage("§7§lRewards:");
        sender.sendMessage("  §7Every Progression: §e" + type.getEveryProgressionRewards().size() + " commands");
        sender.sendMessage("  §7First Time: §e" + type.getFirstTimeRewards().size() + " levels");
        sender.sendMessage("  §7Intervals: §e" + type.getIntervalRewards().size() + " intervals");
    }
    
    private void reloadProgressionType(CommandSender sender, String typeId) {
        ProgressionType type = plugin.getProgressionChainManager().getProgressionType(typeId);
        
        if (type == null) {
            sender.sendMessage("§cProgression type '" + typeId + "' not found.");
            return;
        }
        
        boolean success = plugin.getProgressionChainManager().reloadProgressionType(typeId);
        
        if (success) {
            sender.sendMessage("§aSuccessfully reloaded progression type: " + type.getDisplayName());
        } else {
            sender.sendMessage("§cFailed to reload progression type: " + type.getDisplayName());
        }
    }
    
    private void showProgressionStats(CommandSender sender) {
        Map<String, Object> stats = plugin.getProgressionChainManager().getProgressionStats();
        
        sender.sendMessage("§6§l=== Progression Statistics ===");
        sender.sendMessage("§7Total Types: §e" + stats.get("total-types"));
        sender.sendMessage("§7Enabled Types: §e" + stats.get("enabled-types"));
        sender.sendMessage("§7Base Type: §e" + stats.get("base-type"));
        sender.sendMessage("§7Highest Type: §e" + stats.get("highest-type"));
        
        @SuppressWarnings("unchecked")
        List<String> order = (List<String>) stats.get("progression-order");
        if (order != null && !order.isEmpty()) {
            sender.sendMessage("§7Progression Order: §e" + String.join(" → ", order));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("brankup.admin.progression")) {
            return completions;
        }
        
        if (args.length == 1) {
            completions.add("list");
            completions.add("info");
            completions.add("reload");
            completions.add("stats");
            completions.add("help");
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            
            if ("info".equals(subcommand) || "reload".equals(subcommand)) {
                List<String> progressionOrder = plugin.getProgressionChainManager().getProgressionOrder();
                completions.addAll(progressionOrder);
            }
        }
        
        return completions;
    }
} 