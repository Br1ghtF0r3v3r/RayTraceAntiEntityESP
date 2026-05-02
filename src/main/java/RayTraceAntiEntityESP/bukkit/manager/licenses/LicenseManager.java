package RayTraceAntiEntityESP.bukkit.manager.licenses;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;


public class LicenseManager {

    public static int maxSessions = 1;
    private static final String USER_ID = "a267a";

    public static boolean verifyLicense(String licenseKey, JavaPlugin plugin) {
        if (licenseKey.isEmpty()) {
            plugin.getLogger().severe("No license key set in config.yml!");
            return false;
        }
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("https://api.licensegate.io/license/" + USER_ID + "/" + licenseKey + "/verify"))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            plugin.getLogger().info("License response: " + body);

            boolean valid = body.contains("\"result\":\"VALID\"");
            boolean expired = body.contains("\"result\":\"EXPIRED\"");

            if (valid) {
                maxSessions = parseIntField(body, "ipLimit", 1);
                plugin.getLogger().info("License verified! Max sessions: " + maxSessions);
                return true;
            } else if (expired) {
                plugin.getLogger().severe("License expired!");
            } else {
                plugin.getLogger().severe("Invalid license! Body: " + body);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not reach license server, running in grace mode.");
            maxSessions = 1;
            return true;
        }
        return false;
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
}