package dev.nighter.celestCombat.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TabCompleter implements org.bukkit.command.TabCompleter {
    private final CommandManager commandManager;

    public TabCompleter(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Complete subcommand names
            return commandManager.getCommands().entrySet().stream()
                    .filter(entry -> entry.getValue().hasPermission(sender))
                    .map(entry -> entry.getKey())
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            // Complete subcommand arguments
            String subCommand = args[0];
            BaseCommand baseCommand = commandManager.getCommands().get(subCommand);

            // If not found with original case, try to find case-insensitive match
            if (baseCommand == null) {
                baseCommand = commandManager.getCommands().entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(subCommand))
                        .map(entry -> entry.getValue())
                        .findFirst()
                        .orElse(null);
            }

            if (baseCommand != null && baseCommand.hasPermission(sender)) {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                return baseCommand.tabComplete(sender, subArgs);
            }
        }

        return new ArrayList<>();
    }
}