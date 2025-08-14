package net.bumpier.brankup.util;

import net.bumpier.brankup.config.ConfigManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AdventureMessageService {

    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final FileConfiguration messagesConfig;

    public AdventureMessageService(BukkitAudiences audiences, ConfigManager configManager) {
        this.audiences = audiences;
        this.messagesConfig = configManager.getMessagesConfig();
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Sends a message to a CommandSender after parsing it with MiniMessage.
     *
     * @param sender The recipient of the message.
     * @param key The key of the message in messages.yml.
     * @param placeholders Placeholders to be replaced in the message, in key-value pairs.
     */
    public void sendMessage(CommandSender sender, String key, String... placeholders) {
        Audience recipient = audiences.sender(sender);
        String messageTemplate = messagesConfig.getString(key, "<red>Missing message: " + key);

        TagResolver placeholderResolver = createPlaceholderResolver(placeholders);
        Component parsedMessage = miniMessage.deserialize(messageTemplate, placeholderResolver);

        recipient.sendMessage(parsedMessage);
    }

    /**
     * New method to send a message directly from a string template.
     * @param sender The recipient of the message.
     * @param messageTemplate The raw string message with MiniMessage formatting.
     * @param placeholders Placeholders to be replaced.
     */
    public void sendParsedMessage(CommandSender sender, String messageTemplate, String... placeholders) {
        Audience recipient = audiences.sender(sender);
        TagResolver placeholderResolver = createPlaceholderResolver(placeholders);
        Component parsedMessage = miniMessage.deserialize(messageTemplate, placeholderResolver);
        recipient.sendMessage(parsedMessage);
    }

    public void sendTitle(Player player, ConfigurationSection titleConfig, String... placeholders) {
        if (titleConfig == null || !titleConfig.getBoolean("enabled", false)) {
            return;
        }

        Audience recipient = audiences.player(player);
        String titleTemplate = titleConfig.getString("title", "");
        String subtitleTemplate = titleConfig.getString("subtitle", "");

        TagResolver placeholderResolver = createPlaceholderResolver(placeholders);

        Component parsedTitle = miniMessage.deserialize(titleTemplate, placeholderResolver);
        Component parsedSubtitle = miniMessage.deserialize(subtitleTemplate, placeholderResolver);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(titleConfig.getInt("fade-in", 10) * 50L),
                Duration.ofMillis(titleConfig.getInt("stay", 40) * 50L),
                Duration.ofMillis(titleConfig.getInt("fade-out", 10) * 50L)
        );
        Title title = Title.title(parsedTitle, parsedSubtitle, times);

        recipient.showTitle(title);
    }

    private TagResolver createPlaceholderResolver(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be in key-value pairs.");
        }
        List<TagResolver> resolvers = new ArrayList<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            // MiniMessage placeholders do not include the enclosing <>
            resolvers.add(Placeholder.parsed(placeholders[i], placeholders[i + 1]));
        }
        return TagResolver.resolver(resolvers);
    }
}