package net.bumpier.brankup.progression;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ProgressionRewardService {

    private final bRankup plugin;
    private final ConfigurationSection rewardsConfig;

    public ProgressionRewardService(bRankup plugin, ConfigurationSection rewardsConfig) {
        this.plugin = plugin;
        this.rewardsConfig = rewardsConfig;
    }

    /**
     * Collects all reward commands for a given progression level-up without executing them.
     * @param player The player receiving the rewards.
     * @param newLevel The new level the player has achieved (can be a rank or prestige).
     * @return A list of command strings to be executed.
     */
    public List<String> collectRewards(Player player, long newLevel) {
        List<String> commandsToDispatch = new ArrayList<>();
        if (rewardsConfig == null) {
            return commandsToDispatch;
        }

        PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
        if (data == null) {
            return commandsToDispatch;
        }

        // 1. Every-level rewards (e.g., every-prestige)
        addParsedCommands(commandsToDispatch, player, rewardsConfig.getStringList("every-level"));
        // Support for legacy config key
        addParsedCommands(commandsToDispatch, player, rewardsConfig.getStringList("every-prestige"));


        // 2. Interval rewards
        ConfigurationSection intervalConfig = rewardsConfig.getConfigurationSection("interval-rewards");
        if (intervalConfig != null) {
            for (String key : intervalConfig.getKeys(false)) {
                if (key.startsWith("every-")) {
                    try {
                        int interval = Integer.parseInt(key.substring(6));
                        if (interval > 0 && newLevel % interval == 0) {
                            addParsedCommands(commandsToDispatch, player, intervalConfig.getStringList(key));
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed keys like "every-ten"
                    }
                }
            }
        }

        // 3. First-time rewards
        ConfigurationSection firstTimeConfig = rewardsConfig.getConfigurationSection("first-time-rewards");
        if (firstTimeConfig != null) {
            String levelKey = String.valueOf(newLevel);
            // We create a unique key for the reward type to prevent conflicts (e.g., "rank-50" vs "prestige-50")
            String rewardId = rewardsConfig.getName() + "-" + levelKey;

            if (firstTimeConfig.contains(levelKey) && !data.getClaimedOneTimeRewards().contains(rewardId)) {
                addParsedCommands(commandsToDispatch, player, firstTimeConfig.getStringList(levelKey));
                data.addClaimedReward(rewardId);
                plugin.getDatabaseService().saveClaimedReward(player.getUniqueId(), rewardId);
            }
        }

        return commandsToDispatch;
    }

    private void addParsedCommands(List<String> commandList, Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            String parsedCommand = command.replace("%brankup_player_name%", player.getName());
            // This is where PlaceholderAPI would be integrated in a future step.
            commandList.add(parsedCommand);
        }
    }

    /**
     * Collects and immediately dispatches rewards for a given level-up.
     * @param player The player receiving the rewards.
     * @param newLevel The new level achieved.
     */
    public void dispatchRewards(Player player, long newLevel) {
        List<String> commands = collectRewards(player, newLevel);
        if (!commands.isEmpty()) {
            new RewardDispatcher(commands).runTaskTimer(plugin, 0L, 1L);
        }
    }
}