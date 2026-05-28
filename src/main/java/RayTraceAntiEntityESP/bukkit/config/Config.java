package RayTraceAntiEntityESP.bukkit.config;

import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.List;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.misc.StringFormat.formatToString;

public class Config {

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

    public static java.util.Set<String> antiEntities;
    public static String antiMode;
    public static boolean isBlacklist;
    public static String excludeEntityTag;

    public static void setConfig() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        loadSpigotConfig();

        isCheckingEnabled = config.getBoolean("checking.enabled", true);
        checkingPeriodTicks = config.getLong("checking.period_ticks", 5);
        checkingDistanceOverride = config.getDouble("checking.distance_override", 5);
        checkingBoundingBoxExtraValue = config.getDouble("checking.bounding_box_extra_value", 0);
        checkingVerticesLayers = config.getInt("checking.vertices_layers", 5);

        isPerspectiveCheckingEnabled = config.getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = config.getDouble("perspective_checking.distances_from_head", 4);

        isDisplayNameEnabled = config.getBoolean("display_name.enabled", true);
        displayNameOffSetY = config.getDouble("display_name.offset_y", 0);

        boolean prevDebugEnabled = isDebugEnabled;
        isDebugEnabled = config.getBoolean("debug.enabled", false);

        List<String> entityList = config.getStringList("anti_entities");
        antiEntities = new java.util.HashSet<>();
        for (String entity : entityList) {
            antiEntities.add(entity.toLowerCase());
        }
        antiMode = config.getString("anti_mode", "whitelist");
        isBlacklist = "blacklist".equalsIgnoreCase(antiMode);
        excludeEntityTag = config.getString("exclude_entity_tag", "raytrace_anti_entity_esp_excluded");

        RayTraceEngine.clearAntiEntityCache();

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

    private static final it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<String> trackingRangeCache =
            new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>();

    public static void clearTrackingRangeCache() {
        trackingRangeCache.clear();
    }

    public static double getSpigotTrackingRange(Entity entity) {
        String worldName = entity.getWorld().getName();
        String entityType = entity.getClass().getSimpleName();
        String key = worldName + ":" + entityType;
        double cached = trackingRangeCache.getOrDefault(key, Double.NEGATIVE_INFINITY);
        if (cached != Double.NEGATIVE_INFINITY) return cached;
        double range = computeTrackingRange(entity);
        trackingRangeCache.put(key, range);
        return range;
    }

    public static double computeTrackingRange(Entity entity) {
        String worldName = entity.getWorld().getName();
        String worldPath = "world-settings." + worldName + ".entity-tracking-range.";
        boolean hasWorldSettings = spigotConfig.contains(worldPath.substring(0, worldPath.length() - 1));
        String base = hasWorldSettings ? worldPath : "world-settings.default.entity-tracking-range.";

        double range = switch (entity) {
            case Player ignored -> spigotConfig.getDouble(base + "players", 128);
            case Animals ignored -> spigotConfig.getDouble(base + "animals", 96);
            case Monster ignored -> spigotConfig.getDouble(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getDouble(base + "misc", 96);
            default -> spigotConfig.getDouble(base + "other", 64);
        };
        return range + 16;
    }

    public static void printConfig(CommandSender sender) {
        var cfg = plugin.getConfig();
        sender.sendMessage(formatToString(sender, "&6--- RayTrace Anti Entity ESP Config (File) ---"));
        sender.sendMessage(formatToString(sender, "&echecking.enabled: &f" + cfg.getBoolean("checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&echecking.period_ticks: &f" + cfg.getLong("checking.period_ticks", 5)));
        sender.sendMessage(formatToString(sender, "&echecking.distance_override: &f" + cfg.getDouble("checking.distance_override", 5)));
        sender.sendMessage(formatToString(sender, "&echecking.bounding_box_extra_value: &f" + cfg.getDouble("checking.bounding_box_extra_value", 0)));
        sender.sendMessage(formatToString(sender, "&echecking.vertices_layers: &f" + cfg.getInt("checking.vertices_layers", 5)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.enabled: &f" + cfg.getBoolean("perspective_checking.enabled", false)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.distances_from_head: &f" + cfg.getDouble("perspective_checking.distances_from_head", 4)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.enabled: &f" + cfg.getBoolean("display_name.enabled", true)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.offset_y: &f" + cfg.getDouble("display_name.offset_y", 0)));
        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + cfg.getBoolean("debug.enabled", false)));
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", cfg.getStringList("anti_entities"))));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + cfg.getString("anti_mode", "whitelist")));
        sender.sendMessage(formatToString(sender, "&eexclude_entity_tag: &f" + cfg.getString("exclude_entity_tag", "raytrace_anti_entity_esp_excluded")));
    }
}