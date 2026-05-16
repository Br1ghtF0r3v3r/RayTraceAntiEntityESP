package RayTraceAntiEntityESP.bukkit.config;

import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.misc.StringFormat.formatToString;

public class Config {

    public static int asyncThreads;

    public static boolean isCheckingEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;
    public static double checkingBoundingBoxExtraValue;
    public static int checkingVerticesLayers;

    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static boolean isDisplayNameEnabled;
    public static double displayNameOffSetY;

    public static boolean isDebugEnabled;

    public static List<String> antiEntities;
    public static String antiMode;
    public static boolean isBlacklist;
    public static String excludeEntityTag;

    public static void setConfig() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        loadSpigotConfig();

        asyncThreads = config.getInt("performance.async_threads", 2);

        isCheckingEnabled = config.getBoolean("checking.enabled", true);
        checkingPeriodTicks = config.getLong("checking.period_ticks", 1);
        checkingDistanceOverride = config.getDouble("checking.distance_override", 5);
        checkingBoundingBoxExtraValue = config.getDouble("checking.bounding_box_extra_value", 0.5);
        checkingVerticesLayers = config.getInt("checking.vertices_layers", 5);

        isPerspectiveCheckingEnabled = config.getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = config.getDouble("perspective_checking.distances_from_head", 4);

        isDisplayNameEnabled = config.getBoolean("display_name.enabled", true);
        displayNameOffSetY = config.getDouble("display_name.offset_y", 0.25);

        boolean prevDebugEnabled = isDebugEnabled;
        isDebugEnabled = config.getBoolean("debug.enabled", false);

        antiEntities = config.getStringList("anti_entities");
        antiMode = config.getString("anti_mode", "whitelist");
        isBlacklist = "blacklist".equalsIgnoreCase(antiMode);
        excludeEntityTag = config.getString("exclude_entity_tag", "raytrace_anti_esp_excluded");

        if (prevDebugEnabled != isDebugEnabled) {
            RayTraceEngine.clearAllCaches();
        }

        if (isCheckingEnabled) {
            RayTraceEngine.startTask();
        } else {
            RayTraceEngine.killTask();
        }

    }

    public static YamlConfiguration spigotConfig;
    public static volatile double maxTrackingRange = 144.0;

    public static void loadSpigotConfig() {
        File spigotFile = new File("spigot.yml");
        spigotConfig = YamlConfiguration.loadConfiguration(spigotFile);
        maxTrackingRange = spigotConfig.getDouble("world-settings.default.entity-tracking-range.players", 128) + 16;
        clearTrackingRangeCache();
    }

    public static double getMaxTrackingRange() {
        return maxTrackingRange;
    }

    private static final ConcurrentHashMap<String, Double> trackingRangeCache = new ConcurrentHashMap<>();

    public static void clearTrackingRangeCache() {
        trackingRangeCache.clear();
    }

    public static double getSpigotTrackingRange(Entity entity) {
        String key = entity.getWorld().getName() + ":" + entity.getClass().getSimpleName();
        return trackingRangeCache.computeIfAbsent(key, k -> computeTrackingRange(entity));
    }

    public static double computeTrackingRange(Entity entity) {
        String worldName = entity.getWorld().getName();
        String base = spigotConfig.contains("world-settings." + worldName + ".entity-tracking-range") ? "world-settings." + worldName + ".entity-tracking-range." : "world-settings.default.entity-tracking-range.";
        return switch (entity) {
            case Player ignored -> spigotConfig.getDouble(base + "players", 128);
            case Animals ignored -> spigotConfig.getDouble(base + "animals", 96);
            case Monster ignored -> spigotConfig.getDouble(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getDouble(base + "misc", 96);
            default -> spigotConfig.getDouble(base + "other", 64);
        } + 16;
    }

    public static void printConfig(CommandSender sender) {
        var cfg = plugin.getConfig();
        sender.sendMessage(formatToString(sender, "&6--- RayTrace Anti Entity ESP Config (File) ---"));
        sender.sendMessage(formatToString(sender, "&echecking.enabled: &f" + cfg.getBoolean("checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&echecking.period_ticks: &f" + cfg.getLong("checking.period_ticks", 1)));
        sender.sendMessage(formatToString(sender, "&echecking.distance_override: &f" + cfg.getDouble("checking.distance_override", 5)));
        sender.sendMessage(formatToString(sender, "&echecking.bounding_box_extra_value: &f" + cfg.getDouble("checking.bounding_box_extra_value", 0.5)));
        sender.sendMessage(formatToString(sender, "&echecking.vertices_layers: &f" + cfg.getInt("checking.vertices_layers", 5)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.enabled: &f" + cfg.getBoolean("perspective_checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.distances_from_head: &f" + cfg.getDouble("perspective_checking.distances_from_head", 4)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.enabled: &f" + cfg.getBoolean("display_name.enabled", true)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.offset_y: &f" + cfg.getDouble("display_name.offset_y", 0.25)));
        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + cfg.getBoolean("debug.enabled", false)));
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", cfg.getStringList("anti_entities"))));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + cfg.getString("anti_mode", "whitelist")));
    }

}