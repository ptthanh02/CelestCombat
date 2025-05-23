package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RemoveTagWorldCommand extends BaseCommand {

    public RemoveTagWorldCommand(CelestCombat plugin) {
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
            sender.sendMessage("§cUsage: /celestcombat removeTagWorld <world>");
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
            // Suggest world names
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, args);
    }
}