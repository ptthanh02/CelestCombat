package dev.nighter.celestCombat.listeners;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class TridentListener implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;

    // Track players with active trident countdown displays to avoid duplicates
    private final Map<UUID, Scheduler.Task> tridentCountdownTasks = new ConcurrentHashMap<>();

    // Track thrown tridents to their player owners
    private final Map<Integer, UUID> activeTridents = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();

        // Check if player is right-clicking with a trident
        if (item != null && item.getType() == Material.TRIDENT &&
                (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {

            // Check if trident usage is banned in this world
            if (combatManager.isTridentBanned(player)) {
                event.setCancelled(true);

                // Send banned message
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                plugin.getMessageService().sendMessage(player, "trident_banned", placeholders);
                return;
            }

            // Check if trident is on cooldown
            if (combatManager.isTridentOnCooldown(player)) {
                event.setCancelled(true);

                // Send cooldown message
                int remainingTime = combatManager.getRemainingTridentCooldown(player);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("time", String.valueOf(remainingTime));
                plugin.getMessageService().sendMessage(player, "trident_cooldown", placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident && event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();

            // Check if trident usage is banned in this world
            if (combatManager.isTridentBanned(player)) {
                event.setCancelled(true);

                // Send banned message
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                plugin.getMessageService().sendMessage(player, "trident_banned", placeholders);
                return;
            }

            // Check if trident is on cooldown
            if (combatManager.isTridentOnCooldown(player)) {
                event.setCancelled(true);

                // Send cooldown message
                int remainingTime = combatManager.getRemainingTridentCooldown(player);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("time", String.valueOf(remainingTime));
                plugin.getMessageService().sendMessage(player, "trident_cooldown", placeholders);
            } else {
                // Set cooldown when player successfully launches a trident
                combatManager.setTridentCooldown(player);

                // Start displaying the countdown for trident cooldown
                startTridentCountdown(player);

                // Track this trident to the player for the hit event
                activeTridents.put(event.getEntity().getEntityId(), player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Trident) {
            // Get the trident's entity ID
            int tridentId = event.getEntity().getEntityId();

            // Check if we're tracking this trident
            if (activeTridents.containsKey(tridentId)) {
                UUID playerUUID = activeTridents.remove(tridentId);
                Player player = plugin.getServer().getPlayer(playerUUID);

                if (player != null && player.isOnline()) {
                    // Trident landed, refresh combat if enabled
                    combatManager.refreshCombatOnTridentLand(player);
                }
            }
        }
    }

    /**
     * Starts a separate countdown task for trident cooldown display.
     * This ensures the countdown is shown regardless of combat status.
     */
    private void startTridentCountdown(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        // Cancel any existing countdown task for this player
        Scheduler.Task existingTask = tridentCountdownTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // How often to update the countdown message (in ticks, 20 = 1 second)
        long updateInterval = 20L;

        // Create a new countdown task
        Scheduler.Task task = Scheduler.runTaskTimer(() -> {
            // Check if player is still online
            if (!player.isOnline()) {
                cancelTridentCountdown(playerUUID);
                return;
            }

            // Check if cooldown is still active
            if (!combatManager.isTridentOnCooldown(player)) {
                cancelTridentCountdown(playerUUID);
                return;
            }

            // Get remaining time
            int remainingTime = combatManager.getRemainingTridentCooldown(player);

            // Send the appropriate message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("time", String.valueOf(remainingTime));

            // If player is in combat, CombatManager will handle the combined message
            // Otherwise, send a trident-specific message
            if (!combatManager.isInCombat(player)) {
                plugin.getMessageService().sendMessage(player, "trident_only_countdown", placeholders);
            }

        }, 0L, updateInterval);

        // Store the task
        tridentCountdownTasks.put(playerUUID, task);
    }

    /**
     * Cancels and removes the trident countdown task for a player.
     */
    private void cancelTridentCountdown(UUID playerUUID) {
        Scheduler.Task task = tridentCountdownTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Cleanup method to cancel all tasks when the plugin is disabled.
     * Call this from your main plugin's onDisable method.
     */
    public void shutdown() {
        tridentCountdownTasks.values().forEach(Scheduler.Task::cancel);
        tridentCountdownTasks.clear();
        activeTridents.clear();
    }
}