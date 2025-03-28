package dev.nighter.celestCombat.combat;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DeathAnimationManager {
    private final CelestCombat plugin;
    private final Random random = new Random();

    public DeathAnimationManager(CelestCombat plugin) {
        this.plugin = plugin;
    }

    public void performDeathAnimation(Player victim, Player killer) {
        // Check if death animations are enabled
        if (!plugin.getConfig().getBoolean("death_animation.enabled", true)) {
            return;
        }

        // Check if the death was by another player
        if (killer == null && plugin.getConfig().getBoolean("death_animation.only_player_kill", true)) {
            return;
        }

        Location deathLocation = victim.getLocation();
        World world = deathLocation.getWorld();

        if (world == null) return;

        // Get available animations from config
        List<String> availableAnimations = new ArrayList<>();

        // Check each animation type
        if (plugin.getConfig().getBoolean("death_animation.animation.lightning", true)) {
            availableAnimations.add("lightning");
        }
        if (plugin.getConfig().getBoolean("death_animation.animation.fire_particles", false)) {
            availableAnimations.add("fire_particles");
        }

        // If no animations are available, return
        if (availableAnimations.isEmpty()) {
            return;
        }

        // Randomly select an animation if multiple are true
        String selectedAnimation = availableAnimations.get(random.nextInt(availableAnimations.size()));

        // Schedule the animation
        Scheduler.runLocationTask(deathLocation, () -> {
            switch (selectedAnimation) {
                case "lightning":
                    performLightningAnimation(world, deathLocation);
                    break;
                case "fire_particles":
                    performParticleAnimation(world, deathLocation);
                    break;
            }
        });
    }

    private void performLightningAnimation(World world, Location location) {
        world.strikeLightningEffect(location);

        // Play a dramatic thunder sound
        world.playSound(
                location,
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                1.0F,
                1.0F
        );
    }

    private void performParticleAnimation(World world, Location location) {
        // Create a circular burst of particles
        for (int i = 0; i < 50; i++) {
            double angle = 2 * Math.PI * i / 50;
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;

            world.spawnParticle(
                    Particle.FLAME,
                    location.clone().add(x, 1, z),
                    1,
                    0.1, 0.1, 0.1,
                    0.05
            );
        }

        // Play a dramatic sound
        world.playSound(
                location,
                Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,
                1.0F,
                1.0F
        );
    }
}