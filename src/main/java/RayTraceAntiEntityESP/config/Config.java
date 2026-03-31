package RayTraceAntiEntityESP.config;

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
    public static int checkingSamplePointsPerCorner;

    public static boolean isDebugEnabled;
    public static long debugPeriodTicks;

    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static boolean isFakeDisplayNameEnabled;
    public static long fakeDisplayNamePeriodTicks;
    public static double fakeDisplayNameOffSetY;

    public static List<String> antiEntities;
    public static String antiMode;

    public static void setConfig() {

        loadSpigotConfig();

        isCheckingEnabled = plugin.getConfig().getBoolean("checking.enabled", true);
        checkingPeriodTicks = plugin.getConfig().getLong("checking.period_ticks", 1);
        checkingDistanceOverride = plugin.getConfig().getDouble("checking.distance_override", 0);
        checkingBoundingBoxExtraValue = plugin.getConfig().getDouble("checking.bounding_box_extra_value", 0.5);
        checkingSamplePointsPerCorner = plugin.getConfig().getInt("checking.vertices_layers", 5);

        isDebugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        debugPeriodTicks = plugin.getConfig().getLong("debug.period_ticks", 1);

        isPerspectiveCheckingEnabled = plugin.getConfig().getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = plugin.getConfig().getDouble("perspective_checking.distances_from_head", 4);

        isFakeDisplayNameEnabled = plugin.getConfig().getBoolean("fake_name_display.enabled", true);
        fakeDisplayNamePeriodTicks = plugin.getConfig().getLong("fake_name_display.period_ticks", 1);
        fakeDisplayNameOffSetY = plugin.getConfig().getDouble("fake_name_display.offset_y", 0.25);

        antiEntities = plugin.getConfig().getStringList("anti_entities");
        antiMode = plugin.getConfig().getString("anti_mode", "whitelist");

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
        sender.sendMessage(formatToString(sender, "&echecking.enabled: &f" + config.getBoolean("checking.enabled")));
        sender.sendMessage(formatToString(sender, "&echecking.period_ticks: &f" + config.getLong("checking.period_ticks")));
        sender.sendMessage(formatToString(sender, "&echecking.distance_override: &f" + config.getDouble("checking.distance_override")));
        sender.sendMessage(formatToString(sender, "&echecking.bounding_box_extra_value: &f" + config.getDouble("checking.bounding_box_extra_value")));
        sender.sendMessage(formatToString(sender, "&echecking.vertices_layers: &f" + config.getInt("checking.vertices_layers")));

        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + config.getBoolean("debug.enabled")));
        sender.sendMessage(formatToString(sender, "&edebug.period_ticks: &f" + config.getLong("debug.period_ticks")));

        sender.sendMessage(formatToString(sender, "&eperspective_checking.enabled: &f" + config.getBoolean("perspective_checking.enabled")));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.distances_from_head: &f" + config.getDouble("perspective_checking.distances_from_head")));

        sender.sendMessage(formatToString(sender, "&efake_name_display.enabled: &f" + config.getBoolean("fake_name_display.enabled")));
        sender.sendMessage(formatToString(sender, "&efake_name_display.period_ticks: &f" + config.getLong("fake_name_display.period_ticks")));
        sender.sendMessage(formatToString(sender, "&efake_name_display.offset_y: &f" + config.getDouble("fake_name_display.offset_y")));

        List<String> entities = config.getStringList("anti_entities");
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", entities)));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + config.getString("anti_mode")));
    }

}
