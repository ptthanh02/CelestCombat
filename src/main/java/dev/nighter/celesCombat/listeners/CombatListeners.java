package dev.nighter.celesCombat.listeners;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
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
    private final CelesCombat plugin;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            Player opponent = combatManager.getCombatOpponent(player);
            playerLoggedOutInCombat.put(player.getUniqueId(), true);
            combatManager.punishCombatLogout(player);
            combatManager.removeFromCombat(player);
            combatManager.removeFromCombat(opponent);
        } else {
            playerLoggedOutInCombat.put(player.getUniqueId(), false);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            combatManager.removeFromCombat(killer);
        }
        combatManager.removeFromCombat(victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player) && !player.hasPermission("celescombat.bypass.commands")) {
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