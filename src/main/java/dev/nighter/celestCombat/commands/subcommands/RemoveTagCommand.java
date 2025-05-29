package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
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

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "player":
                return executeRemovePlayer(sender, subArgs);
            case "world":
                return executeRemoveWorld(sender, subArgs);
            case "all":
                return executeRemoveAll(sender, subArgs);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean executeRemovePlayer(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat removeTag player <player>");
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

    private boolean executeRemoveWorld(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /celestcombat removeTag world <world>");
            return true;
        }

        // Find the world
        World targetWorld = Bukkit.getWorld(args[0]);

        // Validate world
        if (targetWorld == null) {
            placeholders.put("world", args[0]);
            messageService.sendMessage(sender, "world_not_found", placeholders);
            return true;
        }

        // Get all players in combat in the specified world
        List<Player> playersInCombat = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld().equals(targetWorld))
                .filter(player -> plugin.getCombatManager().isInCombat(player))
                .collect(Collectors.toList());

        if (playersInCombat.isEmpty()) {
            placeholders.put("world", targetWorld.getName());
            messageService.sendMessage(sender, "no_players_in_combat_world", placeholders);
            return true;
        }

        // Remove all players from combat in the specified world
        int removedCount = 0;
        for (Player player : playersInCombat) {
            plugin.getCombatManager().removeFromCombatSilently(player);
            removedCount++;
        }

        // Send success message to command sender
        placeholders.put("world", targetWorld.getName());
        placeholders.put("count", String.valueOf(removedCount));
        messageService.sendMessage(sender, "combat_remove_world_success", placeholders);

        return true;
    }

    private boolean executeRemoveAll(CommandSender sender, String[] args) {
        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments (no arguments needed)
        if (args.length != 0) {
            sender.sendMessage("§cUsage: /celestcombat removeTag all");
            return true;
        }

        // Get all players in combat
        List<Player> playersInCombat = Bukkit.getOnlinePlayers().stream()
                .filter(player -> plugin.getCombatManager().isInCombat(player))
                .collect(Collectors.toList());

        if (playersInCombat.isEmpty()) {
            messageService.sendMessage(sender, "no_players_in_combat_server", placeholders);
            return true;
        }

        // Remove all players from combat
        int removedCount = 0;
        for (Player player : playersInCombat) {
            plugin.getCombatManager().removeFromCombatSilently(player);
            removedCount++;
        }

        // Send success message to command sender
        placeholders.put("count", String.valueOf(removedCount));
        messageService.sendMessage(sender, "combat_remove_all_success", placeholders);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§cUsage:");
        sender.sendMessage("§c/celestcombat removeTag player <player>");
        sender.sendMessage("§c/celestcombat removeTag world <world>");
        sender.sendMessage("§c/celestcombat removeTag all");
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
            // Complete subcommand names
            return Arrays.asList("player", "world", "all").stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "player":
                    // Only suggest players who are currently in combat
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(player -> plugin.getCombatManager().isInCombat(player))
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "world":
                    // Suggest world names
                    return Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "all":
                    // No tab completion needed for 'all'
                    break;
            }
        }

        return super.tabComplete(sender, args);
    }
}