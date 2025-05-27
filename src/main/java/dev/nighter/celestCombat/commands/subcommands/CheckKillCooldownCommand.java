package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CheckKillCooldownCommand extends BaseCommand {

    public CheckKillCooldownCommand(CelestCombat plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkSender(sender)) {
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("§cUsage: /celestcombat checkKillCooldown <player> [target_player]");
            return true;
        }

        // Find the killer player
        Player killer = Bukkit.getPlayer(args[0]);
        if (killer == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        Player victim = null;
        if (args.length == 2) {
            victim = Bukkit.getPlayer(args[1]);
            if (victim == null) {
                placeholders.put("player", args[1]);
                messageService.sendMessage(sender, "player_not_found", placeholders);
                return true;
            }
        }

        // Get remaining cooldown
        long remainingMs = plugin.getKillRewardManager().getRemainingCooldown(killer, victim);

        if (remainingMs <= 0) {
            placeholders.put("player", killer.getName());
            if (victim != null) {
                placeholders.put("target", victim.getName());
                messageService.sendMessage(sender, "no_kill_cooldown_target", placeholders);
            } else {
                messageService.sendMessage(sender, "no_kill_cooldown", placeholders);
            }
        } else {
            // Format the remaining time
            String formattedTime = formatTime(remainingMs);
            placeholders.put("player", killer.getName());
            placeholders.put("time", formattedTime);

            if (victim != null) {
                placeholders.put("target", victim.getName());
                messageService.sendMessage(sender, "kill_cooldown_remaining_target", placeholders);
            } else {
                messageService.sendMessage(sender, "kill_cooldown_remaining", placeholders);
            }
        }

        return true;
    }

    private String formatTime(long milliseconds) {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");

        return sb.length() > 0 ? sb.toString().trim() : "0s";
    }

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Console can also check cooldowns
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest all online players
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggest target players (for same-player cooldown check)
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, args);
    }
}