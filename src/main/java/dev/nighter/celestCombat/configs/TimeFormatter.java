package dev.nighter.celestCombat.configs;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeFormatter {
    private final JavaPlugin plugin;

    // Cache for storing parsed time values
    private final Map<String, Long> timeCache = new ConcurrentHashMap<>();

    // Constants for time conversion to ticks (1 tick = 1/20 second)
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60L;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60L;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24L;
    private static final long TICKS_PER_WEEK = TICKS_PER_DAY * 7L;
    private static final long TICKS_PER_MONTH = TICKS_PER_DAY * 30L; // Approximation
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365L; // Approximation

    // Pattern to match time format: 1y_2mo_3w_4d_5h_6m_7s
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?:(\\d+)y)?_?(?:(\\d+)mo)?_?(?:(\\d+)w)?_?(?:(\\d+)d)?_?(?:(\\d+)h)?_?(?:(\\d+)m)?_?(?:(\\d+)s)?",
            Pattern.CASE_INSENSITIVE);

    // Pattern for simple time format: 5s, 10m, etc.
    private static final Pattern SIMPLE_TIME_PATTERN = Pattern.compile(
            "(\\d+)([smhdwmoy])", Pattern.CASE_INSENSITIVE);

    private static final Map<String, Long> TIME_UNIT_MULTIPLIERS = new HashMap<>();

    static {
        TIME_UNIT_MULTIPLIERS.put("s", TICKS_PER_SECOND);
        TIME_UNIT_MULTIPLIERS.put("m", TICKS_PER_MINUTE);
        TIME_UNIT_MULTIPLIERS.put("h", TICKS_PER_HOUR);
        TIME_UNIT_MULTIPLIERS.put("d", TICKS_PER_DAY);
        TIME_UNIT_MULTIPLIERS.put("w", TICKS_PER_WEEK);
        TIME_UNIT_MULTIPLIERS.put("mo", TICKS_PER_MONTH);
        TIME_UNIT_MULTIPLIERS.put("y", TICKS_PER_YEAR);
    }

    public TimeFormatter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets a time value from config and converts it to ticks with a default fallback
     * Uses cache to improve performance for repeated calls with the same path
     * Supports formats:
     * - Simple: "5s", "10m", "1h", etc.
     * - Complex: "1y_2mo_3w_4d_5h_6m_7s"
     * - Numeric: Direct tick value
     *
     * @param path The config path
     * @param defaultValue Default value if path doesn't exist
     * @return Time in ticks
     */
    public long getTimeFromConfig(String path, String defaultValue) {
        // Check if the value is already in cache
        String cacheKey = path + ":" + defaultValue;
        if (timeCache.containsKey(cacheKey)) {
            return timeCache.get(cacheKey);
        }

        String timeString = plugin.getConfig().getString(path, defaultValue);
        long result = parseTimeToTicks(timeString, -1L);

        if (result == -1L) {
            // Parsing failed
            plugin.getLogger().warning("Failed to parse time value for '" + path +
                    "' (value: '" + timeString + "'). Using 1h as fallback.");
            result = 3600L * 20L; // 1 hour in ticks
        }

        // Cache the result
        timeCache.put(cacheKey, result);
        return result;
    }

    /**
     * Gets a time value from config and converts it to ticks
     * Uses cache to improve performance for repeated calls with the same path
     * Supports formats:
     * - Simple: "5s", "10m", "1h", etc.
     * - Complex: "1y_2mo_3w_4d_5h_6m_7s"
     * - Numeric: Direct tick value
     *
     * @param path The config path
     * @param defaultValue Default value in ticks if path doesn't exist
     * @return Time in ticks
     */
    public long getTimeInTicks(String path, long defaultValue) {
        // Check if the value is already in cache
        String cacheKey = path + ":" + defaultValue;
        if (timeCache.containsKey(cacheKey)) {
            return timeCache.get(cacheKey);
        }

        String timeString = plugin.getConfig().getString(path);
        if (timeString == null) {
            // Cache the default value
            timeCache.put(cacheKey, defaultValue);
            return defaultValue;
        }

        long result = parseTimeToTicks(timeString, defaultValue);

        // Cache the result
        timeCache.put(cacheKey, result);
        return result;
    }

    /**
     * Clears the time cache.
     * This should be called when the config is reloaded.
     */
    public void clearCache() {
        timeCache.clear();
        plugin.getLogger().info("Time formatter cache cleared.");
    }

    /**
     * Parses a time string to ticks
     *
     * @param timeString The time string to parse
     * @param defaultValue Default value if parsing fails
     * @return Time in ticks
     */
    public long parseTimeToTicks(String timeString, long defaultValue) {
        timeString = timeString.trim();

        // Try parsing as a direct number
        try {
            return Long.parseLong(timeString);
        } catch (NumberFormatException ignored) {
            // Not a direct number, continue with time format parsing
        }

        // Try complex format first: 1y_2mo_3w_4d_5h_6m_7s
        Matcher complexMatcher = TIME_PATTERN.matcher(timeString);
        if (complexMatcher.matches()) {
            long ticks = 0L;

            if (complexMatcher.group(1) != null) // Years
                ticks += Long.parseLong(complexMatcher.group(1)) * TICKS_PER_YEAR;

            if (complexMatcher.group(2) != null) // Months
                ticks += Long.parseLong(complexMatcher.group(2)) * TICKS_PER_MONTH;

            if (complexMatcher.group(3) != null) // Weeks
                ticks += Long.parseLong(complexMatcher.group(3)) * TICKS_PER_WEEK;

            if (complexMatcher.group(4) != null) // Days
                ticks += Long.parseLong(complexMatcher.group(4)) * TICKS_PER_DAY;

            if (complexMatcher.group(5) != null) // Hours
                ticks += Long.parseLong(complexMatcher.group(5)) * TICKS_PER_HOUR;

            if (complexMatcher.group(6) != null) // Minutes
                ticks += Long.parseLong(complexMatcher.group(6)) * TICKS_PER_MINUTE;

            if (complexMatcher.group(7) != null) // Seconds
                ticks += Long.parseLong(complexMatcher.group(7)) * TICKS_PER_SECOND;

            return ticks;
        }

        // Try simple format: 5s, 10m, etc.
        Matcher simpleMatcher = SIMPLE_TIME_PATTERN.matcher(timeString);
        if (simpleMatcher.matches()) {
            long value = Long.parseLong(simpleMatcher.group(1));
            String unit = simpleMatcher.group(2).toLowerCase();

            // Convert 'mo' for month since it's two characters
            if (unit.equals("o") && timeString.toLowerCase().endsWith("mo")) {
                unit = "mo";
            }

            Long multiplier = TIME_UNIT_MULTIPLIERS.get(unit);
            if (multiplier != null) {
                return value * multiplier;
            }
        }

        // If all parsing attempts fail, log warning and return default
        plugin.getLogger().warning("Invalid time format for '" + timeString + "', using default value");
        return defaultValue;
    }

    /**
     * Formats ticks into a human-readable time string
     *
     * @param ticks The time in ticks
     * @return Formatted time string
     */
    public String formatTicks(long ticks) {
        StringBuilder builder = new StringBuilder();

        long years = ticks / TICKS_PER_YEAR;
        if (years > 0) {
            builder.append(years).append("y_");
            ticks %= TICKS_PER_YEAR;
        }

        long months = ticks / TICKS_PER_MONTH;
        if (months > 0) {
            builder.append(months).append("mo_");
            ticks %= TICKS_PER_MONTH;
        }

        long weeks = ticks / TICKS_PER_WEEK;
        if (weeks > 0) {
            builder.append(weeks).append("w_");
            ticks %= TICKS_PER_WEEK;
        }

        long days = ticks / TICKS_PER_DAY;
        if (days > 0) {
            builder.append(days).append("d_");
            ticks %= TICKS_PER_DAY;
        }

        long hours = ticks / TICKS_PER_HOUR;
        if (hours > 0) {
            builder.append(hours).append("h_");
            ticks %= TICKS_PER_HOUR;
        }

        long minutes = ticks / TICKS_PER_MINUTE;
        if (minutes > 0) {
            builder.append(minutes).append("m_");
            ticks %= TICKS_PER_MINUTE;
        }

        long seconds = ticks / TICKS_PER_SECOND;
        if (seconds > 0 || builder.length() == 0) {
            builder.append(seconds).append("s");
        } else if (builder.length() > 0) {
            // Remove trailing underscore
            builder.setLength(builder.length() - 1);
        }

        return builder.toString();
    }
}