package dev.nighter.celesCombat.listeners;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
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
        Action action = event.getAction();

        // Check if player is right-clicking with an ender pearl
        if (item != null && item.getType() == Material.ENDER_PEARL &&
                (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {

            if (combatManager.isInCombat(player)) {
                // Check if ender pearl is on cooldown
                if (combatManager.isEnderPearlOnCooldown(player) && combatManager.isInCombat(player)) {
                    event.setCancelled(true);

                    // Send cooldown message
                    int remainingTime = combatManager.getRemainingEnderPearlCooldown(player);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", player.getName());
                    placeholders.put("time", String.valueOf(remainingTime));
                    plugin.getMessageService().sendMessage(player, "enderpearl_cooldown", placeholders);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl && event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();

            if (combatManager.isInCombat(player)){
                // Check if ender pearl is on cooldown
                if (combatManager.isEnderPearlOnCooldown(player)) {
                    event.setCancelled(true);

                    // Send cooldown message
                    int remainingTime = combatManager.getRemainingEnderPearlCooldown(player);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", player.getName());
                    placeholders.put("time", String.valueOf(remainingTime));
                    plugin.getMessageService().sendMessage(player, "enderpearl_cooldown", placeholders);
                } else {
                    // Set cooldown when player successfully launches an ender pearl
                    combatManager.setEnderPearlCooldown(player);
                }
            }
        }
    }
}