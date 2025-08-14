package net.bumpier.brankup.progression;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.ArrayList;

public class RewardDispatcher extends BukkitRunnable {

    private final List<String> commands;
    private final List<String> batchedCommands;
    private int currentBatchIndex = 0;
    
    // OPTIMIZATION: Batch size for more efficient command execution
    private static final int BATCH_SIZE = 5;
    
    // OPTIMIZATION: Delay between batches to prevent server lag
    private static final int BATCH_DELAY_TICKS = 2;

    public RewardDispatcher(List<String> commands) {
        this.commands = new ArrayList<>(commands);
        this.batchedCommands = new ArrayList<>();
        
        // OPTIMIZATION: Pre-process commands into batches for more efficient execution
        for (int i = 0; i < commands.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, commands.size());
            List<String> batch = commands.subList(i, endIndex);
            
            // OPTIMIZATION: Combine multiple commands into single console execution when possible
            if (batch.size() > 1) {
                StringBuilder combinedCommand = new StringBuilder();
                for (String cmd : batch) {
                    if (combinedCommand.length() > 0) {
                        combinedCommand.append(" && ");
                    }
                    combinedCommand.append(cmd);
                }
                batchedCommands.add(combinedCommand.toString());
            } else {
                batchedCommands.addAll(batch);
            }
        }
    }

    @Override
    public void run() {
        if (currentBatchIndex >= batchedCommands.size()) {
            this.cancel(); // No more commands to run, stop the task.
            return;
        }

        // OPTIMIZATION: Execute current batch of commands
        String commandToDispatch = batchedCommands.get(currentBatchIndex);
        currentBatchIndex++;

        // OPTIMIZATION: Execute the command from the console with error handling
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToDispatch);
        } catch (Exception e) {
            // OPTIMIZATION: Log errors but continue processing other commands
            Bukkit.getLogger().warning("Failed to execute reward command: " + commandToDispatch + " - " + e.getMessage());
        }
        
        // OPTIMIZATION: Schedule next batch with delay to prevent server lag
        if (currentBatchIndex < batchedCommands.size()) {
            // Schedule next batch execution
            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("bRankup"), 
                () -> run(), BATCH_DELAY_TICKS);
        }
    }
}