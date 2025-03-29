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
import java.util.stream.Collectors;

public class ConfigUpdater {
    private final String currentVersion;
    private final JavaPlugin plugin;
    private static final String CONFIG_VERSION_KEY = "config-version";

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

        plugin.getLogger().info("Updating config from version " + configVersionStr + " to " + currentVersion);

        try {
            File backupFile = new File(plugin.getDataFolder(), "config_backup_" + configVersionStr + ".yml");
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> userValues = flattenConfig(currentConfig);

            File tempFile = new File(plugin.getDataFolder(), "config_new.yml");
            createDefaultConfigWithHeader(tempFile); // Create the new default config with the header

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(CONFIG_VERSION_KEY, currentVersion);
            applyUserValues(newConfig, userValues);
            newConfig.save(configFile);
            tempFile.delete();
            plugin.reloadConfig();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createDefaultConfigWithHeader(File destinationFile) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.toList());

                List<String> newLines = new ArrayList<>();
                newLines.add("# Configuration version - Do not modify this value");
                newLines.add("config-version: " + currentVersion);
                newLines.add("");
                newLines.addAll(defaultLines);

                Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
            } else {
                plugin.getLogger().warning("Default config.yml not found in the plugin's resources.");
                // You might want to create an empty config file here or handle this differently
                destinationFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default config with header: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveResource(String resourcePath, File destination) throws IOException {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }

        if (!destination.exists()) {
            destination.createNewFile();
        }

        try (OutputStream out = Files.newOutputStream(destination.toPath())) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            in.close();
        }
    }

    /**
     * Flattens a configuration section into a map of path -> value
     */
    private Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!key.equals(CONFIG_VERSION_KEY) && !config.isConfigurationSection(key)) {
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
            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().warning("Config path '" + path + "' from old config no longer exists in new config");
            }
        }
    }
}