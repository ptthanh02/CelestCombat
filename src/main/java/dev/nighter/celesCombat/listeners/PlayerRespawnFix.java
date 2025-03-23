package dev.nighter.celesCombat.listeners;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * This class ensures that players who died from combat logging
 * can properly respawn when they join back.
 */
public class PlayerRespawnFix implements Listener {

    private final CelesCombat plugin;

    public PlayerRespawnFix(CelesCombat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is dead (in respawn screen)
        if (player.isDead()) {
            plugin.getLogger().info("Player " + player.getName() + " joined while dead, scheduling respawn");

            if (player.isOnline() && player.isDead()) {
                try {
                    player.spigot().respawn();
                    plugin.getLogger().info("Successfully respawned " + player.getName() + " after join");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to auto-respawn player: " + e.getMessage());
                }
            }
        }
    }
}