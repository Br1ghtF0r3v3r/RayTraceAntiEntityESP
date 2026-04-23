package RayTraceAntiEntityESP.config;

import RayTraceAntiEntityESP.manager.engine.RayTraceManager;
import RayTraceAntiEntityESP.utils.DebugsUtils;
import RayTraceAntiEntityESP.utils.FakeNameDisplay;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.util.List;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.misc.StringFormat.formatToString;

public class Config {

    public static boolean isCheckingEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;
    public static double checkingBoundingBoxExtraValue;
    public static int checkingSampleLayers;

    public static boolean isDebugEnabled;
    public static long debugPeriodTicks;

    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static boolean isFakeDisplayNameEnabled;
    public static long fakeDisplayNamePeriodTicks;
    public static double fakeDisplayNameOffSetY;

    public static List<String> antiEntities;
    public static String antiMode;
    public static String bypassTag;

    public static void setConfig() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();

        loadSpigotConfig();

        isCheckingEnabled = config.getBoolean("checking.enabled", true);
        checkingPeriodTicks = config.getLong("checking.period_ticks", 1);
        checkingDistanceOverride = config.getDouble("checking.distance_override", 5);
        checkingBoundingBoxExtraValue = config.getDouble("checking.bounding_box_extra_value", 0.5);
        checkingSampleLayers = config.getInt("checking.vertices_layers", 5);

        isPerspectiveCheckingEnabled = config.getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = config.getDouble("perspective_checking.distances_from_head", 4);

        isDebugEnabled = config.getBoolean("debug.enabled", false);
        debugPeriodTicks = config.getLong("debug.period_ticks", 1);

        isFakeDisplayNameEnabled = config.getBoolean("fake_name_display.enabled", true);
        fakeDisplayNamePeriodTicks = config.getLong("fake_name_display.period_ticks", 1);
        fakeDisplayNameOffSetY = config.getDouble("fake_name_display.offset_y", 0.25);

        antiEntities = config.getStringList("anti_entities");
        antiMode = config.getString("anti_mode", "whitelist");
        bypassTag = config.getString("bypass_tag", "raytrace_anti_esp_bypass");

        if (isCheckingEnabled) {
            RayTraceManager.startTask();
        } else {
            RayTraceManager.killTask();
        }

        if (isFakeDisplayNameEnabled) {
            FakeNameDisplay.startTask();
        } else {
            FakeNameDisplay.killTask();
        }

        if (isDebugEnabled) {
            DebugsUtils.startTask();
        } else {
            DebugsUtils.killTask();
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
        } * 2;
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

        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + config.getBoolean("debug.enabled", false)));
        sender.sendMessage(formatToString(sender, "&edebug.period_ticks: &f" + config.getLong("debug.period_ticks", 1)));
        
        sender.sendMessage(formatToString(sender, "&efake_name_display.enabled: &f" + config.getBoolean("fake_name_display.enabled", true)));
        sender.sendMessage(formatToString(sender, "&efake_name_display.period_ticks: &f" + config.getLong("fake_name_display.period_ticks", 1)));
        sender.sendMessage(formatToString(sender, "&efake_name_display.offset_y: &f" + config.getDouble("fake_name_display.offset_y", 0.25)));

        List<String> entities = config.getStringList("anti_entities");
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", entities)));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + config.getString("anti_mode", "whitelist")));
    }

}
