package RayTraceAntiEntityESP.bukkit.manager.licenses;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LicenseManager {

    private static final String USER_ID = "a267a";
    private static final HttpClient http = HttpClient.newHttpClient();

    public static int maxSessions = 1;

    public static boolean verifyLicense(String licenseKey, JavaPlugin plugin) {
        if (licenseKey.isEmpty()) {
            plugin.getLogger().severe("No license key set in config.yml!");
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.licensegate.io/license/" + USER_ID + "/" + licenseKey + "/verify"))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (body.contains("\"result\":\"VALID\"")) {
                maxSessions = SessionManager.fetchMaxSessions(licenseKey, plugin);
                String maxDisplay = maxSessions < 0 ? "∞" : String.valueOf(maxSessions);
                plugin.getLogger().info("License verified! Max sessions: " + maxDisplay);
                return true;
            } else if (body.contains("\"result\":\"EXPIRED\"")) {
                plugin.getLogger().severe("License expired!");
            } else {
                plugin.getLogger().severe("Invalid license! Response: " + body);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not reach license server, running in grace mode.");
            maxSessions = 1;
            return true;
        }
        return false;
    }
}