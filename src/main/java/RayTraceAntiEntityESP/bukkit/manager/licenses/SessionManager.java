package RayTraceAntiEntityESP.bukkit.manager.licenses;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class SessionManager {

    private static final String BASE_URL = "https://tgixnkhvdhzojjkfjbmg.supabase.co/rest/v1";
    private static final String SESSIONS = BASE_URL + "/license_sessions";
    private static final String LIMITS = BASE_URL + "/license_limits";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRnaXhua2h2ZGh6b2pqa2ZqYm1nIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc0NTMwNjAsImV4cCI6MjA5MzAyOTA2MH0.RbDQDM7UEHj6oF1rEqNkyHy3pKgl1oFXmFuPCK4IYPE";

    private static final int STALE_SECS = 90;
    private static final int HEARTBEAT_SECS = 30;

    private static final HttpClient http = HttpClient.newHttpClient();
    private static BukkitTask heartbeatTask;

    private static final String SERVER_ID = UUID.randomUUID().toString();
    private static String activeLicenseKey;

    public static boolean startSession(String licenseKey, JavaPlugin plugin) {
        try {
            purgeStale(licenseKey);

            int active = countActive(licenseKey);
            int max = fetchMax(licenseKey);

            if (active >= max) {
                plugin.getLogger().severe(
                        "Session limit reached for this license! " +
                                "(" + active + "/" + max + " slots in use). " +
                                "Disable another server or purchase additional slots.");
                return false;
            }

            if (!insertSession(licenseKey)) {
                plugin.getLogger().severe("Failed to register session with Supabase.");
                return false;
            }

            activeLicenseKey = licenseKey;
            startHeartbeat(plugin);
            plugin.getLogger().info("Session claimed [" + (active + 1) + "/" + max + "] server=" + SERVER_ID);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning(
                    "Could not reach session server — running in grace mode: " + e.getMessage());
            return true;
        }
    }

    public static void endSession() {
        stopHeartbeat();
        if (activeLicenseKey == null) return;
        try {
            HttpRequest req = baseRequest(SESSIONS + "?server_id=eq." + SERVER_ID)
                    .DELETE()
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            plugin.getLogger().info("Session released for server=" + SERVER_ID);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not release session: " + e.getMessage());
        } finally {
            activeLicenseKey = null;
        }
    }

    private static void purgeStale(String licenseKey) throws Exception {
        String filter = "?license_key=eq." + enc(licenseKey) +
                "&last_ping=lt.now()-interval+'" + STALE_SECS + "+seconds'";
        HttpRequest req = baseRequest(SESSIONS + filter).DELETE().build();
        http.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private static int countActive(String licenseKey) throws Exception {
        HttpRequest req = baseRequest(SESSIONS + "?license_key=eq." + enc(licenseKey))
                .header("Prefer", "count=exact")
                .header("select", "id")
                .GET()
                .build();

        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());

        String range = resp.headers().firstValue("Content-Range").orElse("0/0");
        try {
            String total = range.contains("/") ? range.split("/")[1] : "0";
            return Integer.parseInt(total.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int fetchMax(String licenseKey) throws Exception {
        HttpRequest req = baseRequest(LIMITS + "?license_key=eq." + enc(licenseKey) +
                "&select=max_sessions")
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body().trim();

        // Response looks like: [{"max_sessions":3}]  or  []
        if (body.equals("[]") || body.isEmpty()) return 1;
        int start = body.indexOf("\"max_sessions\":");
        if (start == -1) return 1;
        start += "\"max_sessions\":".length();
        int end = body.indexOf('}', start);
        if (end == -1) end = body.length();
        try {
            return Integer.parseInt(body.substring(start, end).replaceAll("[^0-9]", "").trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static boolean insertSession(String licenseKey) throws Exception {
        String body = "{\"license_key\":\"" + esc(licenseKey) + "\",\"server_id\":\"" + SERVER_ID + "\"}";
        HttpRequest req = baseRequest(SESSIONS)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=ignore-duplicates")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 201;
    }

    private static void startHeartbeat(JavaPlugin plugin) {
        long ticks = HEARTBEAT_SECS * 20L;
        heartbeatTask = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (activeLicenseKey == null) return;
            try {
                HttpRequest req = baseRequest(SESSIONS + "?server_id=eq." + SERVER_ID)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"last_ping\":\"now()\"}"))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200 && resp.body().equals("[]")) {
                    plugin.getLogger().severe("Session was revoked externally! Shutting down.");
                    org.bukkit.Bukkit.getScheduler().runTask(plugin,
                            () -> org.bukkit.Bukkit.getPluginManager().disablePlugin(plugin));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Heartbeat failed: " + e.getMessage());
            }
        }, ticks, ticks);
    }

    private static void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    private static HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer " + ANON_KEY);
    }

    private static String enc(String s) {
        return s.replace(" ", "%20").replace("+", "%2B");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
