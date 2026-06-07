package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.misc.StringFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class VersionChecker {

    private static final String GITHUB_REPO = "Br1ghtF0r3v3r/RayTraceAntiEntityESP";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases";

    private static String latestVersion = null;
    private static boolean updateAvailable = false;

    public static void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URI(API_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) return;

                JsonArray releases = JsonParser.parseReader(
                        new InputStreamReader(conn.getInputStream())
                ).getAsJsonArray();

                if (releases.isEmpty()) return;

                latestVersion = releases.get(0).getAsJsonObject()
                        .get("tag_name").getAsString()
                        .replace("v", "").trim();

                String current = plugin.getPluginMeta().getVersion().trim();

                if (!current.equals(latestVersion)) {
                    updateAvailable = true;
                    plugin.getLogger().warning("=================================");
                    plugin.getLogger().warning("A new version is available!");
                    plugin.getLogger().warning("Current: v" + current);
                    plugin.getLogger().warning("Latest:  v" + latestVersion);
                    plugin.getLogger().warning("https://github.com/" + GITHUB_REPO + "/releases/latest");
                    plugin.getLogger().warning("=================================");
                } else {
                    plugin.getLogger().info("Plugin is up to date! (v" + current + ")");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    public static void notifyIfOutdated(Player player) {
        if (!updateAvailable) return;
        player.sendMessage(StringFormat.formatToString(player,
                "&e[RayTraceAntiEntityESP] &aUpdate available! &fv" + latestVersion +
                        " &7→ &fhttps://github.com/" + GITHUB_REPO + "/releases/latest"));
    }
}