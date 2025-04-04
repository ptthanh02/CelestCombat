package dev.nighter.celestCombat.combat;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
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

    // Combat configuration cache to avoid repeated config lookups
    private final long combatDurationTicks;
    private final long combatDurationSeconds;
    private final boolean disableFlightInCombat;
    private final long enderPearlCooldownTicks;
    private final long enderPearlCooldownSeconds;
    private final long killRewardCooldownTicks;
    private final long killRewardCooldownDays;
    private final String logoutSoundName;
    private final boolean useLogoutLightning;

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
        this.killRewardCooldownTicks = plugin.getTimeFromConfig("kill_rewards.cooldown.duration", "1d");
        this.killRewardCooldownDays = killRewardCooldownTicks / (20 * 60 * 60 * 24);
        this.logoutSoundName = plugin.getConfig().getString("combat.logout_effects.sound", "ENTITY_LIGHTNING_BOLT_THUNDER");
        this.useLogoutLightning = plugin.getConfig().getBoolean("combat.logout_effects.lightning", true);

        // Start the global countdown timer
        startGlobalCountdownTimer();
    }

    private void startGlobalCountdownTimer() {
        if (globalCountdownTask != null) {
            globalCountdownTask.cancel();
        }

        globalCountdownTask = Scheduler.runTaskTimer(() -> {
            long currentTime = System.currentTimeMillis();

            // Process all players in a single timer tick
            for (Map.Entry<UUID, Long> entry : playersInCombat.entrySet()) {
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

            // Clean up expired kill reward cooldowns
            killRewardCooldowns.entrySet().removeIf(entry -> currentTime > entry.getValue());

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
        }
        // Don't handle only pearl cooldown because player is not in combat
    }

    public void tagPlayer(Player player, Player attacker) {
        if (player == null || attacker == null) return;

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
        if (disableFlightInCombat) {
            if (player.isFlying()) {
                player.setFlying(false);
            }
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
        applyLogoutEffects(player.getLocation());
        removeFromCombat(player);
    }

    private void applyLogoutEffects(Location location) {
        if (location == null) return;

        // Batch location-based effects in a single task
        Scheduler.runLocationTask(location, () -> {
            if (useLogoutLightning) {
                location.getWorld().strikeLightningEffect(location);
            }

            // Only play sound if not set to "NONE"
            if (logoutSoundName != null && !logoutSoundName.isEmpty() && !logoutSoundName.equalsIgnoreCase("NONE")) {
                try {
                    Sound sound = Sound.valueOf(logoutSoundName);
                    location.getWorld().playSound(location, sound, 1.0F, 1.0F);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound effect in config: " + logoutSoundName);
                }
            }
        });
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
        if (!plugin.getConfig().getBoolean("enderpearl_cooldown.enabled", true)) {
            return;
        }

        enderPearlCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (enderPearlCooldownSeconds * 1000L));
    }

    public boolean isEnderPearlOnCooldown(Player player) {
        if (player == null) return false;

        // If ender pearl cooldowns are disabled in config, always return false
        if (!plugin.getConfig().getBoolean("enderpearl_cooldown.enabled", true)) {
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

    public void setKillRewardCooldown(Player killer, Player victim) {
        if (killer == null || victim == null || killRewardCooldownDays <= 0) return;

        // Create a unique key for this killer-victim pair
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();

        // Calculate expiration time (current time + cooldown in milliseconds)
        long expirationTime = System.currentTimeMillis() + (killRewardCooldownDays * 24L * 60L * 60L * 1000L);
        killRewardCooldowns.put(key, expirationTime);
    }

    public boolean isKillRewardOnCooldown(Player killer, Player victim) {
        if (killer == null || victim == null || killRewardCooldownDays <= 0) return false;

        // Create the unique key for this killer-victim pair
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();

        if (!killRewardCooldowns.containsKey(key)) {
            return false;
        }

        long cooldownEndTime = killRewardCooldowns.get(key);
        long currentTime = System.currentTimeMillis();

        if (currentTime > cooldownEndTime) {
            killRewardCooldowns.remove(key);
            return false;
        }

        return true;
    }

    public long getRemainingKillRewardCooldown(Player killer, Player victim) {
        if (killer == null || victim == null) return 0;

        String key = killer.getUniqueId().toString() + ":" + victim.getUniqueId().toString();
        if (!killRewardCooldowns.containsKey(key)) return 0;

        long endTime = killRewardCooldowns.get(key);
        long currentTime = System.currentTimeMillis();

        // Return remaining time in days
        return (long) Math.ceil(Math.max(0, (endTime - currentTime) / (24.0 * 60.0 * 60.0 * 1000.0)));
    }

    public boolean canFlyInCombat(Player player) {
        if (player == null) return true;

        // If flight in combat is allowed in config or player isn't in combat, allow flight
        if (!disableFlightInCombat || !isInCombat(player)) {
            return true;
        }

        player.setFlying(false);

        // Notify the player
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        plugin.getMessageService().sendMessage(player, "combat_fly_disabled", placeholders);

        return false;
    }

    public void shutdown() {
        // Cancel the global countdown task
        if (globalCountdownTask != null) {
            globalCountdownTask.cancel();
            globalCountdownTask = null;
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