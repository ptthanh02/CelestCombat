package dev.nighter.celestCombat.updates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class ConfigUpdater {
    private final String currentVersion;
    private final JavaPlugin plugin;
    private static final String CONFIG_VERSION_KEY = "config_version";

    public ConfigUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Check if the config needs to be updated and update it if necessary
     */
    public void checkAndUpdateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // If config doesn't exist, create it with the version header
        if (!configFile.exists()) {
            createDefaultConfigWithHeader(configFile);
            return;
        }

        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        String configVersionStr = currentConfig.getString(CONFIG_VERSION_KEY, "0.0.0");
        Version configVersion = new Version(configVersionStr);
        Version pluginVersion = new Version(currentVersion);

        if (configVersion.compareTo(pluginVersion) >= 0) {
            return;
        }

        if (!configVersionStr.equals("0.0.0")) {
            plugin.getLogger().info("Updating config from version " + configVersionStr + " to " + currentVersion);
        }

        try {
            Map<String, Object> userValues = flattenConfig(currentConfig);

            // Create temp file with new default config
            File tempFile = new File(plugin.getDataFolder(), "config_new.yml");
            createDefaultConfigWithHeader(tempFile);

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(CONFIG_VERSION_KEY, currentVersion);

            // Check if there are actual differences before creating backup
            boolean configDiffers = hasConfigDifferences(userValues, newConfig);

            if (configDiffers) {
                File backupFile = new File(plugin.getDataFolder(), "config_backup_" + configVersionStr + ".yml");
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Config backup created at " + backupFile.getName());
            } else {
                if (!configVersionStr.equals("0.0.0")) {
                    plugin.getLogger().info("No significant config changes detected, skipping backup creation");
                }
            }

            // Apply user values and save
            applyUserValues(newConfig, userValues);
            newConfig.save(configFile);
            tempFile.delete();
            plugin.reloadConfig();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determines if there are actual differences between old and new config
     */
    private boolean hasConfigDifferences(Map<String, Object> userValues, FileConfiguration newConfig) {
        // Get all paths from new config (excluding config_version)
        Map<String, Object> newConfigMap = flattenConfig(newConfig);

        // Check for removed or changed keys
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object oldValue = entry.getValue();

            // Skip config_version key
            if (path.equals(CONFIG_VERSION_KEY)) continue;

            // Check if path no longer exists
            if (!newConfig.contains(path)) {
                return true; // Found a removed path
            }

            // Check if default value changed
            Object newDefaultValue = newConfig.get(path);
            if (newDefaultValue != null && !newDefaultValue.equals(oldValue)) {
                return true; // Default value changed
            }
        }

        // Check for new keys
        for (String path : newConfigMap.keySet()) {
            if (!path.equals(CONFIG_VERSION_KEY) && !userValues.containsKey(path)) {
                return true; // Found a new path
            }
        }

        return false; // No significant differences
    }

    private void createDefaultConfigWithHeader(File destinationFile) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines()
                        .toList();

                List<String> newLines = new ArrayList<>();
                newLines.add("# Configuration version - Do not modify this value");
                newLines.add(CONFIG_VERSION_KEY + ": " + currentVersion);
                newLines.add("");
                newLines.addAll(defaultLines);

                Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
            } else {
                plugin.getLogger().warning("Default config.yml not found in the plugin's resources.");
                destinationFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default config with header: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Flattens a configuration section into a map of path -> value
     */
    private Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                result.put(key, config.get(key));
            }
        }
        return result;
    }

    /**
     * Applies the user values to the new config
     */
    private void applyUserValues(FileConfiguration newConfig, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            // Don't override config_version
            if (path.equals(CONFIG_VERSION_KEY)) continue;

            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().warning("Config path '" + path + "' from old config no longer exists in new config");
            }
        }
    }
}