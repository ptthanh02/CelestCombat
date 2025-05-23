package dev.nighter.celestCombat.commands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.language.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseCommand {
    protected final CelestCombat plugin;
    protected final MessageService messageService;

    public BaseCommand(CelestCombat plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
    }

    /**
     * Execute the command
     * @param sender The sender of the command
     * @param args The arguments of the command
     * @return true if the command was executed successfully, false otherwise
     */
    public abstract boolean execute(CommandSender sender, String[] args);

    /**
     * Get the permission required to execute this command
     * @return The permission string
     */
    public abstract String getPermission();

    /**
     * Check if the sender has permission to execute this command
     * @param sender The sender to check
     * @return true if the sender has permission, false otherwise
     */
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    /**
     * Check if the command can only be executed by players
     * @return true if only players can execute this command, false otherwise
     */
    public abstract boolean isPlayerOnly();

    /**
     * Get tab completions for this command
     * @param sender The sender of the command
     * @param args The arguments of the command
     * @return A list of tab completions
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    /**
     * Check if the command sender is a player and has permission
     * @param sender The sender to check
     * @return true if checks pass, false otherwise
     */
    protected boolean checkSender(CommandSender sender) {
        if (isPlayerOnly() && !(sender instanceof Player)) {
            Map<String, String> placeholders = new HashMap<>();
            messageService.sendMessage(sender, "error_player_only", placeholders);
            return false;
        }

        if (!hasPermission(sender)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("permission", getPermission());
            messageService.sendMessage(sender, "no_permission", placeholders);
            return false;
        }

        return true;
    }
}