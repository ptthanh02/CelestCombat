package dev.nighter.celestCombat.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nighter.celestCombat.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UpdateChecker implements Listener {
    private final JavaPlugin plugin;
    private final String projectId = "sJEHxBdW";
    private boolean updateAvailable = false;
    private final String currentVersion;
    private String latestVersion = "";
    private String downloadUrl = "";
    private String directLink = "";

    // Console colors
    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONSOLE_BRIGHT_GREEN = "\u001B[92m";
    private static final String CONSOLE_YELLOW = "\u001B[33m";
    private static final String CONSOLE_BRIGHT_BLUE = "\u001B[94m";
    private static final String CONSOLE_LAVENDER = "\u001B[38;5;183m";
    private static final String CONSOLE_PINK = "\u001B[38;5;206m";
    private static final String CONSOLE_DEEP_PINK = "\u001B[38;5;198m";

    // Track players who have received an update notification today
    private final Map<UUID, LocalDate> notifiedPlayers = new HashMap<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Check for updates asynchronously on plugin startup
        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate) {
                displayConsoleUpdateMessage();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Displays a fancy update message in the console
     */
    private void displayConsoleUpdateMessage() {
        String modrinthLink = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;
        String frameColor = CONSOLE_BRIGHT_BLUE;

        plugin.getLogger().info(frameColor +
                "────────────────────────────────────────────────────" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor + CONSOLE_BRIGHT_GREEN +
                "         🌟 Celest Combat Update Available 🌟" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor +
                "────────────────────────────────────────────────────" + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "📦 Current version: " + CONSOLE_YELLOW  + formatConsoleText(currentVersion, 31) + CONSOLE_RESET);
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "✅ Latest version: " + CONSOLE_BRIGHT_GREEN + formatConsoleText(latestVersion, 32) + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "📥 Download the latest version at:" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor + " " +
                CONSOLE_BRIGHT_GREEN + formatConsoleText(modrinthLink, 51) + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                "────────────────────────────────────────────────────" + CONSOLE_RESET);
    }

    /**
     * Format text to fit within console box with padding
     */
    private String formatConsoleText(String text, int maxLength) {
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text + " ".repeat(maxLength - text.length());
    }

    /**
     * Checks for updates from Modrinth
     * @return CompletableFuture that resolves to true if an update is available
     */
    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // String currentVersion = "0.0.0";
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectId + "/version");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "CelestCombat-UpdateChecker/1.0");

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("Failed to check for updates. HTTP Error: " + connection.getResponseCode());
                    return false;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = reader.lines().collect(Collectors.joining("\n"));
                reader.close();

                JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
                if (versions.isEmpty()) {
                    return false;
                }

                // Find the latest version
                JsonObject latestVersionObj = null;
                for (JsonElement element : versions) {
                    JsonObject version = element.getAsJsonObject();
                    // Skip pre-releases by checking if version_type is "release"
                    String versionType = version.get("version_type").getAsString();
                    if (versionType.equals("release")) {
                        if (latestVersionObj == null) {
                            latestVersionObj = version;
                        } else {
                            // Compare date_published to find the newest
                            String currentDate = latestVersionObj.get("date_published").getAsString();
                            String newDate = version.get("date_published").getAsString();
                            if (newDate.compareTo(currentDate) > 0) {
                                latestVersionObj = version;
                            }
                        }
                    }
                }

                if (latestVersionObj == null) {
                    return false;
                }

                latestVersion = latestVersionObj.get("version_number").getAsString();
                String versionId = latestVersionObj.get("id").getAsString();

                // Create proper Modrinth page link (instead of direct download)
                downloadUrl = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;

                // Also save direct link (but don't display it)
                JsonArray files = latestVersionObj.getAsJsonArray("files");
                if (!files.isEmpty()) {
                    JsonObject primaryFile = files.get(0).getAsJsonObject();
                    directLink = primaryFile.get("url").getAsString();
                }

                // Compare versions using the Version class
                Version latest = new Version(latestVersion);
                Version current = new Version(currentVersion);

                // If latest version is greater than current version, an update is available
                updateAvailable = latest.compareTo(current) > 0;
                return updateAvailable;

            } catch (Exception e) {
                plugin.getLogger().warning("Error checking for updates: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Sends a beautiful update notification to a player
     *
     * @param player The player to notify
     */
    private void sendUpdateNotification(Player player) {
        if (!updateAvailable || !player.hasPermission("celestcombat.update.notify")) {
            return;
        }

        TextColor primaryBlue = TextColor.fromHexString("#3B82F6"); // Brighter blue
        TextColor green = TextColor.fromHexString("#22C55E"); // Vibrant green
        TextColor redPink = TextColor.fromHexString("#EF4444"); // Bold red
        TextColor orange = TextColor.fromHexString("#F97316"); // Rich orange
        TextColor white = TextColor.fromHexString("#F3F4F6"); // Softer white

        Component borderTop = Component.text("───── CelestCombat Update ─────").color(primaryBlue);
        Component borderBottom = Component.text("───────────────────────").color(primaryBlue);

        Component updateMsg = Component.text("➤ New update available!").color(green);

        Component versionsComponent = Component.text("✦ Current: ")
                .color(white)
                .append(Component.text(currentVersion).color(redPink))
                .append(Component.text("  ✦ Latest: ").color(white))
                .append(Component.text(latestVersion).color(green));

        Component downloadButton = Component.text("▶ [Click to download latest version]")
                .color(orange)
                .clickEvent(ClickEvent.openUrl(downloadUrl))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Download version ")
                                .color(white)
                                .append(Component.text(latestVersion).color(green))
                ));

        player.sendMessage(" ");
        player.sendMessage(borderTop);
        player.sendMessage(" ");
        player.sendMessage(updateMsg);
        player.sendMessage(versionsComponent);
        player.sendMessage(downloadButton);
        player.sendMessage(" ");
        player.sendMessage(borderBottom);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player has permission and if there's an update
        if (player.hasPermission("celestcombat.update.notify")) {

            UUID playerId = player.getUniqueId();
            LocalDate today = LocalDate.now();

            // Clean up old notifications
            notifiedPlayers.entrySet().removeIf(entry -> entry.getValue().isBefore(today));

            // Check if the player has already been notified today
            if (notifiedPlayers.containsKey(playerId) && notifiedPlayers.get(playerId).isEqual(today)) {
                return; // Already notified today
            }

            if (updateAvailable) {
                // Wait a bit before sending the notification
                Scheduler.runTaskLater(() -> {
                    sendUpdateNotification(player);
                    notifiedPlayers.put(playerId, today); // Mark as notified after sending
                }, 40L);
            } else {
                // Re-check for updates when an operator joins, but only if we haven't found an update yet
                checkForUpdates().thenAccept(hasUpdate -> {
                    if (hasUpdate) {
                        Scheduler.runTask(() -> {
                            sendUpdateNotification(player);
                            notifiedPlayers.put(playerId, today); // Mark as notified after sending
                        });
                    }
                });
                // Do NOT mark as notified here if no update is found yet
            }
        }
    }
}