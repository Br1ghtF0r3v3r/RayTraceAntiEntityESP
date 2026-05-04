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
    private static final String LICENSES = BASE_URL + "/licenses";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRnaXhua2h2ZGh6b2pqa2ZqYm1nIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc0NTMwNjAsImV4cCI6MjA5MzAyOTA2MH0.RbDQDM7UEHj6oF1rEqNkyHy3pKgl1oFXmFuPCK4IYPE";

    public static int maxSessions = -1;

    private static final int STALE_SECS = 90;
    private static final int HEARTBEAT_SECS = 30;

    private static final HttpClient http = HttpClient.newHttpClient();
    private static BukkitTask heartbeatTask;

    private static final String SERVER_ID = UUID.randomUUID().toString();
    private static String activeLicenseKey;

    public static void fetchMaxSessions(String licenseKey, JavaPlugin plugin) {
        try {
            HttpRequest req = baseRequest(LICENSES
                    + "?license_key=eq." + enc(licenseKey)
                    + "&select=max_sessions")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();

            if (body == null || body.trim().equals("[]")) {
                plugin.getLogger().info("No session limit found for this license — unlimited servers allowed.");
                maxSessions = -1;
                return;
            }

            maxSessions = parseIntField(body, "max_sessions", -1); // store the result
            plugin.getLogger().info("Max sessions for this license: " + (maxSessions < 0 ? "∞" : maxSessions));

        } catch (Exception e) {
            plugin.getLogger().warning("Could not fetch max sessions, defaulting to unlimited: " + e.getMessage());
            maxSessions = -1;
        }
    }

    public static boolean startSession(String licenseKey, JavaPlugin plugin) {
        try {
            purgeStale(licenseKey);
            int active = countActive(licenseKey);
            int max = maxSessions;

            if (max == 0) {
                plugin.getLogger().severe("This license has been disabled.");
                return false;
            }

            if (max > 0 && active >= max) {
                plugin.getLogger().severe(
                        "Session limit reached! (" + active + "/" + max + " slots in use). " +
                                "Disable another server or purchase additional slots.");
                return false;
            }

            if (!insertSession(licenseKey)) {
                plugin.getLogger().severe("Failed to register session with Supabase.");
                return false;
            }

            activeLicenseKey = licenseKey;
            startHeartbeat(plugin);

            String slotInfo = max < 0 ? (active + 1) + "/∞" : (active + 1) + "/" + max;
            plugin.getLogger().info("Session claimed [" + slotInfo + "] server: " + SERVER_ID);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Could not reach session server — running in grace mode: " + e.getMessage());
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
        String staleThreshold = java.time.Instant.now()
                .minusSeconds(STALE_SECS)
                .toString();
        HttpRequest req = baseRequest(SESSIONS
                + "?license_key=eq." + enc(licenseKey)
                + "&last_ping=lt." + staleThreshold)
                .DELETE()
                .build();
        http.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private static int countActive(String licenseKey) throws Exception {
        HttpRequest req = baseRequest(SESSIONS
                + "?license_key=eq." + enc(licenseKey)
                + "&select=server_id")
                .header("Prefer", "count=exact")
                .GET()
                .build();

        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        String range = resp.headers().firstValue("Content-Range").orElse("0/0");
        try {
            String total = range.contains("/") ? range.split("/")[1] : "0";
            return "*".equals(total.trim()) ? 0 : Integer.parseInt(total.trim());
        } catch (NumberFormatException e) {
            return 0;
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
        plugin.getLogger().info("insertSession status=" + resp.statusCode());
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

    private static int parseIntField(String json, String field, int defaultValue) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return defaultValue;
        int start = idx + key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String enc(String s) {
        return s.replace(" ", "%20").replace("+", "%2B");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}