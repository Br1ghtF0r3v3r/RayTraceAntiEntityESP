package RayTraceAntiEntityESP.bukkit.manager.licenses;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LicenseManager {
    private static final int RESOURCE_ID = 0;
    private static final String SHARED_TOKEN = "CHYYaWbrVQI12Ttj9Gp3pkxKC7q6HyMz";
    public static final String MEMBER_ID = "%%__USER__%%";
    public static final String NONCE = "%%__NONCE__%%";
    public static final String TIMESTAMP = "%%__TIMESTAMP__%%";
    private static final HttpClient http = HttpClient.newHttpClient();

    @SuppressWarnings("ConstantConditions")
    public static boolean verifyLicense(JavaPlugin plugin) {
        String unreplaced = "%%" + "__";
        if (MEMBER_ID.startsWith(unreplaced) || NONCE.startsWith(unreplaced) || TIMESTAMP.startsWith(unreplaced)) {
            plugin.getLogger().severe("License placeholders were not replaced. " +
                    "This build was not downloaded through BuiltByBit.");
            return false;
        }

        plugin.getLogger().info("Verifying BuiltByBit license for member #" + MEMBER_ID + "...");

        try {
            String url = "https://api.builtbybit.com/v1/resources/" + RESOURCE_ID +
                    "/licenses/members/" + MEMBER_ID +
                    "?nonce=" + NONCE + "&date=" + TIMESTAMP;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Shared " + SHARED_TOKEN)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() != 200 || !body.contains("\"result\":\"success\"")) {
                plugin.getLogger().severe("License check failed (HTTP " + response.statusCode() + "): " + body);
                return false;
            }

            if (body.contains("\"active\":true")) {
                SessionManager.fetchMaxSessions(MEMBER_ID, plugin);
                plugin.getLogger().info("License verified! (member #" + MEMBER_ID + ")");
                return true;
            } else {
                plugin.getLogger().severe("License is inactive or expired. " +
                        "Please re-purchase or contact support.");
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not reach BuiltByBit API — running in grace mode: " + e.getMessage());
            SessionManager.fetchMaxSessions(MEMBER_ID, plugin);
            return true;
        }
    }
}