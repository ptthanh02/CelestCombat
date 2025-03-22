package dev.nighter.celesCombat.language.message;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.language.LanguageManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MessageService {
    private final CelesCombat plugin;
    private final LanguageManager languageManager;

    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, new HashMap<>());
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        // Add player placeholder by default if sender is a player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            placeholders.put("player", player.getName());
        }

        String locale = getLocale(sender);

        // Chat message - only send if message exists
        String message = languageManager.getMessage(key, locale, placeholders);
        if (message != null && !message.startsWith("Missing message:")) {
            sender.sendMessage(message);
        }

        // Additional player-specific features only if sender is a Player
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // Title and subtitle - only show if at least one exists
            String title = languageManager.getTitle(key, locale, placeholders);
            String subtitle = languageManager.getSubtitle(key, locale, placeholders);
            if (title != null || subtitle != null) {
                player.sendTitle(title != null ? title : "", subtitle != null ? subtitle : "", 10, 70, 20);
            }

            // Action bar - only show if exists
            String actionBar = languageManager.getActionBar(key, locale, placeholders);
            if (actionBar != null) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBar));
            }

            // Sound - only play if exists
            String soundName = languageManager.getSound(key, locale);
            if (soundName != null) {
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound name: " + soundName);
                }
            }
        }
    }

    // Keep the original method for backward compatibility
    public void sendMessage(Player player, String key) {
        sendMessage((CommandSender) player, key, new HashMap<>());
    }

    // Keep the original method for backward compatibility
    public void sendMessage(Player player, String key, Map<String, String> placeholders) {
        sendMessage((CommandSender) player, key, placeholders);
    }

    private String getLocale(CommandSender sender) {
        // Could be expanded to get sender's preferred locale from a database
        // For now, just return default locale
        return languageManager.getDefaultLocale();
    }

    private String getPlayerLocale(Player player) {
        // Maintained for backward compatibility
        return getLocale(player);
    }
}