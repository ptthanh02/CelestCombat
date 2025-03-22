package dev.nighter.celesCombat.commands;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.combat.CombatManager;
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
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CombatCommand implements CommandExecutor, TabCompleter {
    private final CelesCombat plugin;
    private final CombatManager combatManager;

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
                if (!sender.hasPermission("celescombat.command.reload")) {
                    placeholders.put("permission", "celescombat.command.reload");
                    plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
                    return true;
                }

                // Reload config
                plugin.reloadConfig();

                // Send message
                plugin.getMessageService().sendMessage(sender, "config_reloaded", placeholders);
                return true;

            case "status":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;

                if (!player.hasPermission("celescombat.command.status")) {
                    placeholders.put("permission", "celescombat.command.status");
                    plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
                    return true;
                }

                // Check if player is in combat
                boolean inCombat = combatManager.isInCombat(player);
                int remainingTime = combatManager.getRemainingCombatTime(player);

                // Send message
                placeholders.put("player", player.getName());
                placeholders.put("status", inCombat ? "in combat" : "not in combat");
                placeholders.put("time", String.valueOf(remainingTime));
                plugin.getMessageService().sendMessage(player, "combat_status", placeholders);
                return true;

            case "tag":
                if (!sender.hasPermission("celescombat.command.tag")) {
                    placeholders.put("permission", "celescombat.command.tag");
                    plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /combat tag <player>");
                    return true;
                }

                // Get target player
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    placeholders.put("player", args[1]);
                    plugin.getMessageService().sendMessage(sender, "player_not_found", placeholders);
                    return true;
                }

                // Tag player
                combatManager.tagPlayer(target, null);

                // Send message
                placeholders.put("player", target.getName());
                plugin.getMessageService().sendMessage(sender, "player_tagged", placeholders);
                return true;

            case "untag":
                if (!sender.hasPermission("celescombat.command.untag")) {
                    placeholders.put("permission", "celescombat.command.untag");
                    plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /combat untag <player>");
                    return true;
                }

                // Get target player
                Player untagTarget = Bukkit.getPlayer(args[1]);
                if (untagTarget == null) {
                    placeholders.put("player", args[1]);
                    plugin.getMessageService().sendMessage(sender, "player_not_found", placeholders);
                    return true;
                }

                // Untag player
                combatManager.removeFromCombat(untagTarget);

                // Send message
                placeholders.put("player", untagTarget.getName());
                plugin.getMessageService().sendMessage(sender, "player_untagged", placeholders);
                return true;

            case "list":
                if (!sender.hasPermission("celescombat.command.list")) {
                    placeholders.put("permission", "celescombat.command.list");
                    plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
                    return true;
                }

                // Get list of players in combat
                Map<UUID, Long> playersInCombat = combatManager.getPlayersInCombat();

                if (playersInCombat.isEmpty()) {
                    sender.sendMessage("§aThere are currently no players in combat.");
                    return true;
                }

                sender.sendMessage("§6Players in combat:");

                for (Map.Entry<UUID, Long> entry : playersInCombat.entrySet()) {
                    Player combatPlayer = Bukkit.getPlayer(entry.getKey());
                    if (combatPlayer != null) {
                        long remainingMillis = entry.getValue() - System.currentTimeMillis();
                        if (remainingMillis > 0) {
                            sender.sendMessage("§e- " + combatPlayer.getName() + " §7(" + (remainingMillis / 1000) + "s)");
                        }
                    }
                }

                return true;

            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6CelesCombat Commands:");

        if (sender.hasPermission("celescombat.command.reload")) {
            sender.sendMessage("§e/combat reload §7- Reload the configuration");
        }

        if (sender.hasPermission("celescombat.command.status")) {
            sender.sendMessage("§e/combat status §7- Check your combat status");
        }

        if (sender.hasPermission("celescombat.command.tag")) {
            sender.sendMessage("§e/combat tag <player> §7- Tag a player in combat");
        }

        if (sender.hasPermission("celescombat.command.untag")) {
            sender.sendMessage("§e/combat untag <player> §7- Remove a player from combat");
        }

        if (sender.hasPermission("celescombat.command.list")) {
            sender.sendMessage("§e/combat list §7- List all players in combat");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();

            if (sender.hasPermission("celescombat.command.reload")) {
                subCommands.add("reload");
            }

            if (sender.hasPermission("celescombat.command.status")) {
                subCommands.add("status");
            }

            if (sender.hasPermission("celescombat.command.tag")) {
                subCommands.add("tag");
            }

            if (sender.hasPermission("celescombat.command.untag")) {
                subCommands.add("untag");
            }

            if (sender.hasPermission("celescombat.command.list")) {
                subCommands.add("list");
            }

            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if ((args[0].equalsIgnoreCase("tag") && sender.hasPermission("celescombat.command.tag")) ||
                    (args[0].equalsIgnoreCase("untag") && sender.hasPermission("celescombat.command.untag"))) {

                String partialName = args[1].toLowerCase();

                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partialName))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}