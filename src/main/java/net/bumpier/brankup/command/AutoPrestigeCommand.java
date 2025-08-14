package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AutoPrestigeCommand implements CommandExecutor {

    private final bRankup plugin;
    private final AdventureMessageService messageService;
    private final String permission;

    public AutoPrestigeCommand(bRankup plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.permission = plugin.getConfigManager().getMainConfig().getString("prestige-settings.features.auto-prestige.permission", "brankup.prestige.auto");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendMessage(sender, "error-players-only");
            return true;
        }
        if (!player.hasPermission(permission)) {
            messageService.sendMessage(player, "error-no-permission");
            return true;
        }

        PlayerRankData data = plugin.getPlayerManagerService().getData(player.getUniqueId());
        if (data == null) {
            messageService.sendMessage(player, "error-data-not-loaded");
            return true;
        }

        boolean newState = !data.isAutoPrestigeEnabled();
        data.setAutoPrestigeEnabled(newState);

        if (newState) {
            messageService.sendMessage(player, "auto-prestige-enabled");
        } else {
            messageService.sendMessage(player, "auto-prestige-disabled");
        }
        return true;
    }
}