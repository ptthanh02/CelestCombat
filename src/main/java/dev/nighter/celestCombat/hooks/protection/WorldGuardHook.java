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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class WorldGuardHook implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final long MESSAGE_COOLDOWN = 2000; // 2 seconds cooldown between messages

    // Cache for recently checked locations
    private final Map<ChunkCoordinate, Boolean> pvpStatusCache = new HashMap<>();
    private final Map<ChunkCoordinate, Boolean> regionExistsCache = new HashMap<>();
    private final long CACHE_EXPIRY = TimeUnit.SECONDS.toMillis(30); // 30 seconds cache TTL
    private long lastCacheCleanup = System.currentTimeMillis();

    // For barrier feature
    private final Map<UUID, Set<Location>> activeBarriers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBarrierTime = new ConcurrentHashMap<>();
    private final long BARRIER_COOLDOWN = 2000; // 2 seconds cooldown between barriers
    private final int BARRIER_DURATION_TICKS;
    private final int BARRIER_HEIGHT;
    private final int BARRIER_WIDTH;
    private final Map<UUID, SafezoneApproachTracker> safezoneApproachTrackers = new ConcurrentHashMap<>();

    private static class SafezoneApproachTracker {
        private int consecutiveAttempts = 0;
        private long lastAttemptTime = 0;
        private static final long RESET_INTERVAL = 5000; // 5 seconds
        private static final int MAX_ATTEMPTS = 5; // Maximum consecutive attempts before forcing back

        public boolean shouldPreventEntry() {
            return consecutiveAttempts >= MAX_ATTEMPTS;
        }

        public void incrementAttempts(long currentTime) {
            // Reset attempts if too much time has passed
            if (currentTime - lastAttemptTime > RESET_INTERVAL) {
                consecutiveAttempts = 1;
            } else {
                consecutiveAttempts++;
            }
            lastAttemptTime = currentTime;
        }

        public void reset() {
            consecutiveAttempts = 0;
            lastAttemptTime = 0;
        }
    }

    public WorldGuardHook(CelestCombat plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;

        // Get barrier configuration
        this.BARRIER_DURATION_TICKS = plugin.getConfig().getInt("safezone_barrier.duration", 5 * 20);
        this.BARRIER_HEIGHT = plugin.getConfig().getInt("safezone_barrier.height", 4);
        this.BARRIER_WIDTH = plugin.getConfig().getInt("safezone_barrier.width", 5);

        // Schedule regular cache cleanup
        Scheduler.runTaskTimer(this::cleanupCache, 1200L, 1200L); // Run every minute (20 ticks/sec * 60)
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!CelestCombat.hasWorldGuard) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is in combat
        if (!combatManager.isInCombat(player)) return;

        // Check if player is right-clicking with an Ender Pearl
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack itemInHand = event.getItem();
        if (itemInHand == null || itemInHand.getType() != Material.ENDER_PEARL) return;

        // Calculate predicted Ender Pearl landing location
        Location currentLocation = player.getLocation();
        Location predictedLocation = calculatePredictedLocation(player);

        // Check if predicted location is a safezone
        if (isSafeZone(predictedLocation)) {
            // Cancel the event
            event.setCancelled(true);

            // Send cooldown-protected message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
            plugin.getMessageService().sendMessage(player, "combat_no_pearl_safezone", placeholders);
        }
    }

    /**
     * Calculates the predicted location of an Ender Pearl based on player's view direction
     */
    private Location calculatePredictedLocation(Player player) {
        // Ender Pearl typical travel distance is around 20 blocks
        final double ENDER_PEARL_DISTANCE = 20.0;

        // Get player's view direction
        Vector direction = player.getLocation().getDirection().normalize();

        // Calculate predicted location
        Location predictedLocation = player.getLocation().clone().add(direction.multiply(ENDER_PEARL_DISTANCE));

        return predictedLocation;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!CelestCombat.hasWorldGuard) {
            return;
        }

        Player player = event.getPlayer();

        // Skip event if player is not in combat
        if (!combatManager.isInCombat(player)) {
            // Reset safezone approach tracker when not in combat
            safezoneApproachTrackers.remove(player.getUniqueId());
            return;
        }

        // Only process if the player has moved to a new block (optimization)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Check if player is approaching a safezone
        Location to = event.getTo();
        long currentTime = System.currentTimeMillis();
        UUID playerUUID = player.getUniqueId();

        if (isApproachingSafeZone(player, to)) {
            // Get or create tracker for this player
            SafezoneApproachTracker tracker = safezoneApproachTrackers
                    .computeIfAbsent(playerUUID, k -> new SafezoneApproachTracker());

            // Increment approach attempts
            tracker.incrementAttempts(currentTime);

            // Decide how to handle the approach
            if (tracker.shouldPreventEntry()) {
                // Force player far back if too many consecutive attempts
                Location forceBackLocation = getForcedBackLocation(event.getFrom(), to);

                // Teleport player far back
                player.teleportAsync(forceBackLocation);

                // Send warning message
                sendCooldownMessage(player, "combat_multiple_safezone_attempts");

                // Reset the tracker
                tracker.reset();
            } else {
                // Create barrier and cancel movement
                createBarrier(player, to);
                event.setCancelled(true);

                // Nudge player back slightly to prevent bouncing against barrier
                Vector direction = to.toVector().subtract(event.getFrom().toVector()).normalize().multiply(-1.5);
                Location safeLocation = event.getFrom().clone().add(direction);
                player.teleportAsync(safeLocation);

                // Send message
                sendCooldownMessage(player, "combat_no_safezone_entry");
            }
        }
    }

    /**
     * Calculates a location to force the player back if they repeatedly try to enter a safezone
     */
    private Location getForcedBackLocation(Location from, Location currentTo) {
        // Calculate a direction away from the safezone
        Vector direction = from.toVector().subtract(currentTo.toVector()).normalize();

        // Move back a significant distance (10 blocks)
        Location forceBackLocation = from.clone().add(direction.multiply(6));

        // Ensure the forced back location is safe (not in a block)
        Location safeLocation = findSafeLocation(forceBackLocation);
        return safeLocation != null ? safeLocation : from;
    }

    /**
     * Finds a safe location to teleport the player to
     */
    private Location findSafeLocation(Location location) {
        // Check 10 blocks up and down for a safe spot
        for (int y = -5; y <= 5; y++) {
            Location checkLoc = location.clone().add(0, y, 0);
            Block feet = checkLoc.getBlock();
            Block head = checkLoc.clone().add(0, 1, 0).getBlock();

            // Check if the location is safe (air blocks at feet and head level)
            if (feet.getType() == Material.AIR && head.getType() == Material.AIR) {
                return checkLoc;
            }
        }
        return null;
    }

    /**
     * Checks if a player is approaching a safezone
     */
    private boolean isApproachingSafeZone(Player player, Location location) {
        // Check if the location they're moving to is a safezone
        if (isSafeZone(location)) {
            return true;
        }

        // Also check a few blocks ahead in the direction they're facing
        Vector direction = player.getLocation().getDirection().normalize();
        Location checkAhead = location.clone().add(direction);

        return isSafeZone(checkAhead);
    }

    /**
     * Creates a temporary barrier of red glass panes to prevent entry
     */
    private void createBarrier(Player player, Location location) {
        UUID playerUUID = player.getUniqueId();

        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        if (lastBarrierTime.containsKey(playerUUID) &&
                currentTime - lastBarrierTime.get(playerUUID) < BARRIER_COOLDOWN) {
            return;
        }

        // Update cooldown
        lastBarrierTime.put(playerUUID, currentTime);

        // Find the direction vector pointing toward the safezone
        Vector direction = findSafezoneDirection(player, location);
        if (direction == null) {
            // Fallback to player's view direction if we couldn't determine safezone direction
            direction = player.getLocation().getDirection().normalize();
        }

        // Create set to track blocks in this barrier
        Set<Location> barrierBlocks = new HashSet<>();

        // Using player's direction to determine wall orientation
        Vector right = new Vector(direction.getZ(), 0, -direction.getX()).normalize();

        // Create wall perpendicular to the player's direction
        Location center = location.clone();

        // Adjust the center position to be just in front of the player
        center.add(direction.clone().multiply(1.5));

        for (int y = 0; y < BARRIER_HEIGHT; y++) {
            for (int i = -BARRIER_WIDTH / 2; i <= BARRIER_WIDTH / 2; i++) {
                Location blockLoc = center.clone().add(right.clone().multiply(i)).add(0, y, 0);
                Block block = blockLoc.getBlock();

                // Only replace air and non-solid blocks
                if (block.getType() == Material.AIR || !block.getType().isSolid()) {
                    // Save original state
                    barrierBlocks.add(blockLoc.clone());

                    // Change block safely using Bukkit scheduler
                    Scheduler.runLocationTask(blockLoc, () -> {
                        block.setType(Material.RED_STAINED_GLASS_PANE);
                    });
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

    /**
     * Removes a specific barrier for a player
     */
    private void removeBarrier(UUID playerUUID, Set<Location> barrierBlocks) {
        // Remove each block in the barrier
        for (Location loc : barrierBlocks) {
            Scheduler.runLocationTask(loc, () -> {
                Block block = loc.getBlock();
                if (block.getType() == Material.RED_STAINED_GLASS_PANE) {
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

    /**
     * Finds the direction vector pointing toward the nearest safezone
     */
    private Vector findSafezoneDirection(Player player, Location currentLocation) {
        // Check in 8 directions around the player to find the closest safezone
        Vector[] directions = {
                new Vector(1, 0, 0),
                new Vector(-1, 0, 0),
                new Vector(0, 0, 1),
                new Vector(0, 0, -1),
                new Vector(1, 0, 1).normalize(),
                new Vector(1, 0, -1).normalize(),
                new Vector(-1, 0, 1).normalize(),
                new Vector(-1, 0, -1).normalize()
        };

        // How far to check in each direction
        final int CHECK_DISTANCE = 5;

        for (int distance = 1; distance <= CHECK_DISTANCE; distance++) {
            for (Vector dir : directions) {
                Location checkLoc = currentLocation.clone().add(dir.clone().multiply(distance));
                if (isSafeZone(checkLoc)) {
                    return dir;
                }
            }
        }

        return null;
    }

    /**
     * Determines if a location is in a safe zone (a region where PvP is disabled)
     * @param location The location to check
     * @return true if the location is in a region AND PvP is disabled there
     */
    private boolean isSafeZone(Location location) {
        return isInAnyRegion(location) && !isPvPAllowed(location);
    }

    private boolean isPvPAllowed(Location location) {
        if (location == null) return true;

        // Check cache first
        ChunkCoordinate coord = new ChunkCoordinate(location);
        Boolean cachedResult = pvpStatusCache.get(coord);
        if (cachedResult != null) {
            return cachedResult;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location worldGuardLoc = BukkitAdapter.adapt(location);

            // Check the PvP flag for the location
            boolean result = query.testState(worldGuardLoc, null, Flags.PVP);

            // Cache the result
            pvpStatusCache.put(coord, result);

            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard PvP flag: " + e.getMessage());
            return true; // Default to allowing PvP if there's an error
        }
    }

    private boolean isInAnyRegion(Location location) {
        if (location == null) return false;

        // Check cache first
        ChunkCoordinate coord = new ChunkCoordinate(location);
        Boolean cachedResult = regionExistsCache.get(coord);
        if (cachedResult != null) {
            return cachedResult;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return false;

            BlockVector3 pos = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(pos);

            boolean result = !regions.getRegions().isEmpty();

            // Cache the result
            regionExistsCache.put(coord, result);

            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard regions: " + e.getMessage());
            return false;
        }
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

    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > CACHE_EXPIRY) {
            pvpStatusCache.clear();
            regionExistsCache.clear();
            lastCacheCleanup = currentTime;
        }
    }

    /**
     * Removes all active barriers for a player (use when they leave combat)
     */
    public void removeAllBarriers(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (activeBarriers.containsKey(playerUUID)) {
            Set<Location> barriers = activeBarriers.get(playerUUID);
            for (Location loc : barriers) {
                Block block = loc.getBlock();
                if (block.getType() == Material.RED_STAINED_GLASS_PANE) {
                    block.setType(Material.AIR);
                }
            }
            activeBarriers.remove(playerUUID);
        }
    }

    // Class for caching location's PvP status by chunk
    private static class ChunkCoordinate {
        private final String world;
        private final int chunkX;
        private final int chunkZ;

        public ChunkCoordinate(Location location) {
            this.world = location.getWorld().getName();
            this.chunkX = location.getBlockX() >> 4; // Divide by 16
            this.chunkZ = location.getBlockZ() >> 4; // Divide by 16
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoordinate that = (ChunkCoordinate) o;
            return chunkX == that.chunkX &&
                    chunkZ == that.chunkZ &&
                    world.equals(that.world);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            return result;
        }
    }
}