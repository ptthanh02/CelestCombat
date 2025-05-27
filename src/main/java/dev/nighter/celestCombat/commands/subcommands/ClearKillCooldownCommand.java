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

public class ClearKillCooldownCommand extends BaseCommand {

    public ClearKillCooldownCommand(CelestCombat plugin) {
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
            sender.sendMessage("§cUsage: /celestcombat clearKillCooldown <player>");
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

        // plugin.getLogger().info(sender.getName() + " cleared kill reward cooldowns for " + target.getName());

        return true;
    }

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Console can also clear cooldowns
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest all online players
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, args);
    }
}