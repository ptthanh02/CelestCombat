package dev.nighter.celestCombat.combat;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {
    private final CelestCombat plugin;
    @Getter private final Map<UUID, Long> playersInCombat;
    private final Map<UUID, Scheduler.Task> combatTasks;
    private final Map<UUID, UUID> combatOpponents;

    // Single countdown task instead of per-player tasks
    private Scheduler.Task globalCountdownTask;
    private static final long COUNTDOWN_INTERVAL = 20L; // 1 second in ticks

    @Getter private final Map<UUID, Long> enderPearlCooldowns;
    @Getter private final Map<String, Long> killRewardCooldowns = new ConcurrentHashMap<>();

    // Store cooldown configs by permission or group
    @Getter private final Map<String, Long> killRewardCooldownsByPermission = new ConcurrentHashMap<>();

    // Combat configuration cache to avoid repeated config lookups
    private long combatDurationTicks;
    private long combatDurationSeconds;
    private boolean disableFlightInCombat;
    private long enderPearlCooldownTicks;
    private long enderPearlCooldownSeconds;
    private Map<String, Boolean> worldEnderPearlSettings = new ConcurrentHashMap<>();
    private boolean enderPearlInCombatOnly;
    private boolean enderPearlEnabled;

    // Kill reward configuration
    private long killRewardCooldownTicks;
    private long killRewardCooldownSeconds;
    private long samePlayerKillRewardCooldownTicks; // New for same-player cooldown
    private boolean usePermissionBasedCooldowns;
    private boolean useSamePlayerCooldown;
    private boolean useGlobalPlayerCooldown;

    private boolean exemptAdminKick;

    // Cleanup task for expired cooldowns
    private Scheduler.Task cleanupTask;
    private static final long CLEANUP_INTERVAL = 12000L; // 10 minutes in ticks

    public CombatManager(CelestCombat plugin) {
        this.plugin = plugin;
        this.playersInCombat = new ConcurrentHashMap<>();
        this.combatTasks = new ConcurrentHashMap<>();
        this.combatOpponents = new ConcurrentHashMap<>();
        this.enderPearlCooldowns = new ConcurrentHashMap<>();

        // Cache configuration values to avoid repeated lookups
        this.combatDurationTicks = plugin.getTimeFromConfig("combat.duration", "20s");
        this.combatDurationSeconds = combatDurationTicks / 20;
        this.disableFlightInCombat = plugin.getConfig().getBoolean("combat.disable_flight", true);

        this.enderPearlCooldownTicks = plugin.getTimeFromConfig("enderpearl_cooldown.duration", "10s");
        this.enderPearlCooldownSeconds = enderPearlCooldownTicks / 20;
        this.enderPearlEnabled = plugin.getConfig().getBoolean("enderpearl_cooldown.enabled", true);
        this.enderPearlInCombatOnly = plugin.getConfig().getBoolean("enderpearl_cooldown.in_combat_only", true);

        // Load per-world settings
        loadWorldEnderPearlSettings();

        // Load kill reward settings
        loadKillRewardSettings();

        this.exemptAdminKick = plugin.getConfig().getBoolean("combat.exempt_admin_kick", true);

        // Start the global countdown timer
        startGlobalCountdownTimer();

        // Start the cleanup task
        startCleanupTask();
    }

    private void loadKillRewardSettings() {
        // Basic cooldown settings
        this.killRewardCooldownTicks = plugin.getTimeFromConfig("kill_rewards.cooldown.duration", "1d");
        this.killRewardCooldownSeconds = killRewardCooldownTicks / 20;

        // Advanced cooldown settings
        this.useSamePlayerCooldown = plugin.getConfig().getBoolean("kill_rewards.cooldown.use_same_player_cooldown", true);
        this.samePlayerKillRewardCooldownTicks = plugin.getTimeFromConfig("kill_rewards.cooldown.same_player_duration", "1d");

        this.useGlobalPlayerCooldown = plugin.getConfig().getBoolean("kill_rewards.cooldown.use_global_cooldown", false);

        // Permission-based cooldowns
        this.usePermissionBasedCooldowns = plugin.getConfig().getBoolean("kill_rewards.cooldown.use_permission_cooldowns", false);
        if (usePermissionBasedCooldowns && plugin.getConfig().isConfigurationSection("kill_rewards.cooldown.permissions")) {
            for (String perm : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("kill_rewards.cooldown.permissions")).getKeys(false)) {
                String path = "kill_rewards.cooldown.permissions." + perm;
                long permCooldown = plugin.getTimeFromConfig(path, "1d");
                killRewardCooldownsByPermission.put(perm, permCooldown);
            }
        }
    }

    public void reloadConfig() {
        // Update cached configuration values
        this.combatDurationTicks = plugin.getTimeFromConfig("combat.duration", "20s");
        this.combatDurationSeconds = combatDurationTicks / 20;
        this.disableFlightInCombat = plugin.getConfig().getBoolean("combat.disable_flight", true);

        this.enderPearlCooldownTicks = plugin.getTimeFromConfig("enderpearl_cooldown.duration", "10s");
        this.enderPearlCooldownSeconds = enderPearlCooldownTicks / 20;
        this.enderPearlEnabled = plugin.getConfig().getBoolean("enderpearl_cooldown.enabled", true);
        this.enderPearlInCombatOnly = plugin.getConfig().getBoolean("enderpearl_cooldown.in_combat_only", true);
        loadWorldEnderPearlSettings();

        // Reload kill reward settings
        loadKillRewardSettings();

        this.exemptAdminKick = plugin.getConfig().getBoolean("combat.exempt_admin_kick", true);
    }

    // Add this method to load world-specific settings
    private void loadWorldEnderPearlSettings() {
        worldEnderPearlSettings.clear();

        if (plugin.getConfig().isConfigurationSection("enderpearl_cooldown.worlds")) {
            for (String worldName : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("enderpearl_cooldown.worlds")).getKeys(false)) {
                boolean enabled = plugin.getConfig().getBoolean("enderpearl_cooldown.worlds." + worldName, true);
                worldEnderPearlSettings.put(worldName, enabled);
            }
        }
    }

    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = Scheduler.runTaskTimerAsync(() -> {
            long currentTime = System.currentTimeMillis();

            // Clean up kill reward cooldowns
            killRewardCooldowns.entrySet().removeIf(entry -> currentTime > entry.getValue());

            // Log cleanup stats if debug enabled
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Cleaned up kill reward cooldowns. Current size: " + killRewardCooldowns.size());
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    private void startGlobalCountdownTimer() {
        if (globalCountdownTask != null) {
            globalCountdownTask.cancel();
        }

        globalCountdownTask = Scheduler.runTaskTimer(() -> {
            long currentTime = System.currentTimeMillis();

            // Process all players in a single timer tick
            for (Map.Entry<UUID, Long> entry : new HashMap<>(playersInCombat).entrySet()) {
                UUID playerUUID = entry.getKey();
                long combatEndTime = entry.getValue();

                // Check if combat has expired
                if (currentTime > combatEndTime) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        removeFromCombat(player);
                    } else {
                        // Player is offline, clean up
                        playersInCombat.remove(playerUUID);
                        combatOpponents.remove(playerUUID);
                        Scheduler.Task task = combatTasks.remove(playerUUID);
                        if (task != null) {
                            task.cancel();
                        }
                    }
                    continue;
                }

                // Update countdown display for online players
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    updatePlayerCountdown(player, currentTime);
                }
            }

            // Handle ender pearl cooldowns
            enderPearlCooldowns.entrySet().removeIf(entry ->
                    currentTime > entry.getValue() ||
                            Bukkit.getPlayer(entry.getKey()) == null
            );

        }, 0L, COUNTDOWN_INTERVAL);
    }

    private void updatePlayerCountdown(Player player, long currentTime) {
        if (player == null || !player.isOnline()) return;

        UUID playerUUID = player.getUniqueId();
        boolean inCombat = playersInCombat.containsKey(playerUUID) &&
                currentTime <= playersInCombat.get(playerUUID);
        boolean hasPearlCooldown = enderPearlCooldowns.containsKey(playerUUID) &&
                currentTime <= enderPearlCooldowns.get(playerUUID);

        if (!inCombat && !hasPearlCooldown) {
            return;
        }

        if (inCombat) {
            int remainingCombatTime = getRemainingCombatTime(player, currentTime);

            if (hasPearlCooldown) {
                // Both cooldowns active - show combined message
                int remainingPearlTime = getRemainingEnderPearlCooldown(player, currentTime);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("combat_time", String.valueOf(remainingCombatTime));
                placeholders.put("pearl_time", String.valueOf(remainingPearlTime));
                plugin.getMessageService().sendMessage(player, "combat_pearl_countdown", placeholders);
            } else {
                // Only combat cooldown active
                if (remainingCombatTime > 0) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", player.getName());
                    placeholders.put("time", String.valueOf(remainingCombatTime));
                    plugin.getMessageService().sendMessage(player, "combat_countdown", placeholders);
                }
            }
        } else if (hasPearlCooldown) {
            // Player is not in combat but has pearl cooldown
            // This handles the case where in_combat_only is false
            int remainingPearlTime = getRemainingEnderPearlCooldown(player, currentTime);

            if (remainingPearlTime > 0) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("time", String.valueOf(remainingPearlTime));
                plugin.getMessageService().sendMessage(player, "pearl_only_countdown", placeholders);
            }
        }
    }

    public void tagPlayer(Player player, Player attacker) {
        if (player == null || attacker == null) return;

        if (player.hasPermission("celestcombat.bypass.tag")) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        long newEndTime = System.currentTimeMillis() + (combatDurationSeconds * 1000L);

        boolean alreadyInCombat = playersInCombat.containsKey(playerUUID);
        boolean alreadyInCombatWithAttacker = alreadyInCombat &&
                attacker.getUniqueId().equals(combatOpponents.get(playerUUID));

        if (alreadyInCombatWithAttacker) {
            long currentEndTime = playersInCombat.get(playerUUID);
            if (newEndTime <= currentEndTime) {
                return; // Don't reset the timer if it would make it shorter
            }
        }

        // Check if we should disable flight
        if (shouldDisableFlight(player) && player.isFlying()) {
            player.setFlying(false);
        }

        combatOpponents.put(playerUUID, attacker.getUniqueId());
        playersInCombat.put(playerUUID, newEndTime);

        // Cancel existing task if any
        Scheduler.Task existingTask = combatTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }
    }

    public void punishCombatLogout(Player player) {
        if (player == null) return;

        player.setHealth(0);
        removeFromCombat(player);
    }

    public void removeFromCombat(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        playersInCombat.remove(playerUUID);
        combatOpponents.remove(playerUUID);

        Scheduler.Task task = combatTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        // Send appropriate message if player was in combat
        if (player.isOnline()) {
            plugin.getMessageService().sendMessage(player, "combat_expired");
        }
    }

    public void removeFromCombatSilently(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        playersInCombat.remove(playerUUID);
        combatOpponents.remove(playerUUID);

        Scheduler.Task task = combatTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        // No message is sent
    }

    public Player getCombatOpponent(Player player) {
        if (player == null) return null;

        UUID playerUUID = player.getUniqueId();
        if (!playersInCombat.containsKey(playerUUID)) return null;

        UUID opponentUUID = combatOpponents.get(playerUUID);
        if (opponentUUID == null) return null;

        return Bukkit.getPlayer(opponentUUID);
    }

    public boolean isInCombat(Player player) {
        if (player == null) return false;

        UUID playerUUID = player.getUniqueId();
        if (!playersInCombat.containsKey(playerUUID)) {
            return false;
        }

        long combatEndTime = playersInCombat.get(playerUUID);
        long currentTime = System.currentTimeMillis();

        if (currentTime > combatEndTime) {
            removeFromCombat(player);
            return false;
        }

        return true;
    }

    public int getRemainingCombatTime(Player player) {
        return getRemainingCombatTime(player, System.currentTimeMillis());
    }

    private int getRemainingCombatTime(Player player, long currentTime) {
        if (player == null) return 0;

        UUID playerUUID = player.getUniqueId();
        if (!playersInCombat.containsKey(playerUUID)) return 0;

        long endTime = playersInCombat.get(playerUUID);
        return (int) Math.ceil(Math.max(0, (endTime - currentTime) / 1000.0));
    }

    public void updateMutualCombat(Player player1, Player player2) {
        if (player1 != null && player1.isOnline() && player2 != null && player2.isOnline()) {
            tagPlayer(player1, player2);
            tagPlayer(player2, player1);
        }
    }

    // Ender pearl cooldown methods
    public void setEnderPearlCooldown(Player player) {
        if (player == null) return;

        // Only set cooldown if enabled in config
        if (!enderPearlEnabled) {
            return;
        }

        // Check world-specific settings
        String worldName = player.getWorld().getName();
        if (worldEnderPearlSettings.containsKey(worldName) && !worldEnderPearlSettings.get(worldName)) {
            return; // Don't set cooldown in this world
        }

        // Check if we should only apply cooldown in combat
        if (enderPearlInCombatOnly && !isInCombat(player)) {
            return;
        }

        enderPearlCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (enderPearlCooldownSeconds * 1000L));
    }

    public boolean isEnderPearlOnCooldown(Player player) {
        if (player == null) return false;

        // If all ender pearl cooldowns are disabled globally, always return false
        if (!enderPearlEnabled) {
            return false;
        }

        // Check world-specific settings
        String worldName = player.getWorld().getName();
        if (worldEnderPearlSettings.containsKey(worldName) && !worldEnderPearlSettings.get(worldName)) {
            return false; // Cooldown disabled for this specific world
        }

        // Check if we should only apply cooldown in combat
        if (enderPearlInCombatOnly && !isInCombat(player)) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        if (!enderPearlCooldowns.containsKey(playerUUID)) {
            return false;
        }

        long cooldownEndTime = enderPearlCooldowns.get(playerUUID);
        long currentTime = System.currentTimeMillis();

        if (currentTime > cooldownEndTime) {
            enderPearlCooldowns.remove(playerUUID);
            return false;
        }

        return true;
    }

    public int getRemainingEnderPearlCooldown(Player player) {
        return getRemainingEnderPearlCooldown(player, System.currentTimeMillis());
    }

    private int getRemainingEnderPearlCooldown(Player player, long currentTime) {
        if (player == null) return 0;

        UUID playerUUID = player.getUniqueId();
        if (!enderPearlCooldowns.containsKey(playerUUID)) return 0;

        long endTime = enderPearlCooldowns.get(playerUUID);
        return (int) Math.ceil(Math.max(0, (endTime - currentTime) / 1000.0));
    }

    /**
     * Gets the kill reward cooldown duration for a specific player
     *
     * @param killer The player who killed another player
     * @param isSamePlayerCooldown Whether to use the same-player cooldown duration
     * @return Cooldown duration in milliseconds
     */
    private long getKillRewardCooldownDuration(Player killer, boolean isSamePlayerCooldown) {
        if (killer == null) return 0;

        // Check if permission-based cooldowns are enabled
        if (usePermissionBasedCooldowns) {
            // Check for permission-based cooldowns, starting from most specific
            for (Map.Entry<String, Long> entry : killRewardCooldownsByPermission.entrySet()) {
                if (killer.hasPermission("celestcombat.cooldown." + entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // If it's a same-player cooldown and that setting is enabled
        if (isSamePlayerCooldown && useSamePlayerCooldown) {
            return samePlayerKillRewardCooldownTicks * 50; // Convert ticks to ms
        }

        // Default cooldown
        return killRewardCooldownSeconds * 1000L;
    }

    /**
     * Sets a kill reward cooldown for a specific killer-victim pair
     *
     * @param killer The player who killed another player
     * @param victim The player who was killed
     */
    public void setKillRewardCooldown(Player killer, Player victim) {
        if (killer == null || victim == null) return;

        long currentTime = System.currentTimeMillis();
        long expirationTime;

        // If global cooldown is enabled, apply to all victims
        if (useGlobalPlayerCooldown) {
            // Create a key that applies to all victims for this killer
            String globalKey = killer.getUniqueId() + ":global";
            expirationTime = currentTime + getKillRewardCooldownDuration(killer, false);
            killRewardCooldowns.put(globalKey, expirationTime);
            plugin.debug("Set global kill reward cooldown for " + killer.getName() + " until " + expirationTime);
        }

        // Always set specific player cooldown
        String specificKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        expirationTime = currentTime + getKillRewardCooldownDuration(killer, true);
        killRewardCooldowns.put(specificKey, expirationTime);
        plugin.debug("Set specific kill reward cooldown for " + killer.getName() + " killing " + victim.getName() + " until " + expirationTime);
    }

    /**
     * Checks if a player is on kill reward cooldown
     *
     * @param killer The player who killed another player
     * @param victim The player who was killed
     * @return True if the killer is on cooldown for the victim
     */
    public boolean isKillRewardOnCooldown(Player killer, Player victim) {
        if (killer == null || victim == null) return false;

        long currentTime = System.currentTimeMillis();

        // Check global cooldown first if enabled
        if (useGlobalPlayerCooldown) {
            String globalKey = killer.getUniqueId() + ":global";
            if (killRewardCooldowns.containsKey(globalKey)) {
                long cooldownEndTime = killRewardCooldowns.get(globalKey);
                if (currentTime <= cooldownEndTime) {
                    return true;
                } else {
                    // Clean up expired cooldown
                    killRewardCooldowns.remove(globalKey);
                }
            }
        }

        // Check specific player cooldown
        String specificKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        if (!killRewardCooldowns.containsKey(specificKey)) {
            return false;
        }

        long cooldownEndTime = killRewardCooldowns.get(specificKey);
        if (currentTime > cooldownEndTime) {
            // Clean up expired cooldown
            killRewardCooldowns.remove(specificKey);
            return false;
        }
        plugin.debug("Kill reward cooldown for " + killer.getName() + " killing " + victim.getName() + " is active until " + cooldownEndTime);

        return true;
    }

    /**
     * Formats milliseconds into a human-readable time string
     *
     * @param timeMs Time in milliseconds
     * @return Formatted time string (e.g., "2d 5h 30m 10s")
     */
    private String formatTimeRemaining(long timeMs) {
        if (timeMs <= 0) return "0s";

        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (seconds > 0 || result.length() == 0) result.append(seconds).append("s");

        return result.toString().trim();
    }

    /**
     * Gets the remaining kill reward cooldown for a killer-victim pair
     *
     * @param killer The player who killed another player
     * @param victim The player who was killed
     * @return Remaining cooldown time in milliseconds, or 0 if no cooldown
     */
    public long getRemainingKillRewardCooldown(Player killer, Player victim) {
        if (killer == null || victim == null) return 0;

        long currentTime = System.currentTimeMillis();
        long remainingTime = 0;

        // Check global cooldown first if enabled
        if (useGlobalPlayerCooldown) {
            String globalKey = killer.getUniqueId() + ":global";
            if (killRewardCooldowns.containsKey(globalKey)) {
                long cooldownEndTime = killRewardCooldowns.get(globalKey);
                if (currentTime <= cooldownEndTime) {
                    remainingTime = Math.max(remainingTime, cooldownEndTime - currentTime);
                }
            }
        }

        // Check specific player cooldown
        String specificKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        if (killRewardCooldowns.containsKey(specificKey)) {
            long cooldownEndTime = killRewardCooldowns.get(specificKey);
            if (currentTime <= cooldownEndTime) {
                remainingTime = Math.max(remainingTime, cooldownEndTime - currentTime);
            }
        }

        return remainingTime;
    }

    /**
     * Formats remaining kill reward cooldown time for a killer-victim pair
     *
     * @param killer The player who killed another player
     * @param victim The player who was killed
     * @return Formatted time string, or "None" if no cooldown
     */
    public String getFormattedKillRewardCooldown(Player killer, Player victim) {
        long remainingMs = getRemainingKillRewardCooldown(killer, victim);
        if (remainingMs <= 0) {
            return "None";
        }
        return formatTimeRemaining(remainingMs);
    }

    /**
     * Clears all kill reward cooldowns for a specific player
     *
     * @param player The player to clear cooldowns for
     */
    public void clearKillRewardCooldowns(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        killRewardCooldowns.entrySet().removeIf(entry ->
                entry.getKey().startsWith(playerUUID.toString() + ":"));
    }

    public boolean shouldDisableFlight(Player player) {
        if (player == null) return false;

        // If flight is enabled in combat by config or player isn't in combat, don't disable flight
        if (!disableFlightInCombat || !isInCombat(player)) {
            return false;
        }

        // Flight should be disabled - notify the player
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        plugin.getMessageService().sendMessage(player, "combat_fly_disabled", placeholders);

        return true;
    }

    public void shutdown() {
        // Cancel the global countdown task
        if (globalCountdownTask != null) {
            globalCountdownTask.cancel();
            globalCountdownTask = null;
        }

        // Cancel the cleanup task
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        // Cancel all individual tasks
        combatTasks.values().forEach(Scheduler.Task::cancel);
        combatTasks.clear();

        playersInCombat.clear();
        combatOpponents.clear();
        enderPearlCooldowns.clear();
        killRewardCooldowns.clear();
    }
}