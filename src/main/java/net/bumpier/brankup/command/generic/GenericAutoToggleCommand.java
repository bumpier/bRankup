package net.bumpier.brankup.command.generic;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.progression.ProgressionType;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GenericAutoToggleCommand implements CommandExecutor {

    private final bRankup plugin;
    private final AdventureMessageService messageService;
    private final ProgressionType progressionType;

    public GenericAutoToggleCommand(bRankup plugin, ProgressionType progressionType) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.progressionType = progressionType;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }
        if (!player.hasPermission(progressionType.getAutoProgressionPermission())) {
            messageService.sendMessage(player, "error-no-permission");
            return true;
        }

        PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
        if (data == null) {
            messageService.sendMessage(player, "error-data-not-loaded");
            return true;
        }

        boolean newState = !data.isAutoProgressionEnabled(progressionType.getId());
        data.setAutoProgressionEnabled(progressionType.getId(), newState);

        String displayName = progressionType.getDisplayName();
        if (newState) {
            messageService.sendMessage(player, "auto-progression-enabled", "type", displayName);
        } else {
            messageService.sendMessage(player, "auto-progression-disabled", "type", displayName);
        }
        return true;
    }
}