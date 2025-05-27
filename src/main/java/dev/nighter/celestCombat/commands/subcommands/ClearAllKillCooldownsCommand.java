package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClearAllKillCooldownsCommand extends BaseCommand {

    public ClearAllKillCooldownsCommand(CelestCombat plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkSender(sender)) {
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();

        // Validate arguments
        if (args.length != 0) {
            sender.sendMessage("§cUsage: /celestcombat clearAllKillCooldowns");
            return true;
        }

        // Get the size before clearing for logging
        int cooldownCount = plugin.getKillRewardManager().getKillRewardCooldowns().size();

        // Check if there are any cooldowns to clear
        if (cooldownCount == 0) {
            messageService.sendMessage(sender, "no_cooldowns_to_clear", placeholders);
            return true;
        }

        // Clear all kill reward cooldowns
        plugin.getKillRewardManager().getKillRewardCooldowns().clear();

        // Send success message
        placeholders.put("count", String.valueOf(cooldownCount));
        messageService.sendMessage(sender, "clear_all_cooldowns_success", placeholders);

        // plugin.getLogger().info(sender.getName() + " cleared all kill reward cooldowns (" + cooldownCount + " total)");

        return true;
    }

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Console can also clear all cooldowns
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // No tab completion needed for this command
        return super.tabComplete(sender, args);
    }
}