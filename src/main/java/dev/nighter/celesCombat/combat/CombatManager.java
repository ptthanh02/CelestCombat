package dev.nighter.celesCombat.combat;

import dev.nighter.celesCombat.CelesCombat;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {
    private final CelesCombat plugin;
    @Getter private final Map<UUID, Long> playersInCombat;
    private final Map<UUID, BukkitTask> combatTasks;
    private final Map<UUID, BukkitTask> countdownTasks;

    // Add a map to track combat opponents
    private final Map<UUID, UUID> combatOpponents;

    public CombatManager(CelesCombat plugin) {
        this.plugin = plugin;
        this.playersInCombat = new HashMap<>();
        this.combatTasks = new HashMap<>();
        this.countdownTasks = new HashMap<>();
        this.combatOpponents = new HashMap<>();
    }

    /**
     * Puts a player in combat
     */
    public void tagPlayer(Player player, Player attacker) {
        UUID playerUUID = player.getUniqueId();
        int combatTime = plugin.getConfig().getInt("combat.duration", 20);

        // Check if this is a mutual combat (both players attacking each other)
        boolean alreadyInCombatWithAttacker =
                isInCombat(player) &&
                        attacker.getUniqueId().equals(combatOpponents.get(playerUUID));

        // If they're already in combat with this specific attacker, only refresh if lower time
        if (alreadyInCombatWithAttacker) {
            long currentEndTime = playersInCombat.get(playerUUID);
            long newEndTime = System.currentTimeMillis() + (combatTime * 1000L);

            // Only update if the new combat time would be longer
            if (newEndTime <= currentEndTime) {
                return; // Skip tagging as it wouldn't extend combat time
            }
        }

        // Store the opponent information
        combatOpponents.put(playerUUID, attacker.getUniqueId());

        // Check if player is already in combat
        if (isInCombat(player)) {
            // Cancel the existing tasks
            BukkitTask existingTask = combatTasks.get(playerUUID);
            if (existingTask != null) {
                existingTask.cancel();
            }

            BukkitTask existingCountdownTask = countdownTasks.get(playerUUID);
            if (existingCountdownTask != null) {
                existingCountdownTask.cancel();
            }
        } else {
            // Send combat start message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("time", String.valueOf(combatTime));
            plugin.getMessageService().sendMessage(player, "combat_tagged", placeholders);
        }

        // Update combat tag time
        playersInCombat.put(playerUUID, System.currentTimeMillis() + (combatTime * 1000L));

        // Create new task to remove combat tag after the specified time
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeFromCombat(player);
        }, combatTime * 20L);

        combatTasks.put(playerUUID, task);

        // Start the countdown timer that updates every second
        startCountdownTimer(player);
    }

    /**
     * Start a countdown timer that shows the remaining combat time every second
     */
    private void startCountdownTimer(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Cancel any existing countdown task
        BukkitTask existingTask = countdownTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Create a new task that runs every second
        BukkitTask countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && isInCombat(player)) {
                int remainingTime = getRemainingCombatTime(player);

                // Send countdown message
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("time", String.valueOf(remainingTime));

                // Check if we should display the countdown message
                if (plugin.getConfig().getBoolean("combat.show_countdown", true)) {
                    plugin.getMessageService().sendMessage(player, "combat_countdown", placeholders);
                }
            } else {
                // If player is no longer in combat, cancel this task
                BukkitTask task = countdownTasks.get(playerUUID);
                if (task != null) {
                    task.cancel();
                    countdownTasks.remove(playerUUID);
                }
            }
        }, 20L, 20L); // 20 ticks = 1 second

        countdownTasks.put(playerUUID, countdownTask);
    }

    /**
     * Removes a player from combat
     */
    public void removeFromCombat(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        if (isInCombat(player)) {
            playersInCombat.remove(playerUUID);

            // Remove opponent information
            combatOpponents.remove(playerUUID);

            // Cancel the combat task if it exists
            BukkitTask task = combatTasks.remove(playerUUID);
            if (task != null) {
                task.cancel();
            }

            // Cancel the countdown task if it exists
            BukkitTask countdownTask = countdownTasks.remove(playerUUID);
            if (countdownTask != null) {
                countdownTask.cancel();
            }

            // Only send the combat ended message if the player is online
            // This prevents sending messages to dead players
            if (player.isOnline() && player.getHealth() > 0) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                plugin.getMessageService().sendMessage(player, "combat_ended", placeholders);
            }
        }
    }

    /**
     * Gets the player's current combat opponent
     */
    public Player getCombatOpponent(Player player) {
        if (player == null || !isInCombat(player)) return null;

        UUID opponentUUID = combatOpponents.get(player.getUniqueId());
        if (opponentUUID == null) return null;

        return Bukkit.getPlayer(opponentUUID);
    }

    /**
     * Checks if a player is in combat
     */
    public boolean isInCombat(Player player) {
        if (player == null) return false;

        UUID playerUUID = player.getUniqueId();

        if (!playersInCombat.containsKey(playerUUID)) {
            return false;
        }

        long combatEndTime = playersInCombat.get(playerUUID);

        // If current time is past the end time, remove from combat
        if (System.currentTimeMillis() > combatEndTime) {
            removeFromCombat(player);
            return false;
        }

        return true;
    }

    /**
     * Gets the remaining combat time in seconds
     */
    public int getRemainingCombatTime(Player player) {
        if (!isInCombat(player)) return 0;

        long endTime = playersInCombat.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();

        // Calculate remaining time in seconds
        return (int) Math.max(0, (endTime - currentTime) / 1000);
    }

    /**
     * Handles when a player kills another player who is in combat
     */
    public void handlePlayerKill(Player killer, Player victim) {
        // Check if the killer is in combat
        if (isInCombat(killer)) {
            // Remove the killer from combat
            removeFromCombat(killer);

            // Send message to killer
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", killer.getName());
            placeholders.put("victim", victim.getName());
            plugin.getMessageService().sendMessage(killer, "combat_exit_on_kill", placeholders);
        }
    }

    /**
     * Cleans up all combat tasks
     */
    public void shutdown() {
        combatTasks.values().forEach(BukkitTask::cancel);
        combatTasks.clear();

        countdownTasks.values().forEach(BukkitTask::cancel);
        countdownTasks.clear();

        playersInCombat.clear();
        combatOpponents.clear();

        // Clean up the explosion tracker
        ExplosionTracker.clearAll();
    }
}