package dev.nighter.celestCombat.protection;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NewbieProtectionManager {
    private final CelestCombat plugin;
    private final File protectionFile;
    private FileConfiguration protectionConfig;

    // Protection storage - UUID -> expiration time in milliseconds
    @Getter private final Map<UUID, Long> protectedPlayers = new ConcurrentHashMap<>();

    // Boss bars for countdown display
    private final Map<UUID, BossBar> protectionBossBars = new ConcurrentHashMap<>();

    // Configuration cache
    private boolean enabled;
    private long protectionDurationTicks;
    private long protectionDurationSeconds;
    private boolean useBossBar;
    private boolean useActionBar;
    private String bossBarTitle;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;
    private Map<String, Boolean> worldProtectionSettings = new ConcurrentHashMap<>();
    private boolean protectFromPvP;
    private boolean protectFromMobs;
    private boolean removeOnDamageDealt;

    // Tasks
    private Scheduler.Task updateTask;
    private Scheduler.Task cleanupTask;
    private Scheduler.Task saveTask;

    // Constants
    private static final long UPDATE_INTERVAL = 20L; // 1 second in ticks
    private static final long CLEANUP_INTERVAL = 12000L; // 10 minutes in ticks
    private static final long SAVE_INTERVAL = 6000L; // 5 minutes in ticks

    public NewbieProtectionManager(CelestCombat plugin) {
        this.plugin = plugin;
        this.protectionFile = new File(plugin.getDataFolder(), "newbie_protection_data.yml");

        // Load configuration
        loadConfig();

        // Load protection data
        loadProtectionData();

        // Start background tasks
        startUpdateTask();
        startCleanupTask();
        startAutoSaveTask();
    }

    /**
     * Loads configuration values from the main config
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        this.enabled = config.getBoolean("newbie_protection.enabled", true);
        this.protectionDurationTicks = plugin.getTimeFromConfig("newbie_protection.duration", "10m");
        this.protectionDurationSeconds = protectionDurationTicks / 20;

        this.useBossBar = config.getBoolean("newbie_protection.display.use_bossbar", true);
        this.useActionBar = config.getBoolean("newbie_protection.display.use_actionbar", false);
        this.bossBarTitle = config.getString("newbie_protection.display.bossbar.title", "&#4CAF50PvP Protection: &#FFFFFF%time%");

        // Parse boss bar color
        String colorStr = config.getString("newbie_protection.display.bossbar.color", "GREEN");
        try {
            this.bossBarColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.bossBarColor = BarColor.YELLOW;
            plugin.getLogger().warning("Invalid boss bar color: " + colorStr + ", using GREEN");
        }

        // Parse boss bar style
        String styleStr = config.getString("newbie_protection.display.bossbar.style", "SOLID");
        try {
            this.bossBarStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.bossBarStyle = BarStyle.SOLID;
            plugin.getLogger().warning("Invalid boss bar style: " + styleStr + ", using SOLID");
        }

        this.protectFromPvP = config.getBoolean("newbie_protection.protect_from_pvp", true);
        this.protectFromMobs = config.getBoolean("newbie_protection.protect_from_mobs", false);
        this.removeOnDamageDealt = config.getBoolean("newbie_protection.remove_on_damage_dealt", true);

        loadWorldProtectionSettings();

        plugin.debug("NewbieProtectionManager config loaded - Enabled: " + enabled +
                ", Duration: " + protectionDurationSeconds + "s" +
                ", Boss bar: " + useBossBar +
                ", Action bar: " + useActionBar);
    }

    /**
     * Loads per-world protection settings
     */
    private void loadWorldProtectionSettings() {
        worldProtectionSettings.clear();

        if (plugin.getConfig().isConfigurationSection("newbie_protection.worlds")) {
            for (String worldName : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("newbie_protection.worlds")).getKeys(false)) {
                boolean enabledInWorld = plugin.getConfig().getBoolean("newbie_protection.worlds." + worldName, true);
                worldProtectionSettings.put(worldName, enabledInWorld);
            }
        }

        plugin.debug("Loaded world-specific newbie protection settings: " + worldProtectionSettings);
    }

    /**
     * Loads protection data from the YAML file
     */
    private void loadProtectionData() {
        if (!protectionFile.exists()) {
            try {
                protectionFile.getParentFile().mkdirs();
                protectionFile.createNewFile();
                plugin.getLogger().info("Created new newbie_protection_data.yml file");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create newbie_protection_data.yml: " + e.getMessage());
                return;
            }
        }

        protectionConfig = YamlConfiguration.loadConfiguration(protectionFile);

        // Load protection data from file
        int loadedCount = 0;
        long currentTime = System.currentTimeMillis();

        for (String uuidStr : protectionConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidStr);
                long expirationTime = protectionConfig.getLong(uuidStr);

                // Only load non-expired protections
                if (expirationTime > currentTime) {
                    protectedPlayers.put(playerUUID, expirationTime);
                    loadedCount++;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in protection data: " + uuidStr);
            }
        }

        plugin.getLogger().info("Loaded " + loadedCount + " active newbie protections");
    }

    /**
     * Saves protection data to the YAML file
     */
    public void saveProtectionData() {
        saveProtectionData(false);
    }

    /**
     * Saves protection data to the YAML file
     * @param synchronous if true, saves synchronously (used during shutdown)
     */
    public void saveProtectionData(boolean synchronous) {
        if (protectionConfig == null) {
            protectionConfig = new YamlConfiguration();
        }

        // Clear existing data
        for (String key : protectionConfig.getKeys(false)) {
            protectionConfig.set(key, null);
        }

        // Save current protections
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : protectedPlayers.entrySet()) {
            // Only save non-expired protections
            if (entry.getValue() > currentTime) {
                protectionConfig.set(entry.getKey().toString(), entry.getValue());
            }
        }

        // Save to file
        if (synchronous || !plugin.isEnabled()) {
            // Save synchronously during shutdown or if plugin is disabled
            try {
                protectionConfig.save(protectionFile);
                plugin.debug("Saved newbie protection data to file (synchronous)");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save newbie_protection_data.yml: " + e.getMessage());
            }
        } else {
            // Save asynchronously during normal operation
            Scheduler.runTaskAsync(() -> {
                try {
                    protectionConfig.save(protectionFile);
                    plugin.debug("Saved newbie protection data to file");
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save newbie_protection_data.yml: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Checks if newbie protection is enabled for a specific world
     */
    public boolean isEnabledInWorld(String worldName) {
        if (!enabled) return false;
        return worldProtectionSettings.getOrDefault(worldName, true);
    }

    /**
     * Grants newbie protection to a player
     */
    public void grantProtection(Player player) {
        if (!enabled || player == null) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!isEnabledInWorld(worldName)) {
            plugin.debug("Newbie protection not enabled in world: " + worldName);
            return;
        }

        UUID playerUUID = player.getUniqueId();
        long expirationTime = System.currentTimeMillis() + (protectionDurationSeconds * 1000L);

        protectedPlayers.put(playerUUID, expirationTime);

        // Create boss bar if enabled
        if (useBossBar) {
            createBossBar(player);
        }

        // Send protection granted message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("duration", formatTime(protectionDurationSeconds));
        plugin.getMessageService().sendMessage(player, "newbie_protection_granted", placeholders);

        plugin.debug("Granted newbie protection to " + player.getName() + " until " + new Date(expirationTime));
    }

    /**
     * Checks if a player has newbie protection
     */
    public boolean hasProtection(Player player) {
        if (!enabled || player == null) {
            return false;
        }

        String worldName = player.getWorld().getName();
        if (!isEnabledInWorld(worldName)) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        Long expirationTime = protectedPlayers.get(playerUUID);

        if (expirationTime == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= expirationTime) {
            removeProtection(player, false);
            return false;
        }

        return true;
    }

    /**
     * Removes newbie protection from a player
     */
    public void removeProtection(Player player, boolean sendMessage) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        boolean hadProtection = protectedPlayers.remove(playerUUID) != null;

        if (hadProtection) {
            // Remove boss bar
            BossBar bossBar = protectionBossBars.remove(playerUUID);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            plugin.debug("Removed newbie protection from " + player.getName());
        }
    }

    /**
     * Handles when a protected player takes damage
     */
    public boolean handleDamageReceived(Player player, Player attacker) {
        if (!hasProtection(player)) {
            return false; // Player is not protected
        }

        // Send message to attacker if they're a player
        if (attacker != null && attacker != player) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("attacker", attacker.getName());
            plugin.getMessageService().sendMessage(attacker, "newbie_protection_attack_blocked", placeholders);
        }

        return true; // Block damage
    }

    /**
     * Handles when a protected player deals damage
     */
    public void handleDamageDealt(Player player) {
        if (removeOnDamageDealt && hasProtection(player)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            plugin.getMessageService().sendMessage(player, "newbie_protection_removed_attack", placeholders);

            removeProtection(player, false);
        }
    }

    /**
     * Gets the remaining protection time for a player in seconds
     */
    public long getRemainingTime(Player player) {
        if (!hasProtection(player)) {
            return 0;
        }

        UUID playerUUID = player.getUniqueId();
        Long expirationTime = protectedPlayers.get(playerUUID);

        if (expirationTime == null) {
            return 0;
        }

        long remainingMillis = expirationTime - System.currentTimeMillis();
        return Math.max(0, remainingMillis / 1000);
    }

    /**
     * Creates a boss bar for a protected player
     */
    private void createBossBar(Player player) {
        if (!useBossBar || player == null) return;

        UUID playerUUID = player.getUniqueId();

        // Remove existing boss bar if any
        BossBar existingBar = protectionBossBars.get(playerUUID);
        if (existingBar != null) {
            existingBar.removeAll();
        }

        // Create new boss bar
        String title = bossBarTitle.replace("%time%", formatTime(getRemainingTime(player)));
        title = plugin.getLanguageManager().colorize(title);

        BossBar bossBar = Bukkit.createBossBar(title, bossBarColor, bossBarStyle);
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);

        protectionBossBars.put(playerUUID, bossBar);
    }

    /**
     * Updates boss bar for a protected player
     */
    private void updateBossBar(Player player) {
        if (!useBossBar || player == null) return;

        UUID playerUUID = player.getUniqueId();
        BossBar bossBar = protectionBossBars.get(playerUUID);

        if (bossBar == null) return;

        long remainingTime = getRemainingTime(player);
        if (remainingTime <= 0) {
            bossBar.removeAll();
            protectionBossBars.remove(playerUUID);
            return;
        }

        // Update title
        String title = bossBarTitle.replace("%time%", formatTime(remainingTime));
        title = plugin.getLanguageManager().colorize(title);
        bossBar.setTitle(title);

        // Update progress
        double progress = Math.max(0.0, Math.min(1.0, (double) remainingTime / protectionDurationSeconds));
        bossBar.setProgress(progress);
    }

    /**
     * Sends action bar message to a protected player
     */
    private void sendActionBar(Player player) {
        if (!useActionBar || player == null || !player.isOnline()) return;

        long remainingTime = getRemainingTime(player);
        if (remainingTime <= 0) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("time", formatTime(remainingTime));
        plugin.getMessageService().sendMessage(player, "newbie_protection_actionbar", placeholders);
    }

    /**
     * Formats time in seconds to a readable string
     */
    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    /**
     * Starts the update task for boss bars and action bars
     */
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Scheduler.runTaskTimer(() -> {
            for (UUID playerUUID : new HashSet<>(protectedPlayers.keySet())) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                if (!hasProtection(player)) {
                    continue; // Protection expired, will be cleaned up
                }

                // Update boss bar
                if (useBossBar) {
                    updateBossBar(player);
                }

                // Send action bar
                if (useActionBar) {
                    sendActionBar(player);
                }
            }
        }, 0L, UPDATE_INTERVAL);
    }

    /**
     * Starts the cleanup task to remove expired protections
     */
    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = Scheduler.runTaskTimerAsync(() -> {
            long currentTime = System.currentTimeMillis();
            int removedCount = 0;

            Iterator<Map.Entry<UUID, Long>> iterator = protectedPlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                if (currentTime >= entry.getValue()) {
                    UUID playerUUID = entry.getKey();
                    iterator.remove();

                    // Remove boss bar on main thread
                    Scheduler.runTask(() -> {
                        BossBar bossBar = protectionBossBars.remove(playerUUID);
                        if (bossBar != null) {
                            bossBar.removeAll();
                        }
                    });

                    removedCount++;
                }
            }

            if (removedCount > 0) {
                plugin.debug("Cleaned up " + removedCount + " expired newbie protections");
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    /**
     * Starts the auto-save task
     */
    private void startAutoSaveTask() {
        if (saveTask != null) {
            saveTask.cancel();
        }

        saveTask = Scheduler.runTaskTimerAsync(this::saveProtectionData, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    /**
     * Manually clears all protections for a player
     */
    public void clearPlayerProtection(Player player) {
        if (player == null) return;

        removeProtection(player, false);
        plugin.debug("Manually cleared newbie protection for " + player.getName());
    }

    /**
     * Reloads configuration
     */
    public void reloadConfig() {
        loadConfig();
        plugin.debug("NewbieProtectionManager configuration reloaded");
    }

    /**
     * Handles player join - grants protection to new players
     */
    public void handlePlayerJoin(Player player) {
        if (!enabled || player == null) return;

        // Check if player has played before
        if (player.hasPlayedBefore()) {
            plugin.debug("Player " + player.getName() + " has played before, not granting newbie protection");
            return;
        }

        // Grant protection to new player
        grantProtection(player);
    }

    /**
     * Handles player quit - clean up boss bars
     */
    public void handlePlayerQuit(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        BossBar bossBar = protectionBossBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Gets protection settings
     */
    public boolean shouldProtectFromPvP() {
        return protectFromPvP;
    }

    public boolean shouldProtectFromMobs() {
        return protectFromMobs;
    }

    /**
     * Shutdown method to clean up resources
     */
    public void shutdown() {
        // Cancel tasks
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        // Remove all boss bars
        for (BossBar bossBar : protectionBossBars.values()) {
            bossBar.removeAll();
        }
        protectionBossBars.clear();

        // Save data
        saveProtectionData(true);

        // Clear collections
        protectedPlayers.clear();
    }
}