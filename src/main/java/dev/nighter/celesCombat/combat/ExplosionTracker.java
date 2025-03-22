package dev.nighter.celesCombat.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExplosionTracker {
    private static final Map<UUID, UUID> lastPlayerExplosionCauser = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> explosionTimestamps = new ConcurrentHashMap<>();

    // How long to track an explosion's causer (in milliseconds)
    private static final long EXPLOSION_TRACKING_DURATION = 5000; // 5 seconds

    /**
     * Registers a player as the causer of an explosion that may damage a victim
     * @param potentialVictim The player who might be damaged by this explosion
     * @param causer The player who caused the explosion
     */
    public static void trackExplosion(UUID potentialVictim, UUID causer) {
        lastPlayerExplosionCauser.put(potentialVictim, causer);
        explosionTimestamps.put(potentialVictim, System.currentTimeMillis());
    }

    /**
     * Gets the last player who caused an explosion that damaged this victim
     * @param victim The player who was damaged
     * @return UUID of the player who caused the explosion, or null if not found/expired
     */
    public static UUID getLastExplosionCauser(UUID victim) {
        if (!lastPlayerExplosionCauser.containsKey(victim) || !explosionTimestamps.containsKey(victim)) {
            return null;
        }

        long timestamp = explosionTimestamps.get(victim);
        long currentTime = System.currentTimeMillis();

        // Check if the explosion tracking is still valid
        if (currentTime - timestamp > EXPLOSION_TRACKING_DURATION) {
            // Expired - remove and return null
            lastPlayerExplosionCauser.remove(victim);
            explosionTimestamps.remove(victim);
            return null;
        }

        return lastPlayerExplosionCauser.get(victim);
    }

    /**
     * Clears all tracked explosions
     */
    public static void clearAll() {
        lastPlayerExplosionCauser.clear();
        explosionTimestamps.clear();
    }

    /**
     * Schedules a cleanup task to remove expired explosion tracking
     * @param plugin The plugin instance
     */
    public static void scheduleCleanup(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Create a copy to avoid concurrent modification
            Map<UUID, Long> timestampsCopy = new HashMap<>(explosionTimestamps);

            for (Map.Entry<UUID, Long> entry : timestampsCopy.entrySet()) {
                UUID victim = entry.getKey();
                long timestamp = entry.getValue();

                if (currentTime - timestamp > EXPLOSION_TRACKING_DURATION) {
                    lastPlayerExplosionCauser.remove(victim);
                    explosionTimestamps.remove(victim);
                }
            }
        }, 20L, 20L); // Run every second
    }
}