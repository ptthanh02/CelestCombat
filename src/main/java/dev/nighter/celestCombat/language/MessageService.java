package dev.nighter.celestCombat.language;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class MessageService {
    private final JavaPlugin plugin;
    private final LanguageManager languageManager;

    // Static empty map to avoid creating new HashMap instances
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    // Cache for key existence checks to reduce repeated lookups
    private final Map<String, Boolean> keyExistsCache = new ConcurrentHashMap<>(128);

    /**
     * Sends a message to a CommandSender with no placeholders
     * @param sender The command sender to receive the message
     * @param key The message key
     */
    public void sendMessage(CommandSender sender, String key) {
        // Use shared empty map instead of creating a new HashMap
        sendMessage(sender, key, EMPTY_PLACEHOLDERS);
    }

    /**
     * Sends a message to a Player with no placeholders
     * @param player The player to receive the message
     * @param key The message key
     */
    public void sendMessage(Player player, String key) {
        // Use shared empty map instead of creating a new HashMap
        sendMessage(player, key, EMPTY_PLACEHOLDERS);
    }

    /**
     * Sends a message to a Player with placeholders
     * @param player The player to receive the message
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the message
     */
    public void sendMessage(Player player, String key, Map<String, String> placeholders) {
        // Use the CommandSender version but with player-specific features
        sendMessage((CommandSender) player, key, placeholders);
    }

    /**
     * Sends a message to a CommandSender with placeholders
     * @param sender The command sender to receive the message
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the message
     */
    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        // Validate the message key exists (using cache to avoid lookups)
        if (!checkKeyExists(key)) {
            plugin.getLogger().warning("Message key not found: " + key);
            sender.sendMessage("§cMissing message key: " + key);
            return;
        }

        // Get and send the chat message if it exists
        String message = languageManager.getMessage(key, placeholders);
        if (message != null && !message.startsWith("Missing message:")) {
            sender.sendMessage(message);
        }

        // Process player-specific features
        if (sender instanceof Player player) {
            sendPlayerSpecificContent(player, key, placeholders);
        }
    }

    /**
     * Check if a key exists, using cache for efficiency
     * @param key The message key to check
     * @return true if the key exists, false otherwise
     */
    private boolean checkKeyExists(String key) {
        return keyExistsCache.computeIfAbsent(key, languageManager::keyExists);
    }

    /**
     * Clear the key existence cache (used during reloads)
     */
    public void clearKeyExistsCache() {
        keyExistsCache.clear();
    }

    /**
     * Sends a message to the console with no placeholders
     * @param key The message key
     */
    public void sendConsoleMessage(String key) {
        sendConsoleMessage(key, EMPTY_PLACEHOLDERS);
    }

    /**
     * Sends a message to the console with placeholders
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the message
     */
    public void sendConsoleMessage(String key, Map<String, String> placeholders) {
        // Validate the message key exists
        if (!languageManager.keyExists(key)) {
            plugin.getLogger().warning("Message key not found: " + key);
            plugin.getLogger().warning("§cMissing message key: " + key);
            return;
        }

        // Get the raw message without prefix for console formatting
        String message = languageManager.getRawMessage(key, placeholders);
        if (message != null && !message.startsWith("Missing message:")) {
            // Strip color codes for console
            String consoleMessage = stripColorCodes(message);
            plugin.getLogger().info(consoleMessage);
        }
    }

    /**
     * Strips color codes from a message for console output
     * @param message The message with color codes
     * @return The message without color codes
     */
    private String stripColorCodes(String message) {
        // Remove standard color codes (§) and RGB hex codes
        return message.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "")
                .replaceAll("&#[0-9a-fA-F]{6}", "")
                .replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");
    }

    /**
     * Handles player-specific message components (title, subtitle, action bar, sound)
     * @param player The player to receive the content
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the content
     */
    private void sendPlayerSpecificContent(Player player, String key, Map<String, String> placeholders) {
        // Title and subtitle
        String title = languageManager.getTitle(key, placeholders);
        String subtitle = languageManager.getSubtitle(key, placeholders);
        if (title != null || subtitle != null) {
            player.sendTitle(
                    title != null ? title : "",
                    subtitle != null ? subtitle : "",
                    10, 70, 20
            );
        }

        // Action bar
        String actionBar = languageManager.getActionBar(key, placeholders);
        if (actionBar != null) {
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(actionBar)
            );
        }

        // Sound
        String soundName = languageManager.getSound(key);
        if (soundName != null) {
            try {
                player.playSound(player.getLocation(), soundName, 1.0f, 1.0f);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid sound name for key " + key + ": " + soundName);
            }
        }
    }
}