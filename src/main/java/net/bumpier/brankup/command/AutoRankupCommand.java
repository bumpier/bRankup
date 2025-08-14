package net.bumpier.brankup.command;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import net.bumpier.brankup.util.AdventureMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AutoRankupCommand implements CommandExecutor {

    private final bRankup plugin;
    private final AdventureMessageService messageService;
    private final String permission;

    public AutoRankupCommand(bRankup plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.permission = plugin.getConfigManager().getMainConfig().getString("rankup-settings.features.auto-rankup.permission", "brankup.rank.auto");
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

        boolean newState = !data.isAutoRankupEnabled();
        data.setAutoRankupEnabled(newState);

        if (newState) {
            messageService.sendMessage(player, "auto-rankup-enabled");
        } else {
            messageService.sendMessage(player, "auto-rankup-disabled");
        }
        return true;
    }
}