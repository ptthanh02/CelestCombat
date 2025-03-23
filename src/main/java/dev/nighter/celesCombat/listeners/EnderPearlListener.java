package dev.nighter.celesCombat.listeners;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class EnderPearlListener implements Listener {
    private final CelesCombat plugin;
    private final CombatManager combatManager;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearlUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if player is using an ender pearl
        if (item != null && item.getType() == Material.ENDER_PEARL) {
            // Check if ender pearl is on cooldown
            if (combatManager.isEnderPearlOnCooldown(player) && !player.hasPermission("celescombat.bypass.pearlcooldown")) {
                event.setCancelled(true);

                // Send cooldown message
                int remainingTime = combatManager.getRemainingEnderPearlCooldown(player);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("time", String.valueOf(remainingTime));
                plugin.getMessageService().sendMessage(player, "enderpearl_cooldown", placeholders);
            } else {
                // Set cooldown when player uses an ender pearl
                combatManager.setEnderPearlCooldown(player);
            }
        }
    }
}