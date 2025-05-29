package dev.nighter.celestCombat.commands.subcommands;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.commands.BaseCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReloadCommand extends BaseCommand {

    public ReloadCommand(CelestCombat plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkSender(sender)) {
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();

        plugin.reload();

        // Reload config
        plugin.reloadConfig();
        plugin.getLanguageManager().reloadLanguages();
        plugin.refreshTimeCache();

        if (plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().reloadConfig();
        }

        // Reload combat manager configuration
        plugin.getCombatManager().reloadConfig();
        plugin.getKillRewardManager().loadConfig();
        plugin.getNewbieProtectionManager().reloadConfig();
        plugin.getCombatListeners().reload();

        // Send success message
        messageService.sendMessage(sender, "config_reloaded", placeholders);

        return true;
    }

    @Override
    public String getPermission() {
        return "celestcombat.command.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false; // Allow console to reload the plugin
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>(); // No tab completion needed for reload
    }
}