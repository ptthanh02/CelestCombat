package dev.nighter.celestCombat.language;

import dev.nighter.celestCombat.CelestCombat;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MessageService {
    private final CelestCombat plugin;
    private final LanguageManager languageManager;

    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, new HashMap<>());
    }

    // Keep the original method for backward compatibility
    public void sendMessage(Player player, String key, Map<String, String> placeholders) {
        sendMessage((CommandSender) player, key, placeholders);
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String locale = languageManager.getDefaultLocale();

        if (!languageManager.keyExists(key, locale)) {
            plugin.getLogger().warning("Message " + key + " doesn't exist in the language file.");
            sender.sendMessage("§8[§9CelestCombat§8]§c Message " + key + " doesn't exist in the language file.");
            return;
        }

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
                player.playSound(player.getLocation(), soundName , 1.0f, 1.0f);
            }
        }
    }
}