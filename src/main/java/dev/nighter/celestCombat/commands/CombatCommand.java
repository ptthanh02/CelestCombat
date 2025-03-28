package dev.nighter.celestCombat.commands;

import dev.nighter.celestCombat.CelestCombat;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CombatCommand implements CommandExecutor, TabCompleter {
    private final CelestCombat plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Define placeholders map once for all cases
        Map<String, String> placeholders = new HashMap<>();

        if (args.length == 0) {
            // Show help message
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReloadCommand(sender, placeholders);
                return true;
            case "tag":
                handleTagCommand(sender, args, placeholders);
                return true;
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private void handleReloadCommand(CommandSender sender, Map<String, String> placeholders) {
        if (!sender.hasPermission("celestcombat.command.reload")) {
            placeholders.put("permission", "celestcombat.command.reload");
            plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
            return;
        }

        // Reload config
        plugin.reloadConfig();
        plugin.getLanguageManager().reloadLanguages();

        // Send message
        plugin.getMessageService().sendMessage(sender, "config_reloaded", placeholders);
    }

    private void handleTagCommand(CommandSender sender, String[] args, Map<String, String> placeholders) {
        // Check permission
        if (!sender.hasPermission("celestcombat.command.tag")) {
            placeholders.put("permission", "celestcombat.command.tag");
            plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
            return;
        }

        // Validate arguments
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("§cUsage: /celestcombat tag <player1> [player2]");
            return;
        }

        // Find the first player
        Player player1 = Bukkit.getPlayer(args[1]);

        // Validate first player
        if (player1 == null) {
            placeholders.put("player", args[1]);
            plugin.getMessageService().sendMessage(sender, "player_not_found", placeholders);
            return;
        }

        // Check if it's a single player tag or mutual combat
        if (args.length == 2) {

            // Tag the player
            plugin.getCombatManager().updateMutualCombat(player1, player1);

            // Send success message
            placeholders.put("player", player1.getName());
            plugin.getMessageService().sendMessage(sender, "combat_tag_single_success", placeholders);
        } else {
            // Two-player tag
            Player player2 = Bukkit.getPlayer(args[2]);

            // Validate second player
            if (player2 == null) {
                placeholders.put("player", args[2]);
                plugin.getMessageService().sendMessage(sender, "player_not_found", placeholders);
                return;
            }

            // Tag players in mutual combat
            plugin.getCombatManager().updateMutualCombat(player1, player2);

            // Send success message
            placeholders.put("player1", player1.getName());
            placeholders.put("player2", player2.getName());
            plugin.getMessageService().sendMessage(sender, "combat_tag_success", placeholders);
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6CelestCombat Commands:");

        if (sender.hasPermission("celestcombat.command.reload")) {
            sender.sendMessage("§e/celestcombat reload §7- Reload the configuration");
        }

        if (sender.hasPermission("celestcombat.command.tag")) {
            sender.sendMessage("§e/celestcombat tag <player1> §7- Tag a player in combat");
            sender.sendMessage("§e/celestcombat tag <player1> <player2> §7- Tag two players in combat");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();

            if (sender.hasPermission("celestcombat.command.reload")) {
                subCommands.add("reload");
            }

            if (sender.hasPermission("celestcombat.command.tag")) {
                subCommands.add("tag");
            }

            completions = subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 || args.length == 3) {
            // If the subcommand is 'tag', suggest online player names
            if (args[0].toLowerCase().equals("tag") && sender.hasPermission("celestcombat.command.tag")) {
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}