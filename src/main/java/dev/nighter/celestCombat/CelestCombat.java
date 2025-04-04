package dev.nighter.celestCombat;

import com.sk89q.worldguard.WorldGuard;
import dev.nighter.celestCombat.bstats.Metrics;
import dev.nighter.celestCombat.combat.CombatManager;
import dev.nighter.celestCombat.combat.DeathAnimationManager;
import dev.nighter.celestCombat.commands.CombatCommand;
import dev.nighter.celestCombat.configs.TimeFormatter;
import dev.nighter.celestCombat.language.LanguageManager;
import dev.nighter.celestCombat.language.GuiService;
import dev.nighter.celestCombat.language.MessageService;
import dev.nighter.celestCombat.listeners.CombatListeners;
import dev.nighter.celestCombat.listeners.EnderPearlListener;
import dev.nighter.celestCombat.hooks.protection.WorldGuardHook;
import dev.nighter.celestCombat.listeners.ItemRestrictionListener;
import dev.nighter.celestCombat.updates.ConfigUpdater;
import dev.nighter.celestCombat.updates.UpdateChecker;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Accessors(chain = false)
public final class CelestCombat extends JavaPlugin {
    @Getter
    private static CelestCombat instance;
    private LanguageManager languageManager;
    private MessageService messageService;
    private UpdateChecker updateChecker;
    private ConfigUpdater configUpdater;
    private TimeFormatter timeFormatter;
    private GuiService guiService;
    private CombatManager combatManager;
    private DeathAnimationManager deathAnimationManager;
    private WorldGuardHook worldGuardHook;

    // WorldGuard support
    public static boolean hasWorldGuard = false;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
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
        updateChecker = new UpdateChecker(this);
        configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        timeFormatter = new TimeFormatter(this);

        // Initialize combat manager
        deathAnimationManager = new DeathAnimationManager(this);
        combatManager = new CombatManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CombatListeners(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new EnderPearlListener(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new ItemRestrictionListener(this, combatManager), this);

        // Register WorldGuard hook if available
        if (hasWorldGuard) {
            worldGuardHook = new WorldGuardHook(this, combatManager);
            getServer().getPluginManager().registerEvents(new WorldGuardHook(this, combatManager), this);
        }

        // Register commands
        CombatCommand combatCommand = new CombatCommand(this);
        PluginCommand command = getCommand("celestcombat");
        if (command != null) {
            command.setExecutor(combatCommand);
            command.setTabCompleter(combatCommand);
        }

        // Setup bStats metrics
        setupBtatsMetrics();

        // Plugin startup message
        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("CelestCombat has been enabled! (Loaded in " + loadTime + "ms)");
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

    private void setupBtatsMetrics() {
        Scheduler.runTask(() -> {
            Metrics metrics = new Metrics(this, 25281);
            metrics.addCustomChart(new Metrics.SimplePie("players",
                    () -> String.valueOf(Bukkit.getOnlinePlayers().size())));
        });
    }

    public long getTimeFromConfig(String path, String defaultValue) {
        return timeFormatter.getTimeFromConfig(path, defaultValue);
    }

    public void refreshTimeCache() {
        if (timeFormatter != null) {
            timeFormatter.clearCache();
        }
    }
}