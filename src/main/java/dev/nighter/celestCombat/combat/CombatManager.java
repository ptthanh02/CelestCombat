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
    private final Map<UUID, Scheduler.Task> countdownTasks;
    private final Map<UUID, UUID> combatOpponents;

    @Getter private final Map<UUID, Long> enderPearlCooldowns;
    @Getter private final Map<String, Long> killRewardCooldowns = new ConcurrentHashMap<>();

    public CombatManager(CelestCombat plugin) {
        this.plugin = plugin;
        this.playersInCombat = new ConcurrentHashMap<>();
        this.combatTasks = new ConcurrentHashMap<>();
        this.countdownTasks = new ConcurrentHashMap<>();
        this.combatOpponents = new ConcurrentHashMap<>();
        this.enderPearlCooldowns = new ConcurrentHashMap<>();
    }

    public void tagPlayer(Player player, Player attacker) {
        if (player == null || attacker == null) return;

        UUID playerUUID = player.getUniqueId();
        long combatTimeTicks = plugin.getTimeFromConfig("combat.duration", "20s"); // returns 400 ticks (20s)
        long combatTimeSeconds = combatTimeTicks / 20; // Convert ticks to seconds for display

        boolean alreadyInCombatWithAttacker =
                isInCombat(player) &&
                        attacker.getUniqueId().equals(combatOpponents.get(playerUUID));

        if (alreadyInCombatWithAttacker) {
            long currentEndTime = playersInCombat.get(playerUUID);
            long newEndTime = System.currentTimeMillis() + (combatTimeSeconds * 1000L);

            if (newEndTime <= currentEndTime) {
                return;
            }
        }

        combatOpponents.put(playerUUID, attacker.getUniqueId());

        if (isInCombat(player)) {
            Scheduler.Task existingTask = combatTasks.get(playerUUID);
            if (existingTask != null) {
                existingTask.cancel();
            }

            Scheduler.Task existingCountdownTask = countdownTasks.get(playerUUID);
            if (existingCountdownTask != null) {
                existingCountdownTask.cancel();
            }
        }

        playersInCombat.put(playerUUID, System.currentTimeMillis() + (combatTimeSeconds * 1000L));

        // Use the entity's scheduler for player-specific tasks
        Scheduler.Task task = Scheduler.runEntityTaskLater(player, () -> {
            removeFromCombat(player);
        }, combatTimeTicks); // Use the original ticks for scheduler

        combatTasks.put(playerUUID, task);
        updateCountdownTimer(player);
    }

    private void updateCountdownTimer(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        Scheduler.Task existingTask = countdownTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        Scheduler.Task countdownTask = Scheduler.runEntityTaskTimer(player, () -> {
            if (player.isOnline()) {
                boolean inCombat = isInCombat(player);
                boolean hasPearlCooldown = isEnderPearlOnCooldown(player);

                if (!inCombat && !hasPearlCooldown) {
                    // If neither cooldown is active, cancel the task
                    Scheduler.Task task = countdownTasks.get(playerUUID);
                    if (task != null) {
                        task.cancel();
                        countdownTasks.remove(playerUUID);
                    }
                    return;
                }

                if (inCombat) {
                    int remainingCombatTime = getRemainingCombatTime(player);

                    if (hasPearlCooldown) {
                        // Both cooldowns active - show combined message
                        int remainingPearlTime = getRemainingEnderPearlCooldown(player);

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
                    // Only pearl cooldown active
                    int remainingPearlTime = getRemainingEnderPearlCooldown(player);
                    if (remainingPearlTime > 0) {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("player", player.getName());
                        placeholders.put("time", String.valueOf(remainingPearlTime));
                        plugin.getMessageService().sendMessage(player, "pearl_countdown", placeholders);
                    }
                }
            } else {
                // Player offline, cancel task
                Scheduler.Task task = countdownTasks.get(playerUUID);
                if (task != null) {
                    task.cancel();
                    countdownTasks.remove(playerUUID);
                }
            }
        }, 0L, 20L); // Start immediately (0L) and run every second (20L)

        countdownTasks.put(playerUUID, countdownTask);
    }

    public void punishCombatLogout(Player player) {
        if (player == null || !player.isOnline()) return;

        player.setHealth(0);
        applyLogoutEffects(player.getLocation());
        removeFromCombat(player);
    }

    private void applyLogoutEffects(Location location) {
        if (location == null) return;

        // Schedule location-based effects with the location scheduler
        Scheduler.runLocationTask(location, () -> {
            if (plugin.getConfig().getBoolean("combat.logout_effects.lightning", true)) {
                location.getWorld().strikeLightningEffect(location);
            }

            String soundName = plugin.getConfig().getString("combat.logout_effects.sound", "ENTITY_LIGHTNING_BOLT_THUNDER");
            // Only play sound if not set to "NONE"
            if (soundName != null && !soundName.isEmpty() && !soundName.equalsIgnoreCase("NONE")) {
                try {
                    Sound sound = Sound.valueOf(soundName);
                    location.getWorld().playSound(location, sound, 1.0F, 1.0F);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound effect in config: " + soundName);
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
        if (player == null || !isInCombat(player)) return null;

        UUID opponentUUID = combatOpponents.get(player.getUniqueId());
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

        if (System.currentTimeMillis() > combatEndTime) {
            removeFromCombat(player);
            return false;
        }

        return true;
    }

    public int getRemainingCombatTime(Player player) {
        if (!isInCombat(player)) return 0;

        long endTime = playersInCombat.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();

        // Math.ceil for the countdown to show 20, 19, ... 2, 1 instead of 19, 18, ... 1, 0
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

        // Use getTimeFromConfig to get enderpearl cooldown in ticks
        long cooldownTicks = plugin.getTimeFromConfig("enderpearl_cooldown.duration", "10s");
        long cooldownSeconds = cooldownTicks / 20; // Convert ticks to seconds for real-time tracking

        enderPearlCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));

        // Update the countdown timer to show both cooldowns if in combat
        if (isInCombat(player)) {
            updateCountdownTimer(player);
        } else {
            // Start a countdown timer just for pearl cooldown
            updateCountdownTimer(player);
        }
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
        if (System.currentTimeMillis() > cooldownEndTime) {
            enderPearlCooldowns.remove(playerUUID);

            // If the pearl cooldown ended but player is still in combat,
            // update the countdown to show only combat time
            if (isInCombat(player)) {
                updateCountdownTimer(player);
            }

            return false;
        }

        return true;
    }

    public int getRemainingEnderPearlCooldown(Player player) {
        if (player == null || !isEnderPearlOnCooldown(player)) return 0;

        long endTime = enderPearlCooldowns.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();

        return (int) Math.ceil(Math.max(0, (endTime - currentTime) / 1000.0));
    }

    public void setKillRewardCooldown(Player killer, Player victim) {
        if (killer == null || victim == null) return;

        // Use getTimeFromConfig for kill rewards cooldown (converting days to ticks)
        long cooldownTicks = plugin.getTimeFromConfig("kill_rewards.cooldown.duration", "1d");
        if (cooldownTicks <= 0) return;

        // Convert ticks to days for real-time tracking
        long cooldownDays = cooldownTicks / (20 * 60 * 60 * 24); // ticks to days (20 ticks/s * 60s/min * 60min/h * 24h/day)
        if (cooldownDays <= 0) return;

        // Create a unique key for this killer-victim pair
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();

        // Calculate expiration time (current time + cooldown in milliseconds)
        long expirationTime = System.currentTimeMillis() + (cooldownDays * 24L * 60L * 60L * 1000L);
        killRewardCooldowns.put(key, expirationTime);
    }

    public boolean isKillRewardOnCooldown(Player killer, Player victim) {
        if (killer == null || victim == null) return false;

        // If cooldowns are disabled in config, always return false
        long cooldownTicks = plugin.getTimeFromConfig("kill_rewards.cooldown.duration", "1d");
        long cooldownDays = cooldownTicks / (20 * 60 * 60 * 24); // Convert ticks to days
        if (cooldownDays <= 0) return false;

        // Create the unique key for this killer-victim pair
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();

        if (!killRewardCooldowns.containsKey(key)) {
            return false;
        }

        long cooldownEndTime = killRewardCooldowns.get(key);
        if (System.currentTimeMillis() > cooldownEndTime) {
            killRewardCooldowns.remove(key);
            return false;
        }

        return true;
    }

    public long getRemainingKillRewardCooldown(Player killer, Player victim) {
        if (killer == null || victim == null || !isKillRewardOnCooldown(killer, victim)) return 0;

        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        long endTime = killRewardCooldowns.get(key);
        long currentTime = System.currentTimeMillis();

        // Return remaining time in days
        return (long) Math.ceil(Math.max(0, (endTime - currentTime) / (24.0 * 60.0 * 60.0 * 1000.0)));
    }

    public void shutdown() {
        combatTasks.values().forEach(Scheduler.Task::cancel);
        combatTasks.clear();

        countdownTasks.values().forEach(Scheduler.Task::cancel);
        countdownTasks.clear();

        playersInCombat.clear();
        combatOpponents.clear();
        enderPearlCooldowns.clear();
    }
}