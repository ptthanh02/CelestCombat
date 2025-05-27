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
    @Getter private final Map<UUID, Long> tridentCooldowns = new ConcurrentHashMap<>();

    // Combat configuration cache to avoid repeated config lookups
    private long combatDurationTicks;
    private long combatDurationSeconds;
    private boolean disableFlightInCombat;
    private long enderPearlCooldownTicks;
    private long enderPearlCooldownSeconds;
    private Map<String, Boolean> worldEnderPearlSettings = new ConcurrentHashMap<>();
    private boolean enderPearlInCombatOnly;
    private boolean enderPearlEnabled;
    private boolean refreshCombatOnPearlLand;

    // Trident configuration cache
    private long tridentCooldownTicks;
    private long tridentCooldownSeconds;
    private Map<String, Boolean> worldTridentSettings = new ConcurrentHashMap<>();
    private boolean tridentInCombatOnly;
    private boolean tridentEnabled;
    private boolean refreshCombatOnTridentLand;
    private Map<String, Boolean> worldTridentBannedSettings = new ConcurrentHashMap<>();

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
        this.refreshCombatOnPearlLand = plugin.getConfig().getBoolean("enderpearl.refresh_combat_on_land", false);

        this.tridentCooldownTicks = plugin.getTimeFromConfig("trident_cooldown.duration", "10s");
        this.tridentCooldownSeconds = tridentCooldownTicks / 20;
        this.tridentEnabled = plugin.getConfig().getBoolean("trident_cooldown.enabled", true);
        this.tridentInCombatOnly = plugin.getConfig().getBoolean("trident_cooldown.in_combat_only", true);
        this.refreshCombatOnTridentLand = plugin.getConfig().getBoolean("trident.refresh_combat_on_land", false);

        // Load per-world settings
        loadWorldTridentSettings();

        // Load per-world settings
        loadWorldEnderPearlSettings();

        // Start the global countdown timer
        startGlobalCountdownTimer();
    }

    private void loadWorldTridentSettings() {
        worldTridentSettings.clear();
        worldTridentBannedSettings.clear();

        // Load cooldown settings per world
        if (plugin.getConfig().isConfigurationSection("trident_cooldown.worlds")) {
            for (String worldName : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("trident_cooldown.worlds")).getKeys(false)) {
                boolean enabled = plugin.getConfig().getBoolean("trident_cooldown.worlds." + worldName, true);
                worldTridentSettings.put(worldName, enabled);
            }
        }

        // Load banned settings per world
        if (plugin.getConfig().isConfigurationSection("trident.banned_worlds")) {
            for (String worldName : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("trident.banned_worlds")).getKeys(false)) {
                boolean banned = plugin.getConfig().getBoolean("trident.banned_worlds." + worldName, false);
                worldTridentBannedSettings.put(worldName, banned);
            }
        }

        // plugin.getLogger().info("Loaded world-specific trident settings: " + worldTridentSettings);
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
        this.refreshCombatOnPearlLand = plugin.getConfig().getBoolean("enderpearl.refresh_combat_on_land", false);
        loadWorldEnderPearlSettings();

        this.tridentCooldownTicks = plugin.getTimeFromConfig("trident_cooldown.duration", "10s");
        this.tridentCooldownSeconds = tridentCooldownTicks / 20;
        this.tridentEnabled = plugin.getConfig().getBoolean("trident_cooldown.enabled", true);
        this.tridentInCombatOnly = plugin.getConfig().getBoolean("trident_cooldown.in_combat_only", true);
        this.refreshCombatOnTridentLand = plugin.getConfig().getBoolean("trident.refresh_combat_on_land", false);
        loadWorldTridentSettings();
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

            tridentCooldowns.entrySet().removeIf(entry ->
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
        boolean hasTridentCooldown = tridentCooldowns.containsKey(playerUUID) &&
                currentTime <= tridentCooldowns.get(playerUUID);

        if (!inCombat && !hasPearlCooldown && !hasTridentCooldown) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());

        if (inCombat) {
            int remainingCombatTime = getRemainingCombatTime(player, currentTime);
            placeholders.put("combat_time", String.valueOf(remainingCombatTime));

            if (hasPearlCooldown && hasTridentCooldown) {
                // All three cooldowns active - show combined message
                int remainingPearlTime = getRemainingEnderPearlCooldown(player, currentTime);
                int remainingTridentTime = getRemainingTridentCooldown(player, currentTime);

                placeholders.put("pearl_time", String.valueOf(remainingPearlTime));
                placeholders.put("trident_time", String.valueOf(remainingTridentTime));
                plugin.getMessageService().sendMessage(player, "combat_pearl_trident_countdown", placeholders);
            } else if (hasPearlCooldown) {
                // Combat + pearl cooldown active
                int remainingPearlTime = getRemainingEnderPearlCooldown(player, currentTime);
                placeholders.put("pearl_time", String.valueOf(remainingPearlTime));
                plugin.getMessageService().sendMessage(player, "combat_pearl_countdown", placeholders);
            } else if (hasTridentCooldown) {
                // Combat + trident cooldown active
                int remainingTridentTime = getRemainingTridentCooldown(player, currentTime);
                placeholders.put("trident_time", String.valueOf(remainingTridentTime));
                plugin.getMessageService().sendMessage(player, "combat_trident_countdown", placeholders);
            } else {
                // Only combat cooldown active
                if (remainingCombatTime > 0) {
                    placeholders.put("time", String.valueOf(remainingCombatTime));
                    plugin.getMessageService().sendMessage(player, "combat_countdown", placeholders);
                }
            }
        } else if (hasPearlCooldown && hasTridentCooldown) {
            // Both pearl and trident cooldowns but no combat
            int remainingPearlTime = getRemainingEnderPearlCooldown(player, currentTime);
            int remainingTridentTime = getRemainingTridentCooldown(player, currentTime);

            placeholders.put("pearl_time", String.valueOf(remainingPearlTime));
            placeholders.put("trident_time", String.valueOf(remainingTridentTime));
            plugin.getMessageService().sendMessage(player, "pearl_trident_countdown", placeholders);
        } else if (hasPearlCooldown) {
            // Only pearl cooldown active
            int remainingPearlTime = getRemainingEnderPearlCooldown(player, currentTime);
            if (remainingPearlTime > 0) {
                placeholders.put("time", String.valueOf(remainingPearlTime));
                plugin.getMessageService().sendMessage(player, "pearl_only_countdown", placeholders);
            }
        } else if (hasTridentCooldown) {
            // Only trident cooldown active
            int remainingTridentTime = getRemainingTridentCooldown(player, currentTime);
            if (remainingTridentTime > 0) {
                placeholders.put("time", String.valueOf(remainingTridentTime));
                plugin.getMessageService().sendMessage(player, "trident_only_countdown", placeholders);
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

        if (!playersInCombat.containsKey(playerUUID)) {
            return; // Player is not in combat
        }

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

    public void refreshCombatOnPearlLand(Player player) {
        if (player == null || !refreshCombatOnPearlLand) return;

        // Only refresh if player is already in combat
        if (!isInCombat(player)) return;

        UUID playerUUID = player.getUniqueId();
        long newEndTime = System.currentTimeMillis() + (combatDurationSeconds * 1000L);
        long currentEndTime = playersInCombat.getOrDefault(playerUUID, 0L);

        // Only extend the combat time, don't shorten it
        if (newEndTime > currentEndTime) {
            playersInCombat.put(playerUUID, newEndTime);

            // Debug message if debug is enabled
            plugin.debug("Refreshed combat time for " + player.getName() + " due to pearl landing");
        }
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

    public void setTridentCooldown(Player player) {
        if (player == null) return;

        // Only set cooldown if enabled in config
        if (!tridentEnabled) {
            return;
        }

        // Check world-specific settings
        String worldName = player.getWorld().getName();
        if (worldTridentSettings.containsKey(worldName) && !worldTridentSettings.get(worldName)) {
            return; // Don't set cooldown in this world
        }

        // Check if we should only apply cooldown in combat
        if (tridentInCombatOnly && !isInCombat(player)) {
            return;
        }

        tridentCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (tridentCooldownSeconds * 1000L));
    }

    public boolean isTridentOnCooldown(Player player) {
        if (player == null) return false;

        // If all trident cooldowns are disabled globally, always return false
        if (!tridentEnabled) {
            return false;
        }

        // Check world-specific settings
        String worldName = player.getWorld().getName();
        if (worldTridentSettings.containsKey(worldName) && !worldTridentSettings.get(worldName)) {
            return false; // Cooldown disabled for this specific world
        }

        // Check if we should only apply cooldown in combat
        if (tridentInCombatOnly && !isInCombat(player)) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        if (!tridentCooldowns.containsKey(playerUUID)) {
            return false;
        }

        long cooldownEndTime = tridentCooldowns.get(playerUUID);
        long currentTime = System.currentTimeMillis();

        if (currentTime > cooldownEndTime) {
            tridentCooldowns.remove(playerUUID);
            return false;
        }

        return true;
    }

    public boolean isTridentBanned(Player player) {
        if (player == null) return false;

        // Check world-specific ban settings
        String worldName = player.getWorld().getName();
        return worldTridentBannedSettings.getOrDefault(worldName, false);
    }

    public void refreshCombatOnTridentLand(Player player) {
        if (player == null || !refreshCombatOnTridentLand) return;

        // Only refresh if player is already in combat
        if (!isInCombat(player)) return;

        UUID playerUUID = player.getUniqueId();
        long newEndTime = System.currentTimeMillis() + (combatDurationSeconds * 1000L);
        long currentEndTime = playersInCombat.getOrDefault(playerUUID, 0L);

        // Only extend the combat time, don't shorten it
        if (newEndTime > currentEndTime) {
            playersInCombat.put(playerUUID, newEndTime);

            // Debug message if debug is enabled
            plugin.debug("Refreshed combat time for " + player.getName() + " due to trident landing");
        }
    }

    public int getRemainingTridentCooldown(Player player) {
        return getRemainingTridentCooldown(player, System.currentTimeMillis());
    }

    private int getRemainingTridentCooldown(Player player, long currentTime) {
        if (player == null) return 0;

        UUID playerUUID = player.getUniqueId();
        if (!tridentCooldowns.containsKey(playerUUID)) return 0;

        long endTime = tridentCooldowns.get(playerUUID);
        return (int) Math.ceil(Math.max(0, (endTime - currentTime) / 1000.0));
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
        tridentCooldowns.clear();
    }
}