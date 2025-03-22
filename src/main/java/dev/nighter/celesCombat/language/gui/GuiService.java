package dev.nighter.celesCombat.language.gui;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.language.LanguageManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class GuiService {
    private final CelesCombat plugin;
    private final LanguageManager languageManager;

    public Inventory createInventory(Player player, String titleKey, int size) {
        return createInventory(player, titleKey, size, new HashMap<>());
    }

    public Inventory createInventory(Player player, String titleKey, int size, Map<String, String> placeholders) {
        String locale = getPlayerLocale(player);

        // Add player placeholder by default
        placeholders.put("player", player.getName());

        String title = languageManager.getGuiTitle(titleKey, locale, placeholders);
        return Bukkit.createInventory(null, size, title);
    }

    public ItemStack createItem(Player player, String itemKey, Material material) {
        return createItem(player, itemKey, material, new HashMap<>());
    }

    public ItemStack createItem(Player player, String itemKey, Material material, Map<String, String> placeholders) {
        String locale = getPlayerLocale(player);

        // Add player placeholder by default
        placeholders.put("player", player.getName());

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = languageManager.getGuiItemName(itemKey, locale, placeholders);
            meta.setDisplayName(name);

            String[] lore = languageManager.getGuiItemLore(itemKey, locale, placeholders);
            meta.setLore(Arrays.asList(lore));

            item.setItemMeta(meta);
        }

        return item;
    }

    public void playSound(Player player, String itemKey) {
        String locale = getPlayerLocale(player);
        String soundName = languageManager.getGuiItemSound(itemKey, locale);

        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name: " + soundName);
            }
        }
    }

    private String getPlayerLocale(Player player) {
        // Could be expanded to get player's preferred locale from a database
        return languageManager.getDefaultLocale();
    }

    // Example method to demonstrate usage
    private void exampleUsage() {
        // This would be called from a command or event handler
        // Example: When a player logs in

        // Get player from the event
        // Player player = event.getPlayer();

        // Create placeholders
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", "ExamplePlayer");

        // Send login message with all components (chat, title, subtitle, action bar, sound)
        // messageService.sendMessage(player, "player_successfully_logged_in", placeholders);

        // Create and show GUI
        // Inventory inventory = guiService.createInventory(player, "login_inv_title", 27, placeholders);
        // ItemStack existButton = guiService.createItem(player, "exist_button", Material.EMERALD, placeholders);
        // inventory.setItem(13, existButton);
        // player.openInventory(inventory);
    }
}