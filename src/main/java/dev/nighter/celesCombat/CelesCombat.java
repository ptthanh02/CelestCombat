package dev.nighter.celesCombat;

import dev.nighter.celesCombat.combat.CombatManager;
import dev.nighter.celesCombat.combat.ExplosionTracker;
import dev.nighter.celesCombat.commands.CombatCommand;
import dev.nighter.celesCombat.language.LanguageManager;
import dev.nighter.celesCombat.language.gui.GuiService;
import dev.nighter.celesCombat.language.message.MessageService;
import dev.nighter.celesCombat.listeners.CPVPListeners;
import dev.nighter.celesCombat.listeners.CombatListeners;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CelesCombat extends JavaPlugin {
    @Getter private static CelesCombat instance;
    @Getter private LanguageManager languageManager;
    @Getter private MessageService messageService;
    @Getter private GuiService guiService;
    @Getter private CombatManager combatManager;
    @Getter private CPVPListeners cpvpListeners;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize language manager
        languageManager = new LanguageManager(this);

        // Initialize services
        messageService = new MessageService(this, languageManager);
        guiService = new GuiService(this, languageManager);

        // Initialize combat manager
        combatManager = new CombatManager(this);

        // Initialize and register CPVP listeners
        cpvpListeners = new CPVPListeners(this, combatManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CombatListeners(this, combatManager), this);
        getServer().getPluginManager().registerEvents(cpvpListeners, this);

        // Initialize explosion tracking
        ExplosionTracker.scheduleCleanup(this);

        // Schedule periodic cleanup for CPVP tracking
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            cpvpListeners.cleanupExpiredEntries();
        }, 20L * 60, 20L * 60); // Run every minute

        // Register commands
        CombatCommand combatCommand = new CombatCommand(this, combatManager);
        PluginCommand command = getCommand("combat");
        if (command != null) {
            command.setExecutor(combatCommand);
            command.setTabCompleter(combatCommand);
        }

        getLogger().info("CelesCombat has been enabled with!");
    }

    @Override
    public void onDisable() {
        // Clean up combat manager
        if (combatManager != null) {
            combatManager.shutdown();
        }

        getLogger().info("CelesCombat has been disabled!");
    }
}