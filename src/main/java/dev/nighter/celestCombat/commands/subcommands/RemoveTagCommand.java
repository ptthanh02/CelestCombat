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

public class RemoveTagCommand extends BaseCommand {

    public RemoveTagCommand(CelestCombat plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkSender(sender)) {
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat removeTag <player>");
            return true;
        }

        // Find the player
        Player target = Bukkit.getPlayer(args[0]);

        // Validate player
        if (target == null) {
            placeholders.put("player", args[0]);
            messageService.sendMessage(sender, "player_not_found", placeholders);
            return true;
        }

        // Check if player is in combat
        if (!plugin.getCombatManager().isInCombat(target)) {
            placeholders.put("player", target.getName());
            messageService.sendMessage(sender, "player_not_in_combat", placeholders);
            return true;
        }

        // Remove player from combat silently (no message to the player)
        plugin.getCombatManager().removeFromCombatSilently(target);

        // Send success message to command sender
        placeholders.put("player", target.getName());
        messageService.sendMessage(sender, "combat_remove_success", placeholders);

        return true;
    }

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Console can also remove combat tags
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Only suggest players who are currently in combat
            return Bukkit.getOnlinePlayers().stream()
                    .filter(player -> plugin.getCombatManager().isInCombat(player))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, args);
    }
}