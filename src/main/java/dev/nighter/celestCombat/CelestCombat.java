package dev.nighter.celestCombat;

import com.sk89q.worldguard.WorldGuard;
import dev.nighter.celestCombat.combat.CombatManager;
import dev.nighter.celestCombat.commands.CombatCommand;
import dev.nighter.celestCombat.language.LanguageManager;
import dev.nighter.celestCombat.language.GuiService;
import dev.nighter.celestCombat.language.MessageService;
import dev.nighter.celestCombat.listeners.CombatListeners;
import dev.nighter.celestCombat.listeners.EnderPearlListener;
import dev.nighter.celestCombat.hooks.protection.WorldGuardHook;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CelestCombat extends JavaPlugin {
    @Getter private static CelestCombat instance;
    @Getter private LanguageManager languageManager;
    @Getter private MessageService messageService;
    @Getter private GuiService guiService;
    @Getter private CombatManager combatManager;

    // WorldGuard support
    public static boolean hasWorldGuard = false;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Check for protection plugins
        checkProtectionPlugins();

        // Initialize language manager
        languageManager = new LanguageManager(this,
                LanguageManager.LanguageFileType.MESSAGES);

        // Initialize services
        messageService = new MessageService(this, languageManager);
        guiService = new GuiService(this, languageManager);

        // Initialize combat manager
        combatManager = new CombatManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CombatListeners(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new EnderPearlListener(this, combatManager), this);

        // Register WorldGuard hook if available
        if (hasWorldGuard) {
            getLogger().info(""+hasWorldGuard);
            getServer().getPluginManager().registerEvents(new WorldGuardHook(this, combatManager), this);
        }

        // Register commands
        CombatCommand combatCommand = new CombatCommand(this);
        PluginCommand command = getCommand("celestcombat");
        if (command != null) {
            command.setExecutor(combatCommand);
            command.setTabCompleter(combatCommand);
        }

        getLogger().info("CelestCombat has been enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up combat manager
        if (combatManager != null) {
            combatManager.shutdown();
        }

        getLogger().info("CelestCombat has been disabled!");
    }

    private void checkProtectionPlugins() {
        // Check for WorldGuard
        hasWorldGuard = isPluginEnabled("WorldGuard") && isWorldGuardAPIAvailable();
        if (hasWorldGuard) {
            getLogger().info("WorldGuard integration enabled successfully!");
        }
    }

    private boolean isPluginEnabled(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    private boolean isWorldGuardAPIAvailable() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            return WorldGuard.getInstance() != null;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}