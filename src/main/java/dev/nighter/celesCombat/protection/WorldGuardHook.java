package dev.nighter.celesCombat.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class WorldGuardHook implements Listener {
    private final CelesCombat plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<Player, Long> lastCheckTime = new WeakHashMap<>();
    private final long MESSAGE_COOLDOWN = 2000; // 2 seconds cooldown between messages
    private final long CHECK_COOLDOWN = 500; // 500ms between region checks

    // Cache for recently checked locations
    private final Map<ChunkCoordinate, Boolean> pvpStatusCache = new HashMap<>();
    private final long CACHE_EXPIRY = TimeUnit.SECONDS.toMillis(30); // 30 seconds cache TTL
    private long lastCacheCleanup = System.currentTimeMillis();

    public WorldGuardHook(CelesCombat plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;

        // Schedule regular cache cleanup
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupCache, 1200L, 1200L); // Run every minute (20 ticks/sec * 60)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Early return conditions for better performance
        if (!CelesCombat.hasWorldGuard) {
            return;
        }

        // Only process if player changed block position
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !hasChangedBlock(from, to)) {
            return;
        }

        // Check safety zone entry
        checkSafeZoneEntry(event.getPlayer(), from, to, event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!CelesCombat.hasWorldGuard) {
            return;
        }

        Player player = event.getPlayer();

        // Skip event if player is not in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // Prevent teleportation to safe zones
        if (to != null && !isPvPAllowed(to) && isPvPAllowed(from)) {
            // Get the teleport cause
            TeleportCause cause = event.getCause();

            // Cancel the teleport
            event.setCancelled(true);

            // Send message with cause context
            sendTeleportBlockedMessage(player, cause);
        }
    }

    private void checkSafeZoneEntry(Player player, Location from, Location to, Cancellable cancellable) {
        // Check if player is in combat
        if (!combatManager.isInCombat(player)) {
            return;
        }

        // Apply cooldown to reduce frequency of checks
        long currentTime = System.currentTimeMillis();
        Long lastCheck = lastCheckTime.get(player);
        if (lastCheck != null && currentTime - lastCheck < CHECK_COOLDOWN) {
            return;
        }
        lastCheckTime.put(player, currentTime);

        // Check if player is trying to enter a PvP disabled region
        if (!isPvPAllowed(to) && isPvPAllowed(from)) {
            // Cancel the movement
            cancellable.setCancelled(true);

            // Push player back slightly
            Vector direction = from.toVector().subtract(to.toVector()).normalize();
            player.setVelocity(direction.multiply(0.3));

            // Send message to player (with cooldown)
            sendCooldownMessage(player, "combat_no_safe_zone");
        }
    }

    private boolean hasChangedBlock(Location from, Location to) {
        if (from == null || to == null) return false;

        // Faster integer comparison
        return from.getBlockX() != to.getBlockX() ||
                from.getBlockY() != to.getBlockY() ||
                from.getBlockZ() != to.getBlockZ();
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

    private void sendTeleportBlockedMessage(Player player, TeleportCause cause) {
        String messageKey = "combat_no_safe_zone";

        // Use specific message for different teleport causes
        if (cause == TeleportCause.ENDER_PEARL) {
            messageKey = "combat_no_pearl_safezone";
        } else if (cause == TeleportCause.COMMAND) {
            messageKey = "combat_no_command_teleport";
        }

        sendCooldownMessage(player, messageKey);
    }

    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > CACHE_EXPIRY) {
            pvpStatusCache.clear();
            lastCacheCleanup = currentTime;
        }
    }

    // Interface to handle event cancellation
    private interface Cancellable {
        void setCancelled(boolean cancel);
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