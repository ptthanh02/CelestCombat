package dev.nighter.celestCombat.commands;

import dev.nighter.celestCombat.CelestCombat;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        if (args[0].toLowerCase().equals("reload")) {
            if (!sender.hasPermission("celestcombat.command.reload")) {
                placeholders.put("permission", "celestcombat.command.reload");
                plugin.getMessageService().sendMessage(sender, "no_permission", placeholders);
                return true;
            }

            // Reload config
            plugin.reloadConfig();
            plugin.getLanguageManager().reloadLanguages();

            // Send message
            plugin.getMessageService().sendMessage(sender, "config_reloaded", placeholders);
            return true;
        } else {
            sendHelpMessage(sender);
            return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6CelestCombat Commands:");

        if (sender.hasPermission("celestcombat.command.reload")) {
            sender.sendMessage("§e/celestcombat reload §7- Reload the configuration");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("celestcombat.command.reload")) {
                if ("reload".toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add("reload");
                }
            }
        }

        return completions;
    }
}