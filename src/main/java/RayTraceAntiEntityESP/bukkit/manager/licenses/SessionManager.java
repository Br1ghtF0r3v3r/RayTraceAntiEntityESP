package RayTraceAntiEntityESP.bukkit.manager.licenses;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class SessionManager {

    private static final String SUPABASE_URL = "https://tgixnkhvdhzojjkfjbmg.supabase.co/rest/v1/sessions";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRnaXhua2h2ZGh6b2pqa2ZqYm1nIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc0NTMwNjAsImV4cCI6MjA5MzAyOTA2MH0.RbDQDM7UEHj6oF1rEqNkyHy3pKgl1oFXmFuPCK4IYPE";
    private static final int HEARTBEAT_SECS = 30;

    private static final HttpClient http = HttpClient.newHttpClient();
    private static BukkitTask heartbeatTask;
    private static String activeLicense;

    public static boolean startSession(String license, JavaPlugin plugin) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=ignore-duplicates")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"build_id\":\"" + license + "\"}"
                    ))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            boolean claimed = response.statusCode() == 201;

            if (!claimed) {
                return false;
            }

            activeLicense = license;
            startHeartbeat(plugin);
            plugin.getLogger().info("Session claimed for license: " + license);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Could not reach session server, running without session lock: " + e.getMessage());
            return true;
        }
    }

    public static void endSession() {
        stopHeartbeat();
        if (activeLicense == null) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "?build_id=eq." + activeLicense))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .DELETE()
                    .build();
            http.send(request, HttpResponse.BodyHandlers.ofString());
            activeLicense = null;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not release session: " + e.getMessage());
        }
    }

    private static void startHeartbeat(JavaPlugin plugin) {
        long ticks = HEARTBEAT_SECS * 20L;
        heartbeatTask = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (activeLicense == null) return;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "?build_id=eq." + activeLicense))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(
                                "{\"last_ping\":\"now()\"}"
                        ))
                        .build();
                http.send(request, HttpResponse.BodyHandlers.ofString());
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
}
