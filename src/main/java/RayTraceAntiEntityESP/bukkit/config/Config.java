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

    public static final int CONFIG_VERSION = 3;

    public static boolean isCheckingEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;
    public static double checkingBoundingBoxExtraValue;
    public static int checkingVerticesLayers;
    public static int checkingStaggerGroups;

    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static boolean isDisplayNameEnabled;
    public static double displayNameOffSetY;
    public static double displayNameLookaheadTicks;

    public static boolean isDebugEnabled;

    public static java.util.Set<String> antiEntities;
    public static String antiMode;
    public static boolean isBlacklist;

    public static void migrateConfigIfNeeded() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration current = YamlConfiguration.loadConfiguration(configFile);
        int fileVersion = current.getInt("config_version", -1);
        if (fileVersion == CONFIG_VERSION) return;

        plugin.getLogger().info("config.yml is " + (fileVersion == -1 ? "missing a config_version"
                : "on version " + fileVersion) + " (jar is on " + CONFIG_VERSION + "). Migrating...");

        File backup = new File(plugin.getDataFolder(),
                "config.yml.backup-" + (fileVersion == -1 ? "unversioned" : String.valueOf(fileVersion)));
        try {
            java.nio.file.Files.copy(configFile.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not back up old config.yml before migrating: " + e.getMessage());
        }

        YamlConfiguration fresh;
        try (var in = plugin.getResource("config.yml")) {
            if (in == null) {
                plugin.getLogger().severe("Bundled config.yml not found inside the jar; skipping migration.");
                return;
            }
            fresh = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to read bundled config.yml: " + e.getMessage());
            return;
        }

        for (String key : current.getKeys(true)) {
            if (key.equals("config_version")) continue;
            Object value = current.get(key);
            if (value instanceof org.bukkit.configuration.ConfigurationSection) continue;
            if (fresh.contains(key)) fresh.set(key, value);
        }
        fresh.set("config_version", CONFIG_VERSION);

        try {
            fresh.save(configFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save migrated config.yml: " + e.getMessage());
            return;
        }

        plugin.getLogger().info("config.yml migrated to version " + CONFIG_VERSION + ". Your previous file was backed up as " + backup.getName() + ".");
    }

    public static void setConfig() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        loadSpigotConfig();

        isCheckingEnabled = config.getBoolean("checking.enabled", true);
        checkingPeriodTicks = config.getLong("checking.period_ticks", 1);
        checkingStaggerGroups = Math.max(1, config.getInt("checking.stagger_groups", 3));
        checkingDistanceOverride = config.getDouble("checking.distance_override", 10);
        checkingBoundingBoxExtraValue = config.getDouble("checking.bounding_box_extra_value", 0);
        checkingVerticesLayers = config.getInt("checking.vertices_layers", 4);

        isPerspectiveCheckingEnabled = config.getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = config.getDouble("perspective_checking.distances_from_head", 4);

        isDisplayNameEnabled = config.getBoolean("display_name.enabled", true);
        displayNameLookaheadTicks = config.getDouble("display_name.lookahead_ticks", 3.0);
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
        clearTrackingRangeCache();
        recomputeMaxTrackingRange();
    }

    public static double getMaxTrackingRange() {
        return maxTrackingRange;
    }

    private static void recomputeMaxTrackingRange() {
        double max = 128;
        org.bukkit.configuration.ConfigurationSection worldSettings =
                spigotConfig.getConfigurationSection("world-settings");
        if (worldSettings != null) {
            for (String world : worldSettings.getKeys(false)) {
                String base = "world-settings." + world + ".entity-tracking-range.";
                max = Math.max(max, spigotConfig.getDouble(base + "players", 0));
                max = Math.max(max, spigotConfig.getDouble(base + "animals", 0));
                max = Math.max(max, spigotConfig.getDouble(base + "monsters", 0));
                max = Math.max(max, spigotConfig.getDouble(base + "misc", 0));
                max = Math.max(max, spigotConfig.getDouble(base + "other", 0));
            }
        }
        maxTrackingRange = max + 16;
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
        double range = computeTrackingRange(entity) + 16;
        trackingRangeCache.put(key, range);
        return range;
    }

    public static double computeTrackingRange(Entity entity) {
        String worldName = entity.getWorld().getName();
        String worldPath = "world-settings." + worldName + ".entity-tracking-range.";
        boolean hasWorldSettings = spigotConfig.contains(worldPath.substring(0, worldPath.length() - 1));
        String base = hasWorldSettings ? worldPath : "world-settings.default.entity-tracking-range.";

        return switch (entity) {
            case Player ignored -> spigotConfig.getDouble(base + "players", 128);
            case Animals ignored -> spigotConfig.getDouble(base + "animals", 96);
            case Monster ignored -> spigotConfig.getDouble(base + "monsters", 96);
            case AbstractVillager ignored -> spigotConfig.getDouble(base + "misc", 96);
            default -> spigotConfig.getDouble(base + "other", 64);
        };
    }

    public static void printConfig(CommandSender sender) {
        var cfg = plugin.getConfig();
        sender.sendMessage(formatToString(sender, "&6--- RayTrace Anti Entity ESP Config (File) ---"));
        sender.sendMessage(formatToString(sender, "&econfig_version: &f" + cfg.getInt("config_version", -1) + " &7(jar: " + CONFIG_VERSION + ")"));
        sender.sendMessage(formatToString(sender, "&echecking.enabled: &f" + cfg.getBoolean("checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&echecking.period_ticks: &f" + cfg.getLong("checking.period_ticks", 1)));
        sender.sendMessage(formatToString(sender, "&echecking.stagger_groups: &f" + cfg.getInt("checking.stagger_groups", 3)));
        sender.sendMessage(formatToString(sender, "&echecking.distance_override: &f" + cfg.getDouble("checking.distance_override", 10)));
        sender.sendMessage(formatToString(sender, "&echecking.bounding_box_extra_value: &f" + cfg.getDouble("checking.bounding_box_extra_value", 0)));
        sender.sendMessage(formatToString(sender, "&echecking.vertices_layers: &f" + cfg.getInt("checking.vertices_layers", 4)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.enabled: &f" + cfg.getBoolean("perspective_checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.distances_from_head: &f" + cfg.getDouble("perspective_checking.distances_from_head", 4)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.enabled: &f" + cfg.getBoolean("display_name.enabled", true)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.offset_y: &f" + cfg.getDouble("display_name.offset_y", 0)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.lookahead_ticks: &f" + cfg.getDouble("display_name.lookahead_ticks", 3.0)));
        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + cfg.getBoolean("debug.enabled", false)));
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", cfg.getStringList("anti_entities"))));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + cfg.getString("anti_mode", "whitelist")));
    }
}