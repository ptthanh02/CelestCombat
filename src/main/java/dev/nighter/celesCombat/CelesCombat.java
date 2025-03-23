package dev.nighter.celesCombat;

import com.sk89q.worldguard.WorldGuard;
import dev.nighter.celesCombat.combat.CombatManager;
import dev.nighter.celesCombat.commands.CombatCommand;
import dev.nighter.celesCombat.language.LanguageManager;
import dev.nighter.celesCombat.language.GuiService;
import dev.nighter.celesCombat.language.MessageService;
import dev.nighter.celesCombat.listeners.CombatListeners;
import dev.nighter.celesCombat.listeners.EnderPearlListener;
import dev.nighter.celesCombat.protection.WorldGuardHook;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CelesCombat extends JavaPlugin {
    @Getter private static CelesCombat instance;
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
            getServer().getPluginManager().registerEvents(new WorldGuardHook(this, combatManager), this);
        }

        // Register commands
        CombatCommand combatCommand = new CombatCommand(this);
        PluginCommand command = getCommand("celescombat");
        if (command != null) {
            command.setExecutor(combatCommand);
            command.setTabCompleter(combatCommand);
        }

        getLogger().info("CelesCombat has been enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up combat manager
        if (combatManager != null) {
            combatManager.shutdown();
        }

        getLogger().info("CelesCombat has been disabled!");
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