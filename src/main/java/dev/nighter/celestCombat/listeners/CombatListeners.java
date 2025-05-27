package dev.nighter.celestCombat.listeners;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.combat.CombatManager;
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

    public CombatListeners(CelestCombat plugin) {
        this.plugin = plugin;
        this.combatManager = plugin.getCombatManager();
    }

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
                    plugin.getKillRewardManager().giveKillReward(opponent, player);
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
            // Execute kill reward commands using KillRewardManager
            plugin.getKillRewardManager().giveKillReward(killer, victim);

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
                plugin.getKillRewardManager().giveKillReward(opponent, victim);
                plugin.getDeathAnimationManager().performDeathAnimation(victim, opponent);
            } else if (lastDamageSource.containsKey(victimId)) {
                // Try to get the last player who damaged this player
                UUID lastAttackerUuid = lastDamageSource.get(victimId);
                Player lastAttacker = plugin.getServer().getPlayer(lastAttackerUuid);

                if (lastAttacker != null && lastAttacker.isOnline() && !lastAttacker.equals(victim)) {
                    plugin.getKillRewardManager().giveKillReward(lastAttacker, victim);
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

            // Get command blocking mode from config
            String blockMode = plugin.getConfig().getString("combat.command_block_mode", "whitelist").toLowerCase();

            // Determine if the command should be blocked based on the mode
            boolean shouldBlock = false;

            if ("blacklist".equalsIgnoreCase(blockMode)) {
                // Blacklist mode - block commands in the list
                List<String> blockedCommands = plugin.getConfig().getStringList("combat.blocked_commands");

                for (String blockedCmd : blockedCommands) {
                    if (command.equalsIgnoreCase(blockedCmd) ||
                            (blockedCmd.endsWith("*") && command.startsWith(blockedCmd.substring(0, blockedCmd.length() - 1)))) {
                        shouldBlock = true;
                        break;
                    }
                }
            } else {
                // Whitelist mode - allow only commands in the list
                List<String> allowedCommands = plugin.getConfig().getStringList("combat.allowed_commands");
                shouldBlock = true; // Block by default

                for (String allowedCmd : allowedCommands) {
                    if (command.equalsIgnoreCase(allowedCmd) ||
                            (allowedCmd.endsWith("*") && command.startsWith(allowedCmd.substring(0, allowedCmd.length() - 1)))) {
                        shouldBlock = false; // Command is allowed
                        break;
                    }
                }
            }

            // Block the command if necessary
            if (shouldBlock) {
                event.setCancelled(true);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("command", command);
                placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
                plugin.getMessageService().sendMessage(player, "command_blocked_in_combat", placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // If player is trying to enable flight
        if (event.isFlying() && combatManager.shouldDisableFlight(player)) {
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