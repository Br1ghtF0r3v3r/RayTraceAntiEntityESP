package RayTraceAntiEntityESP.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;

import static RayTraceAntiEntityESP.Main.plugin;

public class Config {

    public static boolean isCheckingEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;

    public static double boundingBoxExtraValue;
    public static int samplePointsPerCorner;
    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static void setConfig() {

        loadSpigotConfig();

        isCheckingEnabled = plugin.getConfig().getBoolean("enabled", true);
        checkingPeriodTicks = plugin.getConfig().getLong("checking_period_ticks", 1);
        checkingDistanceOverride = plugin.getConfig().getDouble("checking_distance_override", -1);

        boundingBoxExtraValue = plugin.getConfig().getDouble("bounding_box_extra_value", 0.1);
        samplePointsPerCorner = plugin.getConfig().getInt("sample_points", 8);
        isPerspectiveCheckingEnabled = plugin.getConfig().getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = plugin.getConfig().getDouble("perspective_checking.distances", 4);

    }

    public static YamlConfiguration spigotConfig;

    public static void loadSpigotConfig() {
        File spigotFile = new File("spigot.yml");
        spigotConfig = YamlConfiguration.loadConfiguration(spigotFile);
    }

    public static int getSpigotTrackingRange(LivingEntity entity) {
        String worldName = entity.getWorld().getName();
        String base = spigotConfig.contains("world-settings." + worldName + ".entity-tracking-range")
                ? "world-settings." + worldName + ".entity-tracking-range."
                : "world-settings.default.entity-tracking-range.";
        return switch (entity) {
            case Player ignored -> spigotConfig.getInt(base + "players", 128);
            case Animals ignored -> spigotConfig.getInt(base + "animals", 96);
            case Monster ignored -> spigotConfig.getInt(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getInt(base + "misc", 96);
            default -> spigotConfig.getInt(base + "other", 64);
        };
    }

}
