package RayTraceAntiEntityESP.bukkit.manager.licenses;

import dev.respark.licensegate.LicenseGate;
import org.bukkit.plugin.java.JavaPlugin;


public class LicenseManager {

    private static final String USER_ID = "a267a";

    public static boolean verifyLicense(String licenseKey, JavaPlugin plugin) {
        if (licenseKey.isEmpty()) {
            plugin.getLogger().severe("No license key set in config.yml!");
            return false;
        }

        try {
            LicenseGate.ValidationType result = new LicenseGate(USER_ID).verify(licenseKey);

            switch (result) {
                case VALID -> {
                    plugin.getLogger().info("License verified!");
                    return true;
                }
                case EXPIRED -> plugin.getLogger().severe("License expired!");
                default -> plugin.getLogger().severe("Invalid license! Reason: " + result);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not reach license server, running in grace mode.");
            return true;
        }

        return false;
    }
}