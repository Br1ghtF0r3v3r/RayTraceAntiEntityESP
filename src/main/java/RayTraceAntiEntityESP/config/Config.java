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
    public static boolean isDebugEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;

    public static double boundingBoxExtraValue;
    public static int samplePointsPerCorner;
    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;
    public static boolean isFakeDisplayNameEnabled;
    public static long fakeDisplayNamePeriodTicks;
    public static double fakeDisplayNameOffSetY;

    public static List<String> antiEntities;
    public static String antiMode;

    public static void setConfig() {

        loadSpigotConfig();

        isCheckingEnabled = plugin.getConfig().getBoolean("enabled", true);
        isDebugEnabled = plugin.getConfig().getBoolean("debug", false);
        checkingPeriodTicks = plugin.getConfig().getLong("checking_period_ticks", 1);
        checkingDistanceOverride = plugin.getConfig().getDouble("checking_distance_override", 0);

        boundingBoxExtraValue = plugin.getConfig().getDouble("bounding_box_extra_value", 0.5);
        samplePointsPerCorner = plugin.getConfig().getInt("vertices_layers", 5);
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
            case Player ignored -> spigotConfig.getDouble(base + "players", 128);
            case Animals ignored -> spigotConfig.getDouble(base + "animals", 96);
            case Monster ignored -> spigotConfig.getDouble(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getDouble(base + "misc", 96);
            default -> spigotConfig.getDouble(base + "other", 64);
        } * 2;
    }

    public static void printConfig(CommandSender sender) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();

        sender.sendMessage(formatToString(sender, "&6--- RayTrace Anti Entity ESP Config (File) ---"));
        sender.sendMessage(formatToString(sender, "&eenabled: &f" + config.getBoolean("enabled")));
        sender.sendMessage(formatToString(sender, "&edebug: &f" + config.getBoolean("debug")));
        sender.sendMessage(formatToString(sender, "&echecking_period_ticks: &f" + config.getLong("checking_period_ticks")));
        sender.sendMessage(formatToString(sender, "&echecking_distance_override: &f" + config.getDouble("checking_distance_override")));

        sender.sendMessage(formatToString(sender, "&ebounding_box_extra_value: &f" + config.getDouble("bounding_box_extra_value")));
        sender.sendMessage(formatToString(sender, "&evertices_layers: &f" + config.getInt("vertices_layers")));
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
