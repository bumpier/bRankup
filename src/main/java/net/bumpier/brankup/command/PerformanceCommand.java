package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.util.PerformanceMonitor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PerformanceCommand implements CommandExecutor {

    private final bRankup plugin;
    private final PerformanceMonitor performanceMonitor;

    public PerformanceCommand(bRankup plugin) {
        this.plugin = plugin;
        this.performanceMonitor = plugin.getPerformanceMonitor();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("brankup.admin.performance")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            // Show performance summary
            sender.sendMessage("§6=== bRankup Performance Monitor ===");
            sender.sendMessage(performanceMonitor.getPerformanceSummary());
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reset":
                    performanceMonitor.resetMetrics();
                    sender.sendMessage("§aPerformance metrics have been reset.");
                    return true;
                    
                case "cache":
                    // Show cache statistics
                    sender.sendMessage("§6=== Cache Performance ===");
                    sender.sendMessage(String.format("Cache hit ratio: §e%.1f%%", 
                        performanceMonitor.getCacheHitRatio() * 100));
                    return true;
                    
                case "timing":
                    // Show operation timing
                    sender.sendMessage("§6=== Operation Timing ===");
                    sender.sendMessage(String.format("Auto-progression: §e%.3f ms", 
                        performanceMonitor.getAverageOperationDuration("auto-progression")));
                    sender.sendMessage(String.format("Cost calculation: §e%.3f ms", 
                        performanceMonitor.getAverageOperationDuration("cost-calculation")));
                    sender.sendMessage(String.format("Database operations: §e%.3f ms", 
                        performanceMonitor.getAverageOperationDuration("database-operation")));
                    sender.sendMessage(String.format("Reward dispatching: §e%.3f ms", 
                        performanceMonitor.getAverageOperationDuration("reward-dispatching")));
                    return true;
                    
                case "help":
                    showHelp(sender);
                    return true;
                    
                default:
                    sender.sendMessage("§cUnknown subcommand. Use '/performance help' for available options.");
                    return true;
            }
        }

        sender.sendMessage("§cToo many arguments. Use '/performance help' for usage information.");
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== Performance Command Help ===");
        sender.sendMessage("§e/performance §7- Show performance summary");
        sender.sendMessage("§e/performance reset §7- Reset all performance metrics");
        sender.sendMessage("§e/performance cache §7- Show cache performance statistics");
        sender.sendMessage("§e/performance timing §7- Show operation timing statistics");
        sender.sendMessage("§e/performance help §7- Show this help message");
    }
} 