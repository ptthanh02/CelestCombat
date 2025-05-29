package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;

public class NewbieProtectionCommand extends BaseCommand {

    public NewbieProtectionCommand(CelestCombat plugin) {
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

        switch (subCommand.toLowerCase()) {
            case "give":
                return executeGive(sender, subArgs);
            case "remove":
                return executeRemove(sender, subArgs);
            case "check":
                return executeCheck(sender, subArgs);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean executeGive(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat newbieProtection give <player>");
            return true;
        }

        // Find the player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        // Check if player already has protection
        if (plugin.getNewbieProtectionManager().hasProtection(target)) {
            placeholders.put("player", target.getName());
            messageService.sendMessage(sender, "newbie_protection_already_has", placeholders);
            return true;
        }

        // Grant protection
        plugin.getNewbieProtectionManager().grantProtection(target);

        // Send success message
        placeholders.put("player", target.getName());
        messageService.sendMessage(sender, "newbie_protection_give_success", placeholders);

        return true;
    }

    private boolean executeRemove(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat newbieProtection remove <player>");
            return true;
        }

        // Find the player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        // Check if player has protection
        if (!plugin.getNewbieProtectionManager().hasProtection(target)) {
            placeholders.put("player", target.getName());
            messageService.sendMessage(sender, "newbie_protection_not_protected", placeholders);
            return true;
        }

        // Remove protection
        plugin.getNewbieProtectionManager().removeProtection(target, false);

        // Send success message
        placeholders.put("player", target.getName());
        messageService.sendMessage(sender, "newbie_protection_remove_success", placeholders);

        return true;
    }

    private boolean executeCheck(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat newbieProtection check <player>");
            return true;
        }

        // Find the player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        // Check protection status
        if (!plugin.getNewbieProtectionManager().hasProtection(target)) {
            placeholders.put("player", target.getName());
            messageService.sendMessage(sender, "newbie_protection_not_protected", placeholders);
            return true;
        }

        // Get remaining time
        long remainingTime = plugin.getNewbieProtectionManager().getRemainingTime(target);
        String formattedTime = formatTime(remainingTime);

        placeholders.put("player", target.getName());
        placeholders.put("time", formattedTime);
        messageService.sendMessage(sender, "newbie_protection_check_protected", placeholders);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§cUsage:");
        sender.sendMessage("§c/celestcombat newbieProtection give <player>");
        sender.sendMessage("§c/celestcombat newbieProtection remove <player>");
        sender.sendMessage("§c/celestcombat newbieProtection check <player>");
    }

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

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Console can also use newbie protection commands
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Complete subcommand names
            return Arrays.asList("give", "remove", "check").stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Complete player names for all subcommands
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return super.tabComplete(sender, args);
    }
}