package RayTraceAntiEntityESP.paper.utils;

import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapterFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static RayTraceAntiEntityESP.paper.Main.plugin;

public final class VersionChecker {

    private static final String GITHUB_REPO = "Br1ghtF0r3v3r/RayTraceAntiEntityESP";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static volatile String currentVersion = null;
    private static volatile String latestVersion = null;
    private static volatile boolean updateAvailable = false;

    private VersionChecker() {
    }

    public static void check() {
        SchedulerAdapterFactory.get().runTaskAsynchronously(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URI(API_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "RayTraceAntiEntityESP-UpdateChecker");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() != 200) return;

                JsonObject release;
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    release = JsonParser.parseReader(reader).getAsJsonObject();
                }
                if (release == null || !release.has("tag_name") || release.get("tag_name").isJsonNull()) return;

                latestVersion = stripVersionPrefix(release.get("tag_name").getAsString());
                currentVersion = stripVersionPrefix(plugin.getPluginMeta().getVersion());

                if (isNewer(latestVersion, currentVersion)) {
                    updateAvailable = true;
                    plugin.getLogger().warning("=================================");
                    plugin.getLogger().warning("A new version is available!");
                    plugin.getLogger().warning("Current: v" + currentVersion);
                    plugin.getLogger().warning("Latest:  v" + latestVersion);
                    plugin.getLogger().warning("https://github.com/" + GITHUB_REPO + "/releases/latest");
                    plugin.getLogger().warning("=================================");
                } else {
                    plugin.getLogger().info("Plugin is up to date! (v" + currentVersion + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    static String stripVersionPrefix(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!v.isEmpty() && (v.charAt(0) == 'v' || v.charAt(0) == 'V')) v = v.substring(1);
        return v;
    }

    static boolean isNewer(String latest, String current) {
        int[] a = numericParts(latest);
        int[] b = numericParts(current);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] numericParts(String version) {
        String core = version;
        int cut = core.indexOf('-');
        if (cut >= 0) core = core.substring(0, cut);
        cut = core.indexOf('+');
        if (cut >= 0) core = core.substring(0, cut);
        String[] pieces = core.split("\\.");
        int[] out = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                out[i] = Integer.parseInt(pieces[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    public static void notifyIfOutdated(Player player) {
        if (!updateAvailable) return;
        String[] lines = {
                "&e=================================",
                "&eA new version is available!",
                "&eCurrent: &fv" + currentVersion,
                "&eLatest:  &fv" + latestVersion,
                "&fhttps://github.com/" + GITHUB_REPO + "/releases/latest",
                "&e================================="
        };
        for (String line : lines) {
            player.sendMessage(StringFormat.formatToString(player, line));
        }
    }
}
