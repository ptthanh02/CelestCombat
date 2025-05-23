package dev.nighter.celestCombat.commands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.subcommands.*;
import lombok.Getter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

import java.util.HashMap;
import java.util.Map;

public class CommandManager implements CommandExecutor {
    private final CelestCombat plugin;
    @Getter
    private final Map<String, BaseCommand> commands = new HashMap<>();
    private final TabCompleter tabCompleter;

    public CommandManager(CelestCombat plugin) {
        this.plugin = plugin;
        this.tabCompleter = new TabCompleter(this);
    }

    public void registerCommands() {
        // Register existing subcommands
        registerCommand("reload", new ReloadCommand(plugin));
        registerCommand("tag", new TagCommand(plugin));
        registerCommand("help", new HelpCommand(plugin));
        registerCommand("removeTag", new RemoveTagCommand(plugin));
        registerCommand("removeTagWorld", new RemoveTagWorldCommand(plugin));
        registerCommand("removeTagAll", new RemoveTagAllCommand(plugin));

        // Register main command handler
        PluginCommand mainCommand = plugin.getCommand("celestcombat");
        if (mainCommand != null) {
            mainCommand.setExecutor(this);
            mainCommand.setTabCompleter(tabCompleter);
        }
    }

    private void registerCommand(String name, BaseCommand command) {
        commands.put(name, command);
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (args.length == 0) {
            // No arguments, show help
            commands.get("help").execute(sender, args);
            return true;
        }

        String subCommand = args[0];
        BaseCommand command = commands.get(subCommand);

        if (command == null) {
            // Unknown subcommand, show help
            commands.get("help").execute(sender, args);
            return true;
        }

        // Remove the subcommand from args array
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        // Execute the subcommand
        return command.execute(sender, subArgs);
    }
}