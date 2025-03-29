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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class CombatListeners implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Boolean> playerLoggedOutInCombat = new ConcurrentHashMap<>();

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
            combatManager.tagPlayer(attacker, victim);
            combatManager.tagPlayer(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerLoggedOutInCombat.containsKey(playerUUID) && playerLoggedOutInCombat.get(playerUUID)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            plugin.getMessageService().sendMessage(player, "player_died_combat_logout", placeholders);
            playerLoggedOutInCombat.remove(playerUUID);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            Player opponent = combatManager.getCombatOpponent(player);
            playerLoggedOutInCombat.put(player.getUniqueId(), true);

            // Execute kill reward commands for the opponent if they're still online
            if (opponent != null && opponent.isOnline()) {
                giveKillRewards(opponent, player);
            }

            combatManager.punishCombatLogout(player);
            combatManager.removeFromCombat(player);
            if (opponent != null) {
                combatManager.removeFromCombat(opponent);
            }
        } else {
            playerLoggedOutInCombat.put(player.getUniqueId(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            // Execute kill reward commands
            giveKillRewards(killer, victim);

            // Perform death animation
            plugin.getDeathAnimationManager().performDeathAnimation(victim, killer);
            combatManager.removeFromCombat(victim);

            // Remove from combat
            combatManager.removeFromCombat(killer);
        } else {
            // Perform death animation
            plugin.getDeathAnimationManager().performDeathAnimation(victim, null);
            combatManager.removeFromCombat(victim);
        }
    }

    private void giveKillRewards(Player killer, Player victim) {
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
}