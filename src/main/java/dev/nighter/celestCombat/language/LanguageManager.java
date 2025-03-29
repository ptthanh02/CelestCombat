package dev.nighter.celestCombat.language;

import dev.nighter.celestCombat.CelestCombat;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

public class LanguageManager {
    private final CelestCombat plugin;
    @Getter private String defaultLocale;
    private final Map<String, LocaleData> localeMap = new HashMap<>();
    private final Set<String> activeLocales = new HashSet<>();
    private final Set<LanguageFileType> activeFileTypes = new HashSet<>();

    // Enum to represent the different language file types
    @Getter
    public enum LanguageFileType {
        MESSAGES("messages.yml"),
        GUI("gui.yml"),
        FORMATTING("formatting.yml");

        private final String fileName;

        LanguageFileType(String fileName) {
            this.fileName = fileName;
        }
    }

    public LanguageManager(CelestCombat plugin) {
        this.plugin = plugin;
        this.defaultLocale = plugin.getConfig().getString("language", "en_US");
        activeFileTypes.addAll(Arrays.asList(LanguageFileType.values()));
        loadLanguages();
        saveDefaultFiles();
    }

    public LanguageManager(CelestCombat plugin, LanguageFileType... fileTypes) {
        this.plugin = plugin;
        this.defaultLocale = plugin.getConfig().getString("language", "en_US");
        activeFileTypes.addAll(Arrays.asList(fileTypes));
        loadLanguages(fileTypes);
        saveDefaultFiles();
    }

    private void saveDefaultFiles() {
        saveResource("language/vi_VN/messages.yml");
    }

    private void saveResource(String resourcePath) {
        File resourceFile = new File(plugin.getDataFolder(), resourcePath);
        if (!resourceFile.exists()) {
            resourceFile.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    public void loadLanguages() {
        loadLanguages(activeFileTypes.toArray(new LanguageFileType[0]));
    }

    public void loadLanguages(LanguageFileType... fileTypes) {
        File langDir = new File(plugin.getDataFolder(), "language");
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create language directory!");
            return;
        }

        // Load default locale first
        loadLocale(defaultLocale, fileTypes);
        activeLocales.add(defaultLocale);

        // Load other locales
        File[] localeDirs = langDir.listFiles(File::isDirectory);
        if (localeDirs != null) {
            for (File localeDir : localeDirs) {
                String locale = localeDir.getName();
                if (!locale.equals(defaultLocale)) {
                    loadLocale(locale, fileTypes);
                }
            }
        }
    }

    public void reloadLanguages() {
        localeMap.clear();

        // Update the default locale from config when reloading
        this.defaultLocale = plugin.getConfig().getString("language", "en_US");

        // Reload default locale with only the active file types
        loadLocale(defaultLocale, activeFileTypes.toArray(new LanguageFileType[0]));

        // Only reload active locales with active file types
        for (String locale : activeLocales) {
            if (!locale.equals(defaultLocale)) {
                loadLocale(locale, activeFileTypes.toArray(new LanguageFileType[0]));
            }
        }

        plugin.getLogger().info("Successfully reloaded language files for locales: " + String.join(", ", activeLocales));
    }

    public void trackLocaleUsage(String locale) {
        activeLocales.add(locale);
    }

    private void loadLocale(String locale, LanguageFileType... fileTypes) {
        File localeDir = new File(plugin.getDataFolder(), "language/" + locale);
        if (!localeDir.exists() && !localeDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create locale directory for " + locale);
            return;
        }

        // Create and load or update only the specified files
        YamlConfiguration messages = null;
        YamlConfiguration gui = null;
        YamlConfiguration formatting = null;

        for (LanguageFileType fileType : fileTypes) {
            switch (fileType) {
                case MESSAGES:
                    messages = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case GUI:
                    gui = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case FORMATTING:
                    formatting = loadOrCreateFile(locale, fileType.getFileName());
                    break;
            }
        }

        // If a file wasn't specified, create an empty configuration
        if (messages == null) messages = new YamlConfiguration();
        if (gui == null) gui = new YamlConfiguration();
        if (formatting == null) formatting = new YamlConfiguration();

        localeMap.put(locale, new LocaleData(messages, gui, formatting));
    }

    private YamlConfiguration loadOrCreateFile(String locale, String fileName) {
        File file = new File(plugin.getDataFolder(), "language/" + locale + "/" + fileName);
        YamlConfiguration defaultConfig = new YamlConfiguration();
        YamlConfiguration userConfig = new YamlConfiguration();

        // Check if the default resource exists before trying to load it
        boolean defaultResourceExists = plugin.getResource("language/" + defaultLocale + "/" + fileName) != null;

        // Load default configuration from resources if it exists
        if (defaultResourceExists) {
            try (InputStream inputStream = plugin.getResource("language/" + defaultLocale + "/" + fileName)) {
                if (inputStream != null) {
                    defaultConfig.loadFromString(new String(inputStream.readAllBytes()));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load default " + fileName, e);
            }
        }

        // Create file if it doesn't exist and the default resource exists
        if (!file.exists() && defaultResourceExists) {
            try (InputStream inputStream = plugin.getResource("language/" + defaultLocale + "/" + fileName)) {
                if (inputStream != null) {
                    file.getParentFile().mkdirs(); // Ensure parent directory exists
                    Files.copy(inputStream, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create " + fileName + " for locale " + locale, e);
                return new YamlConfiguration(); // Return empty config to avoid further errors
            }
        }

        // Load user configuration if file exists
        if (file.exists()) {
            try {
                userConfig.load(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load " + fileName + " for locale " + locale + ". Using defaults.", e);
                return defaultConfig; // Return default config if user config can't be loaded
            }

            // Merge configurations (add missing keys from default to user config)
            boolean updated = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!userConfig.contains(key)) {
                    userConfig.set(key, defaultConfig.get(key));
                    updated = true;
                }
            }

            // Save if updated
            if (updated) {
                try {
                    userConfig.save(file);
                    plugin.getLogger().info("Updated " + fileName + " for locale " + locale);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to save updated " + fileName + " for locale " + locale, e);
                }
            }

            return userConfig;
        } else {
            // If file doesn't exist and we couldn't create it, return empty config
            return new YamlConfiguration();
        }
    }

    public String getMessage(String key, String locale, Map<String, String> placeholders) {
        // Track locale usage when accessed
        trackLocaleUsage(locale);

        if (!isMessageEnabled(key, locale)) {
            return null;
        }

        LocaleData localeData = getLocaleData(locale);
        String message = localeData.getMessages().getString(key + ".message");

        if (message == null) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            message = defaultLocaleData.getMessages().getString(key + ".message");

            if (message == null) {
                return "Missing message: " + key;
            }
        }

        // Apply prefix
        String prefix = getPrefix(locale);
        message = prefix + message;

        // Apply placeholders and color formatting
        return applyPlaceholdersAndColors(message, placeholders);
    }

    public String getTitle(String key, String locale, Map<String, String> placeholders) {
        trackLocaleUsage(locale);
        if (!isMessageEnabled(key, locale)) {
            return null;
        }
        return getRawMessage(key + ".title", locale, placeholders);
    }

    public String getSubtitle(String key, String locale, Map<String, String> placeholders) {
        trackLocaleUsage(locale);
        if (!isMessageEnabled(key, locale)) {
            return null;
        }
        return getRawMessage(key + ".subtitle", locale, placeholders);
    }

    public String getActionBar(String key, String locale, Map<String, String> placeholders) {
        trackLocaleUsage(locale);
        if (!isMessageEnabled(key, locale)) {
            return null;
        }
        return getRawMessage(key + ".action_bar", locale, placeholders);
    }

    public String getSound(String key, String locale) {
        trackLocaleUsage(locale);
        if (!isMessageEnabled(key, locale)) {
            return null;
        }

        LocaleData localeData = getLocaleData(locale);
        String sound = localeData.getMessages().getString(key + ".sound");

        if (sound == null) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            sound = defaultLocaleData.getMessages().getString(key + ".sound");
        }

        return sound;
    }

    public String getGuiTitle(String key, String locale, Map<String, String> placeholders) {
        trackLocaleUsage(locale);
        if (!activeFileTypes.contains(LanguageFileType.GUI) || !isGuiElementEnabled(key, locale)) {
            return null;
        }

        LocaleData localeData = getLocaleData(locale);
        String title = localeData.getGui().getString(key);

        if (title == null) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            title = defaultLocaleData.getGui().getString(key);

            if (title == null) {
                return "Missing GUI title: " + key;
            }
        }

        return applyPlaceholdersAndColors(title, placeholders);
    }

    public String getGuiItemName(String key, String locale, Map<String, String> placeholders) {
        trackLocaleUsage(locale);
        if (!activeFileTypes.contains(LanguageFileType.GUI) || !isGuiElementEnabled(key, locale)) {
            return null;
        }

        LocaleData localeData = getLocaleData(locale);
        String name = localeData.getGui().getString(key + ".name");

        if (name == null) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            name = defaultLocaleData.getGui().getString(key + ".name");

            if (name == null) {
                return "Missing item name: " + key;
            }
        }

        return applyPlaceholdersAndColors(name, placeholders);
    }

    public String[] getGuiItemLore(String key, String locale, Map<String, String> placeholders) {
        trackLocaleUsage(locale);
        if (!activeFileTypes.contains(LanguageFileType.GUI) || !isGuiElementEnabled(key, locale)) {
            return new String[0];
        }

        LocaleData localeData = getLocaleData(locale);
        var loreList = localeData.getGui().getStringList(key + ".lore");

        if (loreList.isEmpty()) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            loreList = defaultLocaleData.getGui().getStringList(key + ".lore");
        }

        return loreList.stream()
                .map(line -> applyPlaceholdersAndColors(line, placeholders))
                .toArray(String[]::new);
    }

    public String getGuiItemSound(String key, String locale) {
        trackLocaleUsage(locale);
        if (!activeFileTypes.contains(LanguageFileType.GUI) || !isGuiElementEnabled(key, locale)) {
            return null;
        }

        LocaleData localeData = getLocaleData(locale);
        String sound = localeData.getGui().getString(key + ".sound");

        if (sound == null) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            sound = defaultLocaleData.getGui().getString(key + ".sound");
        }

        return sound;
    }

    public String formatNumber(double number, String locale) {
        trackLocaleUsage(locale);
        if (!activeFileTypes.contains(LanguageFileType.FORMATTING)) {
            // Return a default format if formatting file type is not active
            if (number >= 1_000_000_000_000L) {
                return Math.round(number / 1_000_000_000_000.0 * 10) / 10.0 + "T";
            } else if (number >= 1_000_000_000L) {
                return Math.round(number / 1_000_000_000.0 * 10) / 10.0 + "B";
            } else if (number >= 1_000_000L) {
                return Math.round(number / 1_000_000.0 * 10) / 10.0 + "M";
            } else if (number >= 1_000L) {
                return Math.round(number / 1_000.0 * 10) / 10.0 + "K";
            } else {
                return Math.round(number * 10) / 10.0 + "";
            }
        }

        LocaleData localeData = getLocaleData(locale);
        String format;

        if (number >= 1_000_000_000_000L) {
            format = localeData.getFormatting().getString("format-number.trillion", "%s%T");
            return String.format(format, Math.round(number / 1_000_000_000_000.0 * 10) / 10.0);
        } else if (number >= 1_000_000_000L) {
            format = localeData.getFormatting().getString("format-number.billion", "%s%B");
            return String.format(format, Math.round(number / 1_000_000_000.0 * 10) / 10.0);
        } else if (number >= 1_000_000L) {
            format = localeData.getFormatting().getString("format-number.million", "%s%M");
            return String.format(format, Math.round(number / 1_000_000.0 * 10) / 10.0);
        } else if (number >= 1_000L) {
            format = localeData.getFormatting().getString("format-number.thousand", "%s%K");
            return String.format(format, Math.round(number / 1_000.0 * 10) / 10.0);
        } else {
            format = localeData.getFormatting().getString("format-number.default", "%s%");
            return String.format(format, Math.round(number * 10) / 10.0);
        }
    }

    private String getPrefix(String locale) {
        LocaleData localeData = getLocaleData(locale);
        return localeData.getMessages().getString("prefix", "&7[Server] &r");
    }

    private String getRawMessage(String path, String locale, Map<String, String> placeholders) {
        LocaleData localeData = getLocaleData(locale);
        String message = localeData.getMessages().getString(path);

        if (message == null) {
            // Fallback to default locale
            LocaleData defaultLocaleData = getLocaleData(defaultLocale);
            message = defaultLocaleData.getMessages().getString(path);

            if (message == null) {
                return null;  // Return null instead of error message
            }
        }

        return applyPlaceholdersAndColors(message, placeholders);
    }

    private boolean isMessageEnabled(String key, String locale) {
        LocaleData localeData = getLocaleData(locale);
        // Check if this message has an enabled flag, default to true if not specified
        return localeData.getMessages().getBoolean(key + ".enabled", true);
    }

    private boolean isGuiElementEnabled(String key, String locale) {
        LocaleData localeData = getLocaleData(locale);
        // Check if this GUI element has an enabled flag, default to true if not specified
        return localeData.getGui().getBoolean(key + ".enabled", true);
    }

    private String applyPlaceholdersAndColors(String text, Map<String, String> placeholders) {
        if (text == null) return null;

        // Apply placeholders
        String result = text;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        // Apply hex colors
        result = ColorUtil.translateHexColorCodes(result);

        return result;
    }

    private LocaleData getLocaleData(String locale) {
        return Optional.ofNullable(localeMap.get(locale))
                .orElse(localeMap.get(defaultLocale));
    }
}