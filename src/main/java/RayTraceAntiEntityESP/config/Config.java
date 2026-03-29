package RayTraceAntiEntityESP.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static RayTraceAntiEntityESP.Main.plugin;

public class Config {

    public static boolean isCheckingEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;

    public static double boundingBoxExtraValue;
    public static int samplePointsPerCorner;
    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static List<String> antiEntities;
    public static String antiMode;

    public static void setConfig() {

        loadSpigotConfig();

        isCheckingEnabled = plugin.getConfig().getBoolean("enabled", true);
        checkingPeriodTicks = plugin.getConfig().getLong("checking_period_ticks", 1);
        checkingDistanceOverride = plugin.getConfig().getDouble("checking_distance_override", 0);

        boundingBoxExtraValue = plugin.getConfig().getDouble("bounding_box_extra_value", 0.5);
        samplePointsPerCorner = plugin.getConfig().getInt("vertices_layers", 5);
        isPerspectiveCheckingEnabled = plugin.getConfig().getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = plugin.getConfig().getDouble("perspective_checking.distances", 4);

        antiEntities = plugin.getConfig().getStringList("anti_entities");
        antiMode = plugin.getConfig().getString("anti_mode", "whitelist");

    }

    public static YamlConfiguration spigotConfig;

    public static void loadSpigotConfig() {
        File spigotFile = new File("spigot.yml");
        spigotConfig = YamlConfiguration.loadConfiguration(spigotFile);
    }

    public static double getSpigotTrackingRange(LivingEntity entity) {
        String worldName = entity.getWorld().getName();
        String base = spigotConfig.contains("world-settings." + worldName + ".entity-tracking-range")
                ? "world-settings." + worldName + ".entity-tracking-range."
                : "world-settings.default.entity-tracking-range.";
        return switch (entity) {
            case Player ignored -> spigotConfig.getDouble(base + "players", 128);
            case Animals ignored -> spigotConfig.getDouble(base + "animals", 96);
            case Monster ignored -> spigotConfig.getDouble(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getDouble(base + "misc", 96);
            default -> spigotConfig.getDouble(base + "other", 64);
        };
    }

}
