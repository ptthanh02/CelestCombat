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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGuardHook implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final long MESSAGE_COOLDOWN = 2000; // 2 seconds cooldown between messages

    // Track ender pearls from combat players
    private final Map<UUID, UUID> combatPlayerPearls = new ConcurrentHashMap<>();
    // Track player locations when they throw ender pearls
    private final Map<UUID, Location> pearlThrowLocations = new ConcurrentHashMap<>();
    // Track when pearls were thrown (for cleanup)
    private final Map<UUID, Long> pearlThrowTimes = new ConcurrentHashMap<>();

    // Cleanup task
    private Scheduler.Task cleanupTask;
    private static final long CLEANUP_INTERVAL = 60 * 20; // Run cleanup every minute (in ticks)
    private static final long PEARL_LIFETIME = 30 * 1000; // Consider pearls older than 30 seconds as expired

    public WorldGuardHook(CelestCombat plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;

        // Schedule regular cleanup
        startCleanupTask();
    }

    /**
     * Starts the periodic cleanup task
     */
    private void startCleanupTask() {
        // Cancel any existing task first
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        // Schedule a new cleanup task
        cleanupTask = Scheduler.runTaskTimerAsync(() -> {
            cleanupExpiredData();
            plugin.debug("WorldGuardHook cleanup task executed");
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    /**
     * Cleans up expired entries from all tracking maps
     */
    public void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();

        // Clean message cooldowns (keep entries for 5x the cooldown period)
        lastMessageTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > MESSAGE_COOLDOWN * 5);

        // Clean pearl throw locations for players who are no longer in combat
        for (Iterator<Map.Entry<UUID, Location>> it = pearlThrowLocations.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Location> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !combatManager.isInCombat(player)) {
                it.remove();
            }
        }

        // Clean pearl throw times for expired pearls
        Set<UUID> expiredPearls = new HashSet<>();
        for (Map.Entry<UUID, Long> entry : pearlThrowTimes.entrySet()) {
            if (currentTime - entry.getValue() > PEARL_LIFETIME) {
                expiredPearls.add(entry.getKey());
            }
        }

        // Remove expired pearls from all tracking maps
        for (UUID pearlId : expiredPearls) {
            pearlThrowTimes.remove(pearlId);
            combatPlayerPearls.remove(pearlId);
        }

        plugin.debug("Cleaned up " + expiredPearls.size() + " expired pearls. Remaining tracked pearls: " + combatPlayerPearls.size());
    }

    /**
     * Reload configuration
     */
    public void reloadConfig() {
        // Configuration would be reloaded here when needed
    }

    /**
     * Handle player quit events to clean up data
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Clean up all maps
        lastMessageTime.remove(playerUUID);
        pearlThrowLocations.remove(playerUUID);

        // Clean any pearls belonging to this player
        combatPlayerPearls.entrySet().removeIf(entry -> entry.getValue().equals(playerUUID));
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
            UUID projectileId = projectile.getUniqueId();
            UUID playerUUID = player.getUniqueId();

            // Store projectile ID with player ID
            combatPlayerPearls.put(projectileId, playerUUID);

            // Store the player's location when they throw the pearl
            pearlThrowLocations.put(playerUUID, player.getLocation().clone());

            // Store the time when the pearl was thrown (for cleanup)
            pearlThrowTimes.put(projectileId, System.currentTimeMillis());

            // plugin.debug("Tracking combat pearl " + projectileId + " from player " + player.getName());
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
        pearlThrowTimes.remove(projectileId);

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

                // Remove throw location as we're handling this pearl now
                pearlThrowLocations.remove(playerUUID);

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
        } else {
            // If successful teleport to non-safezone, clean up location
            pearlThrowLocations.remove(playerUUID);
        }
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
            // Cancel the movement
            event.setCancelled(true);

            // Send message
            sendCooldownMessage(player, "combat_no_safezone_entry");
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

    /**
     * Shutdown method to clean up resources
     */
    public void shutdown() {
        // Cancel any scheduled tasks
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        // Clear all maps
        lastMessageTime.clear();
        combatPlayerPearls.clear();
        pearlThrowLocations.clear();
        pearlThrowTimes.clear();

        plugin.debug("WorldGuardHook shutdown complete");
    }
}