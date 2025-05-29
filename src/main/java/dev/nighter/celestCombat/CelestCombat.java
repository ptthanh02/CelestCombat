package dev.nighter.celestCombat;

import com.sk89q.worldguard.WorldGuard;
import dev.nighter.celestCombat.bstats.Metrics;
import dev.nighter.celestCombat.combat.CombatManager;
import dev.nighter.celestCombat.combat.DeathAnimationManager;
import dev.nighter.celestCombat.commands.CommandManager;
import dev.nighter.celestCombat.configs.TimeFormatter;
import dev.nighter.celestCombat.language.LanguageManager;
import dev.nighter.celestCombat.language.MessageService;
import dev.nighter.celestCombat.listeners.CombatListeners;
import dev.nighter.celestCombat.listeners.EnderPearlListener;
import dev.nighter.celestCombat.hooks.protection.WorldGuardHook;
import dev.nighter.celestCombat.listeners.ItemRestrictionListener;
import dev.nighter.celestCombat.listeners.TridentListener;
import dev.nighter.celestCombat.protection.NewbieProtectionManager;
import dev.nighter.celestCombat.rewards.KillRewardManager;
import dev.nighter.celestCombat.updates.ConfigUpdater;
import dev.nighter.celestCombat.updates.LanguageUpdater;
import dev.nighter.celestCombat.updates.UpdateChecker;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Accessors(chain = false)
public final class CelestCombat extends JavaPlugin {
    @Getter
    private static CelestCombat instance;
    private final boolean debugMode = getConfig().getBoolean("debug", false);
    private LanguageManager languageManager;
    private MessageService messageService;
    private UpdateChecker updateChecker;
    private ConfigUpdater configUpdater;
    private LanguageUpdater languageUpdater;
    private TimeFormatter timeFormatter;
    private CommandManager commandManager;
    private CombatManager combatManager;
    private KillRewardManager killRewardManager;
    private CombatListeners combatListeners;
    private EnderPearlListener enderPearlListener;
    private TridentListener tridentListener;
    private DeathAnimationManager deathAnimationManager;
    private NewbieProtectionManager newbieProtectionManager;
    private WorldGuardHook worldGuardHook;

    public static boolean hasWorldGuard = false;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        saveDefaultConfig();
        checkProtectionPlugins();

        languageManager = new LanguageManager(this, LanguageManager.LanguageFileType.MESSAGES);
        languageUpdater = new LanguageUpdater(this, LanguageUpdater.LanguageFileType.MESSAGES);
        languageUpdater.checkAndUpdateLanguageFiles();

        messageService = new MessageService(this, languageManager);
        updateChecker = new UpdateChecker(this);
        configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        timeFormatter = new TimeFormatter(this);

        deathAnimationManager = new DeathAnimationManager(this);
        combatManager = new CombatManager(this);
        killRewardManager = new KillRewardManager(this);
        newbieProtectionManager = new NewbieProtectionManager(this);
        combatListeners = new CombatListeners(this);
        getServer().getPluginManager().registerEvents(combatListeners, this);

        enderPearlListener = new EnderPearlListener(this, combatManager);
        getServer().getPluginManager().registerEvents(enderPearlListener, this);

        tridentListener = new TridentListener(this, combatManager);
        getServer().getPluginManager().registerEvents(tridentListener, this);

        getServer().getPluginManager().registerEvents(new ItemRestrictionListener(this, combatManager), this);

        if (hasWorldGuard && getConfig().getBoolean("safezone_protection.enabled", true)) {
            worldGuardHook = new WorldGuardHook(this, combatManager);
            getServer().getPluginManager().registerEvents(worldGuardHook, this);
            debug("WorldGuard safezone protection enabled");
        } else if(hasWorldGuard) {
            getLogger().info("Found WorldGuard but safe zone barrier is disabled in config.");
        }

        commandManager = new CommandManager(this);
        commandManager.registerCommands();

        setupBtatsMetrics();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("CelestCombat has been enabled! (Loaded in " + loadTime + "ms)");
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.shutdown();
        }

        if(combatListeners != null) {
            combatListeners.shutdown();
        }

        if (enderPearlListener != null) {
            enderPearlListener.shutdown();
        }

        if (tridentListener != null) {
            tridentListener.shutdown();
        }

        if (worldGuardHook != null) {
            worldGuardHook.cleanup();
        }

        if (killRewardManager != null) {
            killRewardManager.shutdown();
        }

        if (newbieProtectionManager != null) {
            newbieProtectionManager.shutdown();
        }

        getLogger().info("CelestCombat has been disabled!");
    }

    private void checkProtectionPlugins() {
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
            Metrics metrics = new Metrics(this, 25387);
            metrics.addCustomChart(new Metrics.SimplePie("players",
                    () -> String.valueOf(Bukkit.getOnlinePlayers().size())));
        });
    }

    public long getTimeFromConfig(String path, String defaultValue) {
        return timeFormatter.getTimeFromConfig(path, defaultValue);
    }

    public long getTimeFromConfigInMilliseconds(String path, String defaultValue) {
        long ticks = timeFormatter.getTimeFromConfig(path, defaultValue);
        return ticks * 50L; // Convert ticks to milliseconds
    }

    public void refreshTimeCache() {
        if (timeFormatter != null) {
            timeFormatter.clearCache();
        }
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void reload() {
        if (worldGuardHook != null) {
            worldGuardHook.cleanup();
        }
    }
}