package dev.nighter.celestCombat.hooks.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.combat.CombatManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGuardHookBarrierV1 implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final long MESSAGE_COOLDOWN = 2000; // 2 seconds cooldown between messages

    // For barrier feature
    private final Map<UUID, Set<Location>> activeBarriers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBarrierTime = new ConcurrentHashMap<>();
    private final long BARRIER_COOLDOWN = 1000; // 1 second cooldown between barriers
    private long BARRIER_DURATION_TICKS;
    private int BARRIER_HEIGHT;
    private int BARRIER_WIDTH;
    private Material BARRIER_MATERIAL;

    // Track ender pearls from combat players
    private final Map<UUID, UUID> combatPlayerPearls = new ConcurrentHashMap<>();
    // Track player locations when they throw ender pearls
    private final Map<UUID, Location> pearlThrowLocations = new ConcurrentHashMap<>();

    public WorldGuardHookBarrierV1(CelestCombat plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;

        // Get barrier configuration
        this.BARRIER_DURATION_TICKS = plugin.getTimeFromConfig("safezone_barrier.duration", "3s");
        this.BARRIER_HEIGHT = plugin.getConfig().getInt("safezone_barrier.height", 8);
        this.BARRIER_WIDTH = plugin.getConfig().getInt("safezone_barrier.width", 5);
        this.BARRIER_MATERIAL = Material.getMaterial(plugin.getConfig().getString("safezone_barrier.block", "RED_STAINED_GLASS_PANE").toUpperCase());
    }

    public void reloadConfig() {
        // Reload barrier configuration
        this.BARRIER_DURATION_TICKS = plugin.getTimeFromConfig("safezone_barrier.duration", "3s");
        this.BARRIER_HEIGHT = plugin.getConfig().getInt("safezone_barrier.height", 8);
        this.BARRIER_WIDTH = plugin.getConfig().getInt("safezone_barrier.width", 5);
        this.BARRIER_MATERIAL = Material.getMaterial(plugin.getConfig().getString("safezone_barrier.block", "RED_STAINED_GLASS_PANE").toUpperCase());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        // Only interested in ender pearls
        if (!(projectile instanceof EnderPearl)) {
            return;
        }

        // Check if the shooter is a player in combat
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player)) {
            return;
        }

        Player player = (Player) source;

        // Track pearls from players in combat
        if (combatManager.isInCombat(player)) {
            // Store projectile ID with player ID
            combatPlayerPearls.put(projectile.getUniqueId(), player.getUniqueId());

            // Store the player's location when they throw the pearl
            pearlThrowLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        // Only interested in ender pearls
        if (!(projectile instanceof EnderPearl)) {
            return;
        }

        // Check if this is a pearl we're tracking (from combat player)
        UUID projectileId = projectile.getUniqueId();
        if (!combatPlayerPearls.containsKey(projectileId)) {
            return;
        }

        // Get the player who threw the pearl
        UUID playerUUID = combatPlayerPearls.get(projectileId);
        Player player = plugin.getServer().getPlayer(playerUUID);

        // Clean up tracking for this projectile
        combatPlayerPearls.remove(projectileId);

        // Check pearl destination by using the future teleport location
        Location teleportDestination = null;

        // If hit block, calculate where player would land
        if (event.getHitBlock() != null) {
            Block hitPosition = event.getHitBlock();
            teleportDestination = new Location(
                    projectile.getWorld(),
                    hitPosition.getX(),
                    hitPosition.getY(),
                    hitPosition.getZ()
            );

            // Adjust to where player would stand (account for hitting ceiling/wall)
            if (event.getHitBlockFace() != null) {
                teleportDestination.add(event.getHitBlockFace().getDirection().multiply(0.5));
            }
        }
        // If hit entity or other, just use pearl location
        else {
            teleportDestination = projectile.getLocation();
        }

        // Check if teleport destination is in a safezone
        if (isSafeZone(teleportDestination)) {
            // Cancel the teleportation event
            event.setCancelled(true);

            // Check if player is online and get their saved location
            if (player != null && player.isOnline() && pearlThrowLocations.containsKey(playerUUID)) {
                Location originalLocation = pearlThrowLocations.get(playerUUID);

                // Delay the teleport to ensure it happens after the pearl event is processed
                Scheduler.runTaskLater(() -> {
                    player.teleportAsync(originalLocation).thenAccept(success -> {
                        if (success) {
                            sendCooldownMessage(player, "combat_no_pearl_safezone");
                        } else {
                            // If teleport fails, try to find a safe location
                            Location safeLocation = findSafeLocation(originalLocation);
                            if (safeLocation != null) {
                                player.teleportAsync(safeLocation);
                                sendCooldownMessage(player, "combat_no_pearl_safezone");
                            } else {
                                // Last resort - kill the player
                                player.setHealth(0);
                                plugin.getLogger().warning("Killed player " + player.getName() + " as no safe location could be found");
                                sendCooldownMessage(player, "combat_killed_no_safe_location");
                            }
                        }
                    });
                }, 1L); // Just a 1 tick delay, but enough to ensure proper order
            }
            // If no location saved but player is online, just send message
            else if (player != null && player.isOnline()) {
                sendCooldownMessage(player, "combat_no_pearl_safezone");
            }
        }

        // Always clean up the saved location
        pearlThrowLocations.remove(playerUUID);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!CelestCombat.hasWorldGuard) {
            return;
        }

        Player player = event.getPlayer();

        // Skip event if player is not in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Only process if the player has moved to a new block (optimization)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Check if player is crossing between PVP and no-PVP zones
        Location from = event.getFrom();
        Location to = event.getTo();

        boolean fromSafe = isSafeZone(from);
        boolean toSafe = isSafeZone(to);

        // If trying to enter a safezone while in combat
        if (!fromSafe && toSafe) {
            // Calculate border points and create barrier
            List<Location> borderPoints = findRegionBorderPoints(from, to);
            if (!borderPoints.isEmpty()) {
                createBarrierAtPoints(player, borderPoints);
            }

            // Cancel the movement without pushing the player back
            event.setCancelled(true);

            // Send message
            sendCooldownMessage(player, "combat_no_safezone_entry");
        }
    }

    private List<Location> findRegionBorderPoints(Location from, Location to) {
        List<Location> borderPoints = new ArrayList<>();

        // Get direction vector from from to to
        Vector direction = to.toVector().subtract(from.toVector()).normalize();

        // Parameters for our ray casting
        double step = 0.25; // Step size in blocks
        double maxDistance = from.distance(to) + 2.0; // Slightly farther than the distance

        Location current = from.clone();
        boolean lastPointSafe = isSafeZone(current);

        // Cast ray from from to beyond to
        for (double distance = 0; distance <= maxDistance; distance += step) {
            current = from.clone().add(direction.clone().multiply(distance));
            boolean currentPointSafe = isSafeZone(current);

            // If we cross a boundary, add this point
            if (currentPointSafe != lastPointSafe) {
                borderPoints.add(current.clone());
                lastPointSafe = currentPointSafe;
            }
        }

        return borderPoints;
    }

    private void createBarrierAtPoints(Player player, List<Location> borderPoints) {
        UUID playerUUID = player.getUniqueId();

        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        if (lastBarrierTime.containsKey(playerUUID) &&
                currentTime - lastBarrierTime.get(playerUUID) < BARRIER_COOLDOWN) {
            return;
        }

        // Update cooldown
        lastBarrierTime.put(playerUUID, currentTime);

        // Create set to track blocks in this barrier
        Set<Location> barrierBlocks = new HashSet<>();

        for (Location borderPoint : borderPoints) {
            // For each border point, we need to create part of the barrier

            // Get direction from player to border point
            Vector dirToPlayer = player.getLocation().toVector().subtract(borderPoint.toVector()).normalize();

            // Create vector perpendicular to the direction and Y-axis (for width)
            Vector perpendicular = new Vector(-dirToPlayer.getZ(), 0, dirToPlayer.getX()).normalize();

            // Create wall at the border perpendicular to player's approach
            for (int y = 0; y < BARRIER_HEIGHT; y++) {
                for (int i = -BARRIER_WIDTH / 2; i <= BARRIER_WIDTH / 2; i++) {
                    Location blockLoc = borderPoint.clone().add(perpendicular.clone().multiply(i)).add(0, y, 0);
                    Block block = blockLoc.getBlock();

                    // Only replace air and non-solid blocks
                    if (block.getType() == Material.AIR || !block.getType().isSolid()) {
                        // Save original state
                        barrierBlocks.add(blockLoc.clone());

                        // Change block safely using Bukkit scheduler
                        Scheduler.runLocationTask(blockLoc, () -> {
                            block.setType(BARRIER_MATERIAL);
                        });
                    }
                }
            }
        }

        // Store this barrier for the player
        activeBarriers.computeIfAbsent(playerUUID, k -> new HashSet<>()).addAll(barrierBlocks);

        // Schedule barrier removal
        Scheduler.runTaskLater(() -> {
            removeBarrier(playerUUID, barrierBlocks);
        }, BARRIER_DURATION_TICKS);
    }

    private void removeBarrier(UUID playerUUID, Set<Location> barrierBlocks) {
        // Remove each block in the barrier
        for (Location loc : barrierBlocks) {
            Scheduler.runLocationTask(loc, () -> {
                Block block = loc.getBlock();
                if (block.getType() == BARRIER_MATERIAL) {
                    block.setType(Material.AIR);
                }
            });
        }

        // Remove from tracking
        if (activeBarriers.containsKey(playerUUID)) {
            activeBarriers.get(playerUUID).removeAll(barrierBlocks);
            if (activeBarriers.get(playerUUID).isEmpty()) {
                activeBarriers.remove(playerUUID);
            }
        }
    }

    private boolean isSafeZone(Location location) {
        return isInAnyRegion(location) && !isPvPAllowed(location);
    }

    private boolean isPvPAllowed(Location location) {
        if (location == null) return true;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location worldGuardLoc = BukkitAdapter.adapt(location);

            // Check the PvP flag for the location directly (no caching)
            return query.testState(worldGuardLoc, null, Flags.PVP);
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard PvP flag: " + e.getMessage());
            return true; // Default to allowing PvP if there's an error
        }
    }

    private boolean isInAnyRegion(Location location) {
        if (location == null) return false;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return false;

            BlockVector3 pos = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(pos);

            return !regions.getRegions().isEmpty();
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard regions: " + e.getMessage());
            return false;
        }
    }

    private Location findSafeLocation(Location location) {
        if (location == null) return null;

        // Create a wider search pattern in all directions
        // Search in a 10x10x10 cube around the player's location
        int searchRadius = 10;

        // Try the original location first
        if (isLocationSafe(location)) {
            return location;
        }

        // Try locations directly above and below first (most common solutions)
        for (int y = 1; y <= searchRadius; y++) {
            // Check above
            Location above = location.clone().add(0, y, 0);
            if (isLocationSafe(above)) {
                return above;
            }

            // Check below
            Location below = location.clone().add(0, -y, 0);
            if (isLocationSafe(below)) {
                return below;
            }
        }

        // Expand search in a spiral pattern
        for (int distance = 1; distance <= searchRadius; distance++) {
            // Check in all directions at this distance
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    // Skip middle area as we've already checked it
                    if (Math.abs(x) < distance && Math.abs(z) < distance) continue;

                    // Check at different y levels
                    for (int y = -distance; y <= distance; y++) {
                        Location checkLoc = location.clone().add(x, y, z);
                        if (isLocationSafe(checkLoc)) {
                            return checkLoc;
                        }
                    }
                }
            }
        }

        // No safe location found
        return null;
    }

    private boolean isLocationSafe(Location location) {
        if (location == null) return false;

        // Check if location is in a safezone
        if (isSafeZone(location)) return false;

        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        Block ground = location.clone().add(0, -1, 0).getBlock();

        // Location is safe if feet and head are air, and ground is solid
        return (feet.getType() == Material.AIR || !feet.getType().isSolid())
                && (head.getType() == Material.AIR || !head.getType().isSolid())
                && ground.getType().isSolid();
    }

    private void sendCooldownMessage(Player player, String messageKey) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Check if message is on cooldown
        if (lastMessageTime.containsKey(playerUUID) &&
                currentTime - lastMessageTime.get(playerUUID) < MESSAGE_COOLDOWN) {
            return;
        }

        // Update last message time
        lastMessageTime.put(playerUUID, currentTime);

        // Send message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
        plugin.getMessageService().sendMessage(player, messageKey, placeholders);
    }

    public void removeAllBarriers(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (activeBarriers.containsKey(playerUUID)) {
            Set<Location> barriers = activeBarriers.get(playerUUID);
            for (Location loc : barriers) {
                Block block = loc.getBlock();
                if (block.getType() == BARRIER_MATERIAL) {
                    block.setType(Material.AIR);
                }
            }
            activeBarriers.remove(playerUUID);
        }
    }
}