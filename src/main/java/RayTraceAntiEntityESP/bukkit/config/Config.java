package RayTraceAntiEntityESP.bukkit.config;

import RayTraceAntiEntityESP.bukkit.manager.engine.RayTraceManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.List;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.misc.StringFormat.formatToString;

public class Config {

    public static String licenseKey;

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
    public static String bypassTag;

    public static void setConfig() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();

        loadSpigotConfig();

        licenseKey = plugin.getConfig().getString("license-key", "");

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

        isDebugEnabled = config.getBoolean("debug.enabled", false);

        antiEntities = config.getStringList("anti_entities");
        antiMode = config.getString("anti_mode", "whitelist");
        bypassTag = config.getString("bypass_tag", "raytrace_anti_esp_bypass");

        if (isCheckingEnabled) {
            RayTraceManager.startTask();
        } else {
            RayTraceManager.killTask();
        }

    }

    public static YamlConfiguration spigotConfig;

    public static void loadSpigotConfig() {
        File spigotFile = new File("spigot.yml");
        spigotConfig = YamlConfiguration.loadConfiguration(spigotFile);
    }

    public static double getSpigotTrackingRange(Entity entity) {
        String worldName = entity.getWorld().getName();
        String base = spigotConfig.contains("world-settings." + worldName + ".entity-tracking-range") ? "world-settings." + worldName + ".entity-tracking-range." : "world-settings.default.entity-tracking-range.";
        return switch (entity) {
            case Player ignored          -> spigotConfig.getDouble(base + "players", 128);
            case Animals ignored         -> spigotConfig.getDouble(base + "animals", 96);
            case Monster ignored         -> spigotConfig.getDouble(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getDouble(base + "misc", 96);
            default                      -> spigotConfig.getDouble(base + "other", 64);
        } + 64;
    }

    public static void printConfig(CommandSender sender) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();

        sender.sendMessage(formatToString(sender, "&6--- RayTrace Anti Entity ESP Config (File) ---"));
        sender.sendMessage(formatToString(sender, "&echecking.enabled: &f" + config.getBoolean("checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&echecking.period_ticks: &f" + config.getLong("checking.period_ticks", 1)));
        sender.sendMessage(formatToString(sender, "&echecking.distance_override: &f" + config.getDouble("checking.distance_override", 5)));
        sender.sendMessage(formatToString(sender, "&echecking.bounding_box_extra_value: &f" + config.getDouble("checking.bounding_box_extra_value", 0.5)));
        sender.sendMessage(formatToString(sender, "&echecking.vertices_layers: &f" + config.getInt("checking.vertices_layers", 5)));

        sender.sendMessage(formatToString(sender, "&eperspective_checking.enabled: &f" + config.getBoolean("perspective_checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.distances_from_head: &f" + config.getDouble("perspective_checking.distances_from_head", 4)));

        sender.sendMessage(formatToString(sender, "&edisplay_name.enabled: &f" + config.getBoolean("display_name.enabled", true)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.offset_y: &f" + config.getDouble("display_name.offset_y", 0.25)));

        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + config.getBoolean("debug.enabled", false)));

        List<String> entities = config.getStringList("anti_entities");
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", entities)));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + config.getString("anti_mode", "whitelist")));
    }

}
