package dev.nighter.celestCombat.listeners;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class CombatListeners implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Boolean> playerLoggedOutInCombat = new ConcurrentHashMap<>();
    // Add a map to track the last damage source for each player
    private final Map<UUID, UUID> lastDamageSource = new ConcurrentHashMap<>();
    // Add a map to cleanup stale damage records
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    // Cleanup threshold (5 minutes)
    private static final long DAMAGE_RECORD_CLEANUP_THRESHOLD = TimeUnit.MINUTES.toMillis(5);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        Player victim = null;

        if (event.getEntity() instanceof Player) {
            victim = (Player) event.getEntity();
        } else {
            return;
        }

        Entity damager = event.getDamager();

        if (damager instanceof Player) {
            attacker = (Player) damager;
        }
        else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker != null && victim != null && !attacker.equals(victim)) {
            // Track this as the most recent damage source
            lastDamageSource.put(victim.getUniqueId(), attacker.getUniqueId());
            lastDamageTime.put(victim.getUniqueId(), System.currentTimeMillis());

            // Combat tag both players
            combatManager.tagPlayer(attacker, victim);
            combatManager.tagPlayer(victim, attacker);

            // Perform cleanup of stale records
            cleanupStaleDamageRecords();
        }
    }

    private void cleanupStaleDamageRecords() {
        long currentTime = System.currentTimeMillis();
        lastDamageTime.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > DAMAGE_RECORD_CLEANUP_THRESHOLD);

        // Also clean up damage sources for players that don't have a timestamp anymore
        lastDamageSource.keySet().removeIf(uuid -> !lastDamageTime.containsKey(uuid));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            playerLoggedOutInCombat.put(player.getUniqueId(), true);

            // Punish the player for combat logging
            combatManager.punishCombatLogout(player);

        } else {
            playerLoggedOutInCombat.put(player.getUniqueId(), false);
        }
    }

    // Add a listener for PlayerKickEvent to track admin kicks
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            // Check if exempt_admin_kick is enabled and this was an admin kick
            if (plugin.getConfig().getBoolean("combat.exempt_admin_kick", true)) {

                // Don't punish, just remove from combat
                Player opponent = combatManager.getCombatOpponent(player);
                combatManager.removeFromCombatSilently(player);

                if (opponent != null) {
                    combatManager.removeFromCombat(opponent);
                }
            } else {
                // Regular kick, treat as combat logout
                Player opponent = combatManager.getCombatOpponent(player);
                playerLoggedOutInCombat.put(player.getUniqueId(), true);

                // Punish for combat logging
                combatManager.punishCombatLogout(player);

                if (opponent != null && opponent.isOnline()) {
                    giveKillRewards(opponent, player);
                    plugin.getDeathAnimationManager().performDeathAnimation(player, opponent);
                } else {
                    plugin.getDeathAnimationManager().performDeathAnimation(player, null);
                }

                combatManager.removeFromCombatSilently(player);
                if (opponent != null) {
                    combatManager.removeFromCombat(opponent);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        UUID victimId = victim.getUniqueId();

        // If player directly killed by another player
        if (killer != null && !killer.equals(victim)) {
            // Execute kill reward commands
            giveKillRewards(killer, victim);

            // Perform death animation
            plugin.getDeathAnimationManager().performDeathAnimation(victim, killer);

            // Remove from combat
            combatManager.removeFromCombat(victim);
            combatManager.removeFromCombat(killer);
        }
        // If player died by other causes but was in combat
        else if (combatManager.isInCombat(victim)) {
            Player opponent = combatManager.getCombatOpponent(victim);

            // Check if we have an opponent or a recent damage source
            if (opponent != null && opponent.isOnline()) {
                // Give rewards to the combat opponent
                giveKillRewards(opponent, victim);
                plugin.getDeathAnimationManager().performDeathAnimation(victim, opponent);
            } else if (lastDamageSource.containsKey(victimId)) {
                // Try to get the last player who damaged this player
                UUID lastAttackerUuid = lastDamageSource.get(victimId);
                Player lastAttacker = plugin.getServer().getPlayer(lastAttackerUuid);

                if (lastAttacker != null && lastAttacker.isOnline() && !lastAttacker.equals(victim)) {
                    giveKillRewards(lastAttacker, victim);
                    plugin.getDeathAnimationManager().performDeathAnimation(victim, lastAttacker);
                } else {
                    // No valid attacker found
                    plugin.getDeathAnimationManager().performDeathAnimation(victim, null);
                }
            } else {
                // No attacker information available
                plugin.getDeathAnimationManager().performDeathAnimation(victim, null);
            }

            // Clean up combat state
            combatManager.removeFromCombat(victim);
            if (opponent != null) {
                combatManager.removeFromCombat(opponent);
            }

            // Clean up damage tracking
            lastDamageSource.remove(victimId);
            lastDamageTime.remove(victimId);
        } else {
            // Player died outside of combat
            plugin.getDeathAnimationManager().performDeathAnimation(victim, null);

            // Clean up any stale damage tracking
            lastDamageSource.remove(victimId);
            lastDamageTime.remove(victimId);
        }
    }

    private void giveKillRewards(Player killer, Player victim) {
        if (killer == null || victim == null) {
            return;
        }

        // Prevent self-kill rewards
        if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        if (!plugin.getConfig().getBoolean("kill_rewards.enabled", false)) {
            return;
        }

        // Check if on cooldown
        if (combatManager.isKillRewardOnCooldown(killer, victim)) {
            // If on cooldown, either skip silently or notify the killer
            if (plugin.getConfig().getBoolean("kill_rewards.cooldown.notify", false)) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("killer", killer.getName());
                placeholders.put("victim", victim.getName());
                placeholders.put("days", String.valueOf(combatManager.getRemainingKillRewardCooldown(killer, victim)));
                plugin.getMessageService().sendMessage(killer, "kill_reward_on_cooldown", placeholders);
            }
            return;
        }

        // Execute reward commands
        List<String> commands = plugin.getConfig().getStringList("kill_rewards.commands");
        for (String command : commands) {
            // Replace placeholders in command
            String finalCommand = command
                    .replace("%killer%", killer.getName())
                    .replace("%victim%", victim.getName());

            // Execute the command as the console with try-catch to handle potential errors
            try {
                plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(),
                        finalCommand
                );
            } catch (Exception e) {
                // Log the error
                plugin.getLogger().warning("Failed to execute kill reward command: " + finalCommand);
                plugin.getLogger().warning("Error: " + e.getMessage());
            }
        }

        // Set the cooldown after giving rewards
        combatManager.setKillRewardCooldown(killer, victim);

        // Notify the killer about rewards
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("killer", killer.getName());
        placeholders.put("victim", victim.getName());
        plugin.getMessageService().sendMessage(killer, "kill_reward_received", placeholders);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerLoggedOutInCombat.containsKey(playerUUID)) {
            if (playerLoggedOutInCombat.get(playerUUID)) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                plugin.getMessageService().sendMessage(player, "player_died_combat_logout", placeholders);
            }
            // Clean up the map to prevent memory leaks
            playerLoggedOutInCombat.remove(playerUUID);
        }

        // Clean up any stale damage records for this player
        lastDamageSource.remove(playerUUID);
        lastDamageTime.remove(playerUUID);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            String command = event.getMessage().split(" ")[0].toLowerCase().substring(1);
            List<String> blockedCommands = plugin.getConfig().getStringList("combat.blocked_commands");

            for (String blockedCmd : blockedCommands) {
                if (command.equalsIgnoreCase(blockedCmd) ||
                        (blockedCmd.endsWith("*") && command.startsWith(blockedCmd.substring(0, blockedCmd.length() - 1)))) {
                    event.setCancelled(true);

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", player.getName());
                    placeholders.put("command", command);
                    placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
                    plugin.getMessageService().sendMessage(player, "command_blocked_in_combat", placeholders);

                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // If player is trying to enable flight
        // Check if they're allowed to fly in combat
        if (!plugin.getCombatManager().canFlyInCombat(player)) {
            event.setCancelled(true);
        }
    }

    // Method to clean up any lingering data when the plugin disables
    public void shutdown() {
        playerLoggedOutInCombat.clear();
        lastDamageSource.clear();
        lastDamageTime.clear();
    }
}