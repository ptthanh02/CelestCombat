package dev.nighter.celesCombat;

import dev.nighter.celesCombat.combat.CombatManager;
import dev.nighter.celesCombat.commands.CombatCommand;
import dev.nighter.celesCombat.language.LanguageManager;
import dev.nighter.celesCombat.language.GuiService;
import dev.nighter.celesCombat.language.MessageService;
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

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

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

        // Register commands
        CombatCommand combatCommand = new CombatCommand(this);
        PluginCommand command = getCommand("celescombat");
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