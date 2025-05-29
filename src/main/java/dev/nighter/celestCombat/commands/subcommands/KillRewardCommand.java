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
import java.util.Arrays;

public class KillRewardCommand extends BaseCommand {

    public KillRewardCommand(CelestCombat plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkSender(sender)) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0];
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "check":
                return executeCheck(sender, subArgs);
            case "clear":
                return executeClear(sender, subArgs);
            case "clearAll":
                return executeClearAll(sender, subArgs);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean executeCheck(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("§cUsage: /celestcombat killReward check <player> [target_player]");
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

    private boolean executeClear(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat killReward clear <player>");
            return true;
        }

        // Find the player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        // Clear the player's kill reward cooldowns
        plugin.getKillRewardManager().clearPlayerCooldowns(target);

        // Send success message
        placeholders.put("player", target.getName());
        messageService.sendMessage(sender, "clear_cooldown_success", placeholders);

        return true;
    }

    private boolean executeClearAll(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 0) {
            sender.sendMessage("§cUsage: /celestcombat killReward clearAll");
            return true;
        }

        // Get the size before clearing for logging
        int cooldownCount = plugin.getKillRewardManager().getKillRewardCooldowns().size();

        // Check if there are any cooldowns to clear
        if (cooldownCount == 0) {
            messageService.sendMessage(sender, "no_cooldowns_to_clear", placeholders);
            return true;
        }

        // Clear all kill reward cooldowns
        plugin.getKillRewardManager().getKillRewardCooldowns().clear();

        // Send success message
        placeholders.put("count", String.valueOf(cooldownCount));
        messageService.sendMessage(sender, "clear_all_cooldowns_success", placeholders);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§cUsage:");
        sender.sendMessage("§c/celestcombat killReward check <player> [target_player]");
        sender.sendMessage("§c/celestcombat killReward clear <player>");
        sender.sendMessage("§c/celestcombat killReward clearAll");
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
        return false; // Console can also use kill reward commands
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Complete subcommand names
            return Arrays.asList("check", "clear", "clearAll").stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "check":
                    if (args.length == 2 || args.length == 3) {
                        // Suggest all online players for both killer and victim
                        return Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    break;
                case "clear":
                    if (args.length == 2) {
                        // Suggest all online players
                        return Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    break;
                case "clearAll":
                    // No tab completion needed for clearAll
                    break;
            }
        }

        return super.tabComplete(sender, args);
    }
}