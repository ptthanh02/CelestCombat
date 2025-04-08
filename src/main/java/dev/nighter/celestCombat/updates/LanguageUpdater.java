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

public class LanguageUpdater {
    private final String currentVersion;
    private final JavaPlugin plugin;
    private static final String LANGUAGE_VERSION_KEY = "language_version";
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("en_US", "vi_VN");

    public LanguageUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Check and update all language files
     */
    public void checkAndUpdateLanguageFiles() {
        for (String language : SUPPORTED_LANGUAGES) {
            File langDir = new File(plugin.getDataFolder(), "language/" + language);

            // Check and update each language file
            File messageFile = new File(langDir, "messages.yml");
            updateLanguageFile(language, messageFile);
        }
    }

    /**
     * Update a specific language file
     */
    private void updateLanguageFile(String language, File messageFile) {
        try {
            FileConfiguration currentMessages = YamlConfiguration.loadConfiguration(messageFile);
            String messageVersionStr = currentMessages.getString(LANGUAGE_VERSION_KEY, "0.0.0");
            Version messageVersion = new Version(messageVersionStr);
            Version pluginVersion = new Version(currentVersion);

            if (messageVersion.compareTo(pluginVersion) >= 0) {
                return; // No update needed
            }

            if (!messageVersionStr.equals("0.0.0")) {
                plugin.getLogger().info("Updating " + language + " messages from version " + messageVersionStr + " to " + currentVersion);
            }

            // Store user's current values
            Map<String, Object> userValues = flattenConfig(currentMessages);

            // Create temp file with new default messages
            File tempFile = new File(plugin.getDataFolder(), "language/" + language + "/messages_new.yml");
            createDefaultLanguageFileWithHeader(language, tempFile);

            FileConfiguration newMessages = YamlConfiguration.loadConfiguration(tempFile);
            newMessages.set(LANGUAGE_VERSION_KEY, currentVersion);

            // Check if there are actual differences before creating backup
            boolean messagesDiffer = hasConfigDifferences(userValues, newMessages);

            if (messagesDiffer) {
                File backupFile = new File(plugin.getDataFolder(), "language/" + language + "/messages_backup_" + messageVersionStr + ".yml");
                Files.copy(messageFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info(language + " messages backup created at " + backupFile.getName());
            } else {
                if (!messageVersionStr.equals("0.0.0")) {
                    plugin.getLogger().info("No significant changes detected in " + language + " messages, skipping backup creation");
                }
            }

            // Apply user values and save
            applyUserValues(newMessages, userValues);
            newMessages.save(messageFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update " + language + " messages: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Create a default language file with a version header
     */
    private void createDefaultLanguageFileWithHeader(String language, File destinationFile) {
        try (InputStream in = plugin.getResource("language/" + language + "/messages.yml")) {
            if (in != null) {
                List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines()
                        .toList();

                List<String> newLines = new ArrayList<>();
                newLines.add("# Language file version - Do not modify this value");
                newLines.add(LANGUAGE_VERSION_KEY + ": " + currentVersion);
                newLines.add("");
                newLines.addAll(defaultLines);

                destinationFile.getParentFile().mkdirs();
                Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
            } else {
                plugin.getLogger().warning("Default messages.yml for " + language + " not found in the plugin's resources.");
                destinationFile.getParentFile().mkdirs();
                destinationFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default language file for " + language + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determines if there are actual differences between old and new messages
     */
    private boolean hasConfigDifferences(Map<String, Object> userValues, FileConfiguration newConfig) {
        // Get all paths from new config (excluding version key)
        Map<String, Object> newConfigMap = flattenConfig(newConfig);

        // Check for removed or changed keys
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object oldValue = entry.getValue();

            // Skip version key
            if (path.equals(LANGUAGE_VERSION_KEY)) continue;

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
            if (!path.equals(LANGUAGE_VERSION_KEY) && !userValues.containsKey(path)) {
                return true; // Found a new path
            }
        }

        return false; // No significant differences
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
     * Applies the user values to the new messages
     */
    private void applyUserValues(FileConfiguration newConfig, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            // Don't override version key
            if (path.equals(LANGUAGE_VERSION_KEY)) continue;

            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().warning("Message path '" + path + "' from old messages no longer exists in new messages");
            }
        }
    }
}