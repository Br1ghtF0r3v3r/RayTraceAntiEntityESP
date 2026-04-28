package RayTraceAntiEntityESP.bukkit.manager.licenses;

import dev.respark.licensegate.LicenseGate;
import org.bukkit.plugin.java.JavaPlugin;

public class LicenseManager {

    private static final String USER_ID = "a267a";

    public static boolean verifyLicense(String licenseKey, JavaPlugin plugin) {

        boolean licensed = false;

        if (licenseKey.isEmpty()) {
            plugin.getLogger().severe("No license key set in config.yml!");
        } else {
            try {
                LicenseGate licenseGate = new LicenseGate(USER_ID);
                LicenseGate.ValidationType result = licenseGate.verify(licenseKey);

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
        }
        return licensed;
    }
}
