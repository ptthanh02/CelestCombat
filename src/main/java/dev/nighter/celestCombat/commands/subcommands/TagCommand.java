package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TagCommand extends BaseCommand {

    public TagCommand(CelestCombat plugin) {
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
            sender.sendMessage("§cUsage: /celestcombat tag <player1> [player2]");
            return true;
        }

        // Find the first player
        Player player1 = Bukkit.getPlayer(args[0]);

        // Validate first player
        if (player1 == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        // Check if it's a single player tag or mutual combat
        if (args.length == 1) {
            // Tag the player
            plugin.getCombatManager().updateMutualCombat(player1, player1);

            // Send success message
            placeholders.put("player", player1.getName());
            messageService.sendMessage(sender, "combat_tag_single_success", placeholders);
        } else {
            // Two-player tag
            Player player2 = Bukkit.getPlayer(args[1]);

            // Validate second player
            if (player2 == null) {
                placeholders.put("player", args[1]);
                messageService.sendMessage(sender, "player_not_found", placeholders);
                return true;
            }

            // Tag players in mutual combat
            plugin.getCombatManager().updateMutualCombat(player1, player2);

            // Send success message
            placeholders.put("player1", player1.getName());
            placeholders.put("player2", player2.getName());
            messageService.sendMessage(sender, "combat_tag_success", placeholders);
        }

        return true;
    }

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Console can also tag players
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 || args.length == 2) {
            // Suggest online player names
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, args);
    }
}
