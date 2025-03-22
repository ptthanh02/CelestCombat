package dev.nighter.celesCombat.commands;

import dev.nighter.celesCombat.CelesCombat;
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
    private final CelesCombat plugin;

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
            if (!sender.hasPermission("celescombat.command.reload")) {
                placeholders.put("permission", "celescombat.command.reload");
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
        sender.sendMessage("§6CelesCombat Commands:");

        if (sender.hasPermission("celescombat.command.reload")) {
            sender.sendMessage("§e/combat reload §7- Reload the configuration");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("celescombat.command.reload")) {
                if ("reload".toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add("reload");
                }
            }
        }

        return completions;
    }
}