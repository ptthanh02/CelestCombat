package dev.nighter.celesCombat.listeners;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
import dev.nighter.celesCombat.combat.ExplosionTracker;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class CombatListeners implements Listener {
    private final CelesCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Boolean> playerLoggedOutInCombat = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        Player victim = null;

        // Check if victim is a player
        if (event.getEntity() instanceof Player) {
            victim = (Player) event.getEntity();
        } else {
            return;
        }

        // Get the attacker based on different damage sources
        Entity damager = event.getDamager();

        // Direct player attack
        if (damager instanceof Player) {
            attacker = (Player) damager;
        }
        // Projectile attack (arrows, snowballs, etc.)
        else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        // Explosion damage (including TNT, crystal, anchor, etc.)
        else if (event.getCause() == EntityDamageByEntityEvent.DamageCause.ENTITY_EXPLOSION ||
                event.getCause() == EntityDamageByEntityEvent.DamageCause.BLOCK_EXPLOSION) {
            // Try to find the player responsible for the explosion using our tracker
            UUID lastExplosionCauserUUID = ExplosionTracker.getLastExplosionCauser(victim.getUniqueId());
            if (lastExplosionCauserUUID != null) {
                Player explosionCauser = Bukkit.getPlayer(lastExplosionCauserUUID);
                if (explosionCauser != null && explosionCauser.isOnline()) {
                    attacker = explosionCauser;
                }
            }
        }

        // If both attacker and victim are players
        if (attacker != null && victim != null && !attacker.equals(victim)) {
            // Put both players in combat
            combatManager.tagPlayer(attacker, victim);
            combatManager.tagPlayer(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if player is in combat
        if (combatManager.isInCombat(player)) {
            playerLoggedOutInCombat.put(player.getUniqueId(), true);

            // Get the opponent before killing the player
            Player opponent = combatManager.getCombatOpponent(player);

            // Kill the player
            player.setHealth(0);

            // Notify the opponent specifically if they're online
            if (opponent != null && opponent.isOnline()) {
                Map<String, String> opponentPlaceholders = new HashMap<>();
                opponentPlaceholders.put("player", player.getName());
                opponentPlaceholders.put("opponent", opponent.getName());
                plugin.getMessageService().sendMessage(opponent, "opponent_combat_logged", opponentPlaceholders);
            }

            // Remove from combat after they're dead
            combatManager.removeFromCombat(player);
        } else {
            playerLoggedOutInCombat.put(player.getUniqueId(), false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Check if player previously logged out in combat
        if (playerLoggedOutInCombat.containsKey(playerUUID) && playerLoggedOutInCombat.get(playerUUID)) {
            // Send message to player
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            plugin.getMessageService().sendMessage(player, "player_died_combat_logout", placeholders);

            // Remove from map
            playerLoggedOutInCombat.remove(playerUUID);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Check if the killer is eligible for combat reset
        if (killer != null) {
            combatManager.handlePlayerKill(killer, victim);
        }

        // Remove victim from combat after handling the killer
        combatManager.removeFromCombat(victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Check if player is in combat and command usage is restricted
        if (combatManager.isInCombat(player) && !player.hasPermission("celescombat.bypass.commands")) {
            String command = event.getMessage().split(" ")[0].toLowerCase().substring(1);
            List<String> blockedCommands = plugin.getConfig().getStringList("combat.blocked_commands");

            // Check if the command is blocked
            for (String blockedCmd : blockedCommands) {
                if (command.equalsIgnoreCase(blockedCmd) ||
                        (blockedCmd.endsWith("*") && command.startsWith(blockedCmd.substring(0, blockedCmd.length() - 1)))) {

                    // Cancel the command
                    event.setCancelled(true);

                    // Send message to player
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
}