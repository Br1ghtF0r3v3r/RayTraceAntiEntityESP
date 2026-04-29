package RayTraceAntiEntityESP.bukkit.manager.licenses;

import dev.respark.licensegate.LicenseGate;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LicenseManager {

    private static final String USER_ID = "a267a";
    private static String buildId = null;

    public static String getBuildId(JavaPlugin plugin) {
        if (buildId != null) return buildId;
        try (InputStream is = plugin.getClass().getClassLoader().getResourceAsStream("build.properties")) {
            if (is == null) {
                plugin.getLogger().severe("build.properties not found!");
                return null;
            }
            Properties props = new Properties();
            props.load(is);
            buildId = props.getProperty("build.id");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read build.properties: " + e.getMessage());
        }
        return buildId;
    }

    public static boolean verifyLicense(String licenseKey, JavaPlugin plugin) {
        boolean licensed = false;

        if (licenseKey.isEmpty()) {
            plugin.getLogger().severe("No license key set in config.yml!");
            return false;
        }

        String id = getBuildId(plugin);
        if (id == null) {
            plugin.getLogger().severe("Could not determine build ID, license check aborted!");
            return false;
        } else {
            plugin.getLogger().info("Your build ID: " + id);
        }
        try {
            LicenseGate licenseGate = new LicenseGate(USER_ID);
            LicenseGate.ValidationType result = licenseGate.verify(licenseKey, id);
            if (result == LicenseGate.ValidationType.VALID) {
                licensed = true;
                plugin.getLogger().info("License verified!");
            } else if (result == LicenseGate.ValidationType.EXPIRED) {
                plugin.getLogger().severe("License expired!");
            } else {
                plugin.getLogger().severe("Invalid license! Reason: " + result);
            }
        } catch (Exception e) {
            licensed = false;
            plugin.getLogger().warning("Could not reach license server, running in grace mode.");
        }

        return licensed;
    }
}