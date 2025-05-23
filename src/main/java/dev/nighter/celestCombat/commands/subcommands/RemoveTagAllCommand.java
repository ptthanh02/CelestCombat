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

public class RemoveTagAllCommand extends BaseCommand {

    public RemoveTagAllCommand(CelestCombat plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkSender(sender)) {
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments (no arguments needed)
        if (args.length != 0) {
            sender.sendMessage("§cUsage: /celestcombat removeTagAll");
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
        // No tab completion needed for this command
        return super.tabComplete(sender, args);
    }
}