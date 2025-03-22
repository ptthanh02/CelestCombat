package dev.nighter.celesCombat.listeners;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
import dev.nighter.celesCombat.combat.ExplosionTracker;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class CPVPListeners implements Listener {
    private final CelesCombat plugin;
    private final CombatManager combatManager;

    // Track players who recently placed end crystals
    private final Map<UUID, Long> recentCrystalPlacers = new HashMap<>();
    private final Map<UUID, Long> recentAnchorUsers = new HashMap<>();
    private final Map<UUID, Long> recentTNTPlacers = new HashMap<>();

    private static final long TRACKING_DURATION = 10000; // 10 seconds in milliseconds
    private static final double EXPLOSION_COMBAT_RADIUS = 6.0; // 6 blocks radius

    /**
     * Handles placing of End Crystals
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Player player = event.getPlayer();

        if (item.getType() == Material.END_CRYSTAL) {
            // Track this player as having placed a crystal recently
            recentCrystalPlacers.put(player.getUniqueId(), System.currentTimeMillis());
        } else if (item.getType() == Material.RESPAWN_ANCHOR) {
            // Track this player as having placed an anchor recently
            recentAnchorUsers.put(player.getUniqueId(), System.currentTimeMillis());
        } else if (item.getType() == Material.TNT) {
            // Track this player as having placed TNT recently
            recentTNTPlacers.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Handles interactions with Respawn Anchors
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnchorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if player is charging/using a respawn anchor
        if (block.getType() == Material.RESPAWN_ANCHOR) {
            // Check if the player is trying to detonate the anchor
            if (item == null || item.getType() == Material.GLOWSTONE) {
                // Track this player as having interacted with an anchor recently
                recentAnchorUsers.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }
    }

    /**
     * Handles explosion of End Crystals and other entities
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // Handle end crystal explosions
        if (entity.getType() == EntityType.END_CRYSTAL) {
            Player crystalPlacer = findRecentPlacer(recentCrystalPlacers);
            if (crystalPlacer != null) {
                tagNearbyPlayers(crystalPlacer, entity.getLocation(), EXPLOSION_COMBAT_RADIUS);

                // Register this player as the explosion causer for all nearby players
                for (Player nearbyPlayer : entity.getWorld().getPlayers()) {
                    if (nearbyPlayer.equals(crystalPlacer)) continue;
                    if (nearbyPlayer.getLocation().distance(entity.getLocation()) <= EXPLOSION_COMBAT_RADIUS) {
                        ExplosionTracker.trackExplosion(nearbyPlayer.getUniqueId(), crystalPlacer.getUniqueId());
                    }
                }
            }
        }
        // Handle TNT explosions
        else if (entity.getType() == EntityType.TNT) {
            if (entity.hasMetadata("placer")) {
                String placerUUID = entity.getMetadata("placer").get(0).asString();
                try {
                    Player tntPlacer = plugin.getServer().getPlayer(UUID.fromString(placerUUID));
                    if (tntPlacer != null && tntPlacer.isOnline()) {
                        tagNearbyPlayers(tntPlacer, entity.getLocation(), EXPLOSION_COMBAT_RADIUS);

                        // Register this player as the explosion causer for all nearby players
                        for (Player nearbyPlayer : entity.getWorld().getPlayers()) {
                            if (nearbyPlayer.equals(tntPlacer)) continue;
                            if (nearbyPlayer.getLocation().distance(entity.getLocation()) <= EXPLOSION_COMBAT_RADIUS) {
                                ExplosionTracker.trackExplosion(nearbyPlayer.getUniqueId(), tntPlacer.getUniqueId());
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    /**
     * Handle manual TNT ignition
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTNTPrime(ExplosionPrimeEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof TNTPrimed) {
            Player tntPlacer = findRecentPlacer(recentTNTPlacers);
            if (tntPlacer != null) {
                entity.setMetadata("placer", new FixedMetadataValue(plugin, tntPlacer.getUniqueId().toString()));
            }
        }
    }

    /**
     * Handles explosions from Respawn Anchors
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        Block block = event.getBlock();

        // Check if it's a respawn anchor explosion
        if (block.getType() == Material.RESPAWN_ANCHOR || block.getType() == Material.AIR) {
            Player anchorUser = findRecentPlacer(recentAnchorUsers);
            if (anchorUser != null) {
                tagNearbyPlayers(anchorUser, block.getLocation(), EXPLOSION_COMBAT_RADIUS);

                // Register this player as the explosion causer for all nearby players
                for (Player nearbyPlayer : block.getWorld().getPlayers()) {
                    if (nearbyPlayer.equals(anchorUser)) continue;
                    if (nearbyPlayer.getLocation().distance(block.getLocation()) <= EXPLOSION_COMBAT_RADIUS) {
                        ExplosionTracker.trackExplosion(nearbyPlayer.getUniqueId(), anchorUser.getUniqueId());
                    }
                }
            }
        }
    }

    /**
     * Find the most recent player who placed a specific object
     */
    private Player findRecentPlacer(Map<UUID, Long> recentPlacers) {
        Player mostRecentPlacer = null;
        long mostRecentTime = 0;
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : recentPlacers.entrySet()) {
            UUID playerUUID = entry.getKey();
            long placeTime = entry.getValue();

            // Check if the time is still within tracking duration
            if (currentTime - placeTime <= TRACKING_DURATION) {
                if (placeTime > mostRecentTime) {
                    Player player = plugin.getServer().getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        mostRecentPlacer = player;
                        mostRecentTime = placeTime;
                    }
                }
            }
        }

        return mostRecentPlacer;
    }

    /**
     * Tag all players near an explosion point caused by a specific player
     */
    private void tagNearbyPlayers(Player source, org.bukkit.Location location, double radius) {
        for (Player nearby : location.getWorld().getPlayers()) {
            // Skip the source player
            if (nearby.equals(source)) continue;

            // Check if player is within radius
            if (nearby.getLocation().distance(location) <= radius) {
                // Player is near the explosion, tag them
                combatManager.tagPlayer(nearby, source);
                combatManager.tagPlayer(source, nearby);
            }
        }
    }

    /**
     * Clean up old tracking entries
     */
    public void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();

        recentCrystalPlacers.entrySet().removeIf(entry -> currentTime - entry.getValue() > TRACKING_DURATION);
        recentAnchorUsers.entrySet().removeIf(entry -> currentTime - entry.getValue() > TRACKING_DURATION);
        recentTNTPlacers.entrySet().removeIf(entry -> currentTime - entry.getValue() > TRACKING_DURATION);
    }
}