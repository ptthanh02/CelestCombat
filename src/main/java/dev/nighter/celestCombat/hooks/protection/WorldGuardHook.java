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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGuardHook implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;

    // Message cooldown optimization
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final long MESSAGE_COOLDOWN = 2000; // 2 seconds cooldown between messages

    // Track ender pearls from combat players
    private final Map<UUID, UUID> combatPlayerPearls = new ConcurrentHashMap<>();
    // Track player locations when they throw ender pearls with TTL
    private final Map<UUID, PearlLocationData> pearlThrowLocations = new ConcurrentHashMap<>();

    // Visual barrier system
    private final Map<UUID, Set<Location>> playerBarriers = new ConcurrentHashMap<>();
    private final Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    private final Map<Location, Set<UUID>> barrierViewers = new ConcurrentHashMap<>();

    // Configuration
    private int barrierDetectionRadius;
    private int barrierHeight;
    private Material barrierMaterial;

    // Cache for performance optimization
    private final Map<String, Boolean> safeZoneCache = new ConcurrentHashMap<>();
    private long lastCacheClean = System.currentTimeMillis();
    private static final long CACHE_CLEAN_INTERVAL = 30000; // 30 seconds
    private static final int MAX_CACHE_SIZE = 1000;

    // Pearl data with TTL for memory management
    private static class PearlLocationData {
        final Location location;
        final long timestamp;

        PearlLocationData(Location location) {
            this.location = location.clone();
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 60000; // 60 seconds TTL
        }
    }

    public WorldGuardHook(CelestCombat plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;

        // Load configuration
        reloadConfig();

        // Start cleanup task
        startCleanupTask();
    }

    public void reloadConfig() {
        // Reload configuration
        this.barrierDetectionRadius = plugin.getConfig().getInt("safezone_protection.barrier_detection_radius", 5);
        this.barrierHeight = plugin.getConfig().getInt("safezone_protection.barrier_height", 3);
        this.barrierMaterial = loadBarrierMaterial();

        // Clear cache when config reloads
        safeZoneCache.clear();
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

            // Store the player's location when they throw the pearl with TTL
            pearlThrowLocations.put(player.getUniqueId(), new PearlLocationData(player.getLocation()));
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
        UUID playerUUID = combatPlayerPearls.remove(projectileId); // Remove while getting

        if (playerUUID == null) {
            return;
        }

        // Get the player who threw the pearl
        Player player = plugin.getServer().getPlayer(playerUUID);

        // Check pearl destination by using the future teleport location
        Location teleportDestination = calculateTeleportDestination(event, projectile);

        // Check if teleport destination is in a safezone
        if (isSafeZone(teleportDestination)) {
            // Cancel the teleportation event
            event.setCancelled(true);

            // Handle player teleport back
            handlePearlTeleportBack(player, playerUUID);
        }

        // Always clean up the saved location
        pearlThrowLocations.remove(playerUUID);
    }

    private Location calculateTeleportDestination(ProjectileHitEvent event, Projectile projectile) {
        Location teleportDestination;

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

        return teleportDestination;
    }

    private void handlePearlTeleportBack(Player player, UUID playerUUID) {
        // Check if player is online and get their saved location
        if (player != null && player.isOnline()) {
            PearlLocationData pearlData = pearlThrowLocations.get(playerUUID);
            if (pearlData != null && !pearlData.isExpired()) {
                Location originalLocation = pearlData.location;

                // Delay the teleport to ensure it happens after the pearl event is processed
                Scheduler.runTaskLater(() -> {
                    player.teleportAsync(originalLocation).thenAccept(success -> {
                        if (success) {
                            sendCooldownMessage(player, "combat_no_pearl_safezone");
                        } else {
                            handleFailedTeleport(player, originalLocation);
                        }
                    });
                }, 1L);
            } else {
                // No valid location saved, just send message
                sendCooldownMessage(player, "combat_no_pearl_safezone");
            }
        }
    }

    private void handleFailedTeleport(Player player, Location originalLocation) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!CelestCombat.hasWorldGuard) {
            return;
        }

        Player player = event.getPlayer();

        // Skip event if player is not in combat
        if (!combatManager.isInCombat(player)) {
            // Remove any barriers for this player
            removePlayerBarriers(player);
            return;
        }

        // Only process if the player has moved to a new block (optimization)
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        // Check if player is crossing between PVP and no-PVP zones
        boolean fromSafe = isSafeZone(from);
        boolean toSafe = isSafeZone(to);

        // If trying to enter a safezone while in combat
        if (!fromSafe && toSafe) {;
            // Push player back
            pushPlayerBack(player, from, to);

            // Send message
            sendCooldownMessage(player, "combat_no_safezone_entry");
        }

        // Update visual barriers regardless of movement result
        updatePlayerBarriers(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!combatManager.isInCombat(player)) {
            removePlayerBarriers(player);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Location blockLoc = event.getClickedBlock().getLocation();

        // Check if this block is a barrier for this player
        Set<Location> playerBarrierSet = playerBarriers.get(player.getUniqueId());
        if (playerBarrierSet != null && containsBlockLocation(playerBarrierSet, blockLoc)) {
            // Cancel the interaction to prevent visual glitches
            event.setCancelled(true);

            // Refresh the barrier block for the player to fix any visual issues
            Scheduler.runTaskLater(() -> refreshBarrierBlock(blockLoc, player), 1L);
        }
    }

    /**
     * Helper method to check if a set of locations contains a block location
     * This normalizes locations to block coordinates for proper comparison
     */
    private boolean containsBlockLocation(Set<Location> locations, Location blockLoc) {
        Location normalizedBlockLoc = normalizeToBlockLocation(blockLoc);

        for (Location loc : locations) {
            Location normalizedLoc = normalizeToBlockLocation(loc);
            if (normalizedLoc.equals(normalizedBlockLoc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes a location to block coordinates (integer coordinates)
     */
    private Location normalizeToBlockLocation(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    private void refreshBarrierBlock(Location loc, Player player) {
        // Normalize the location to ensure proper lookup
        Location normalizedLoc = normalizeToBlockLocation(loc);

        Set<UUID> viewers = barrierViewers.get(normalizedLoc);
        if (viewers != null && viewers.contains(player.getUniqueId())) {
            // Re-send the barrier block to fix any visual issues
            player.sendBlockChange(normalizedLoc, barrierMaterial.createBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location blockLoc = normalizeToBlockLocation(event.getBlock().getLocation());

        // Check if this block is part of a barrier system
        if (originalBlocks.containsKey(blockLoc)) {
            // Don't allow breaking barrier blocks
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Clean up player's barriers when they disconnect
        removePlayerBarriers(player);

        // Clean up other player-specific data
        lastMessageTime.remove(playerUUID);
        pearlThrowLocations.remove(playerUUID);

        // Clean up any projectiles this player may have thrown
        combatPlayerPearls.entrySet().removeIf(entry -> entry.getValue().equals(playerUUID));
    }

    private void pushPlayerBack(Player player, Location from, Location to) {
        // Calculate direction vector from 'to' back to 'from'
        Vector direction = from.toVector().subtract(to.toVector()).normalize();

        // Amplify the push slightly (adjustable force)
        direction.multiply(0.6);

        // Create a new location to teleport the player to
        // This is based on their current location plus a small push back
        Location pushLocation = player.getLocation().clone();
        pushLocation.add(direction);

        // Ensure we're not pushing them into a block
        pushLocation.setY(getSafeY(pushLocation));

        // Maintain the original look direction
        pushLocation.setPitch(player.getLocation().getPitch());
        pushLocation.setYaw(player.getLocation().getYaw());

        // Apply some knockback effect to make it feel more natural
        player.setVelocity(direction);
    }

    private double getSafeY(Location loc) {
        // Get the block at the location
        Block block = loc.getBlock();

        // If the block is not solid, we're good
        if (!block.getType().isSolid()) {
            return loc.getY();
        }

        // Otherwise, look for safe space above
        for (int y = 1; y <= 2; y++) {
            Block above = block.getRelative(0, y, 0);
            if (!above.getType().isSolid()) {
                return loc.getBlockY() + y;
            }
        }

        // Look for safe space below if above wasn't safe
        for (int y = 1; y <= 2; y++) {
            Block below = block.getRelative(0, -y, 0);
            if (!below.getType().isSolid() &&
                    !below.getRelative(0, -1, 0).getType().isSolid()) {
                return loc.getBlockY() - y;
            }
        }

        // If all else fails, return original Y
        return loc.getY();
    }

    /**
     * Updates visual barriers for a combat player based on their current location
     */
    private void updatePlayerBarriers(Player player) {
        if (!combatManager.isInCombat(player)) {
            removePlayerBarriers(player);
            return;
        }

        Set<Location> newBarriers = findNearbyBarrierLocations(player.getLocation());
        Set<Location> currentBarriers = playerBarriers.getOrDefault(player.getUniqueId(), new HashSet<>());

        // Remove barriers that are no longer needed
        Set<Location> toRemove = new HashSet<>(currentBarriers);
        toRemove.removeAll(newBarriers);
        for (Location loc : toRemove) {
            removeBarrierBlock(loc, player);
        }

        // Add new barriers
        Set<Location> toAdd = new HashSet<>(newBarriers);
        toAdd.removeAll(currentBarriers);
        for (Location loc : toAdd) {
            createBarrierBlock(loc, player);
        }

        // Update player's barrier set
        if (newBarriers.isEmpty()) {
            playerBarriers.remove(player.getUniqueId());
        } else {
            playerBarriers.put(player.getUniqueId(), newBarriers);
        }
    }

    /**
     * Finds locations where barriers should be placed near the player
     */
    private Set<Location> findNearbyBarrierLocations(Location playerLoc) {
        Set<Location> barrierLocations = new HashSet<>();

        // Search in a radius around the player for safezone borders
        int radius = barrierDetectionRadius;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= barrierHeight; y++) {
                    Location checkLoc = playerLoc.clone().add(x, y, z);

                    // Skip if too far from player (circular radius)
                    if (checkLoc.distance(playerLoc) > radius) {
                        continue;
                    }

                    // Check if this location is on the border between safe and unsafe zones
                    if (isBorderLocation(checkLoc)) {
                        // Normalize the location to block coordinates
                        barrierLocations.add(normalizeToBlockLocation(checkLoc));
                    }
                }
            }
        }

        return barrierLocations;
    }

    /**
     * Checks if a location is on the border between safe and unsafe zones
     */
    private boolean isBorderLocation(Location loc) {
        if (!isSafeZone(loc)) {
            return false;
        }

        // Check adjacent blocks to see if any are unsafe zones
        int[][] directions = {{1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}};

        for (int[] dir : directions) {
            Location adjacent = loc.clone().add(dir[0], dir[1], dir[2]);
            if (!isSafeZone(adjacent)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a barrier block at the specified location for the player
     */
    private void createBarrierBlock(Location loc, Player player) {
        // Normalize location to block coordinates
        Location normalizedLoc = normalizeToBlockLocation(loc);
        Block block = normalizedLoc.getBlock();

        // Only create barrier if the block is air or replaceable
        if (block.getType() != Material.AIR && block.getType().isSolid()) {
            return;
        }

        // Store original block type
        originalBlocks.put(normalizedLoc, block.getType());

        // Add player to viewers of this barrier
        barrierViewers.computeIfAbsent(normalizedLoc, k -> new HashSet<>()).add(player.getUniqueId());

        // Send block change to player (configurable barrier material)
        player.sendBlockChange(normalizedLoc, barrierMaterial.createBlockData());
    }

    /**
     * Removes a barrier block at the specified location for the player
     */
    private void removeBarrierBlock(Location loc, Player player) {
        // Normalize location to block coordinates
        Location normalizedLoc = normalizeToBlockLocation(loc);

        Set<UUID> viewers = barrierViewers.get(normalizedLoc);
        if (viewers != null) {
            viewers.remove(player.getUniqueId());

            // If no more viewers, clean up completely
            if (viewers.isEmpty()) {
                barrierViewers.remove(normalizedLoc);
                Material originalType = originalBlocks.remove(normalizedLoc);
                if (originalType != null) {
                    // Restore original block for the player
                    player.sendBlockChange(normalizedLoc, originalType.createBlockData());
                }
            } else {
                // Just restore original block for this player
                Material originalType = originalBlocks.get(normalizedLoc);
                if (originalType != null) {
                    player.sendBlockChange(normalizedLoc, originalType.createBlockData());
                }
            }
        }
    }

    /**
     * Removes all barriers for a specific player
     */
    private void removePlayerBarriers(Player player) {
        Set<Location> barriers = playerBarriers.remove(player.getUniqueId());
        if (barriers != null) {
            for (Location loc : barriers) {
                removeBarrierBlock(loc, player);
            }
        }
    }

    /**
     * Enhanced cleanup task with better memory management
     */
    private void startCleanupTask() {
        Scheduler.runTaskTimerAsync(() -> {
            long currentTime = System.currentTimeMillis();

            // Clean up barriers for players no longer in combat
            cleanupPlayerBarriers();

            // Clean up expired pearl locations
            cleanupExpiredPearlLocations(currentTime);

            // Clean up message cooldowns
            cleanupMessageCooldowns(currentTime);

            // Clean up safezone cache periodically
            cleanupSafeZoneCache(currentTime);

        }, 100L, 100L); // Run every 5 seconds
    }

    private void cleanupPlayerBarriers() {
        Iterator<Map.Entry<UUID, Set<Location>>> iterator = playerBarriers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<Location>> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            Player player = plugin.getServer().getPlayer(playerUUID);

            if (player == null || !player.isOnline() || !combatManager.isInCombat(player)) {
                // Remove barriers for this player
                Set<Location> barriers = entry.getValue();
                if (player != null && player.isOnline()) {
                    for (Location loc : barriers) {
                        removeBarrierBlock(loc, player);
                    }
                } else {
                    // Player is offline, just clean up data
                    for (Location loc : barriers) {
                        cleanupOfflinePlayerBarrier(loc, playerUUID);
                    }
                }
                iterator.remove();
            }
        }
    }

    private void cleanupOfflinePlayerBarrier(Location loc, UUID playerUUID) {
        Location normalizedLoc = normalizeToBlockLocation(loc);
        Set<UUID> viewers = barrierViewers.get(normalizedLoc);
        if (viewers != null) {
            viewers.remove(playerUUID);
            if (viewers.isEmpty()) {
                barrierViewers.remove(normalizedLoc);
                originalBlocks.remove(normalizedLoc);
            }
        }
    }

    private void cleanupExpiredPearlLocations(long currentTime) {
        pearlThrowLocations.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void cleanupMessageCooldowns(long currentTime) {
        lastMessageTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > MESSAGE_COOLDOWN * 10); // Keep for 10x cooldown time
    }

    private void cleanupSafeZoneCache(long currentTime) {
        if (currentTime - lastCacheClean > CACHE_CLEAN_INTERVAL) {
            // Clean cache if it's too large
            if (safeZoneCache.size() > MAX_CACHE_SIZE) {
                safeZoneCache.clear();
            }
            lastCacheClean = currentTime;
        }
    }

    private boolean isSafeZone(Location location) {
        return isInAnyRegion(location) && !isPvPAllowed(location);
    }

    private boolean isPvPAllowed(Location location) {
        if (location == null) return true;

        // Create cache key
        String cacheKey = location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();

        // Check cache first
        Boolean cached = safeZoneCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location worldGuardLoc = BukkitAdapter.adapt(location);

            // Check the PvP flag for the location directly
            boolean pvpAllowed = query.testState(worldGuardLoc, null, Flags.PVP);

            // Cache the result
            safeZoneCache.put(cacheKey, pvpAllowed);

            return pvpAllowed;
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
        Long lastTime = lastMessageTime.get(playerUUID);
        if (lastTime != null && currentTime - lastTime < MESSAGE_COOLDOWN) {
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
     * Loads and validates the barrier material from config
     */
    private Material loadBarrierMaterial() {
        String materialName = plugin.getConfig().getString("safezone_protection.barrier_material", "RED_STAINED_GLASS");

        try {
            Material material = Material.valueOf(materialName.toUpperCase());

            // Validate that the material is a valid block material
            if (!material.isBlock()) {
                plugin.getLogger().warning("Barrier material '" + materialName + "' is not a valid block material. Using RED_STAINED_GLASS instead.");
                return Material.RED_STAINED_GLASS;
            }

            plugin.debug("Using barrier material: " + material.name() + " for safezone protection.");
            return material;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid barrier material '" + materialName + "' in config. Using RED_STAINED_GLASS instead.");
            plugin.getLogger().warning("Valid materials can be found at: https://jd.papermc.io/paper/1.21.5/org/bukkit/Material.html");
            return Material.RED_STAINED_GLASS;
        }
    }

    /**
     * Cleanup method to be called when plugin is disabled
     */
    public void cleanup() {
        combatPlayerPearls.clear();
        pearlThrowLocations.clear();
        playerBarriers.clear();
        originalBlocks.clear();
        barrierViewers.clear();
        lastMessageTime.clear();
        safeZoneCache.clear();
    }
}