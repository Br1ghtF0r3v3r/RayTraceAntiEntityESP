package RayTraceAntiEntityESP.paper.config;

import RayTraceAntiEntityESP.paper.engine.RayTraceEngine;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.paper.Main.plugin;
import static RayTraceAntiEntityESP.paper.utils.StringFormat.formatToString;

public class Config {

    public static final int CONFIG_VERSION = 6;

    public static final long PERIOD_TICKS_MAX = 1200L;
    public static final int STAGGER_GROUPS_MAX = 64;
    public static final double DISTANCE_OVERRIDE_MAX = 256.0;
    public static final double BOUNDING_BOX_EXTRA_MAX = 4.0;
    public static final int VERTICES_LAYERS_MIN = 2;
    public static final int VERTICES_LAYERS_MAX = 7;
    public static final int ASYNC_THREADS_MAX = 16;
    public static final int SNAPSHOT_TTL_TICKS_MAX = 72000;
    public static final double PERSPECTIVE_DISTANCE_MAX = 32.0;
    public static final long DISPLAY_PERIOD_TICKS_MAX = 200L;
    public static final double DISPLAY_OFFSET_Y_MAX = 10.0;
    public static final double DISPLAY_LOOKAHEAD_TICKS_MAX = 20.0;

    public static boolean isCheckingEnabled;
    public static long checkingPeriodTicks;
    public static double checkingDistanceOverride;
    public static double checkingBoundingBoxExtraValue;
    public static int checkingVerticesLayers;
    public static int checkingStaggerGroups;

    public static boolean checkingAsyncEnabled;
    public static int checkingAsyncThreads;
    public static int checkingAsyncChunkSnapshotTtlTicks;

    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static boolean isDisplayNameEnabled;
    public static long displayNamePeriodTicks;
    public static double displayNameOffSetY;
    public static double displayNameLookaheadTicks;

    public static boolean isDebugEnabled;

    public static boolean isUpdateCheckerEnabled;

    public static Set<String> antiEntities;
    public static String antiMode;
    public static boolean isBlacklist;

    public static Set<String> blacklistedWorlds;
    public static YamlConfiguration spigotConfig;
    public static volatile double maxTrackingRange = 144.0;
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Class<?>, Double>> trackingRangeCache = new ConcurrentHashMap<>();

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
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up old config.yml before migrating: " + e.getMessage());
        }

        YamlConfiguration fresh;
        try (var in = plugin.getResource("config.yml")) {
            if (in == null) {
                plugin.getLogger().severe("Bundled config.yml not found inside the jar; skipping migration.");
                return;
            }
            fresh = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read bundled config.yml: " + e.getMessage());
            return;
        }

        for (String key : current.getKeys(true)) {
            if (key.equals("config_version")) continue;
            Object value = current.get(key);
            if (value instanceof ConfigurationSection) continue;
            if (fresh.contains(key)) fresh.set(key, value);
        }
        fresh.set("config_version", CONFIG_VERSION);

        try {
            fresh.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save migrated config.yml: " + e.getMessage());
            return;
        }

        plugin.getLogger().info("config.yml migrated to version " + CONFIG_VERSION + ". Your previous file was backed up as " + backup.getName() + ".");
    }

    public static void setConfig() {
        FileConfiguration config = plugin.getConfig();
        loadSpigotConfig();

        isCheckingEnabled = config.getBoolean("checking.enabled", true);
        checkingPeriodTicks = clampTicks("checking.period_ticks", config.getLong("checking.period_ticks", 1), PERIOD_TICKS_MAX);
        checkingStaggerGroups = clampInt("checking.stagger_groups", config.getInt("checking.stagger_groups", 3), 1, STAGGER_GROUPS_MAX);
        checkingDistanceOverride = clampDouble("checking.distance_override", config.getDouble("checking.distance_override", 10), 0, DISTANCE_OVERRIDE_MAX, 10);
        checkingBoundingBoxExtraValue = clampDouble("checking.bounding_box_extra_value", config.getDouble("checking.bounding_box_extra_value", 0), 0, BOUNDING_BOX_EXTRA_MAX, 0);
        checkingVerticesLayers = clampInt("checking.vertices_layers", config.getInt("checking.vertices_layers", 4), VERTICES_LAYERS_MIN, VERTICES_LAYERS_MAX);

        boolean prevAsyncEnabled = checkingAsyncEnabled;
        checkingAsyncEnabled = config.getBoolean("async.enabled", true);
        checkingAsyncThreads = clampInt("async.threads", config.getInt("async.threads", 2), 1, ASYNC_THREADS_MAX);
        checkingAsyncChunkSnapshotTtlTicks = clampInt("async.chunk_snapshot_ttl_ticks", config.getInt("async.chunk_snapshot_ttl_ticks", 200), 1, SNAPSHOT_TTL_TICKS_MAX);
        if (prevAsyncEnabled != checkingAsyncEnabled) {
            RayTraceEngine.onAsyncModeChanged(checkingAsyncEnabled);
        }

        isPerspectiveCheckingEnabled = config.getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = clampDouble("perspective_checking.distances_from_head", config.getDouble("perspective_checking.distances_from_head", 4), 0, PERSPECTIVE_DISTANCE_MAX, 4);

        isDisplayNameEnabled = config.getBoolean("display_name.enabled", true);
        displayNamePeriodTicks = clampTicks("display_name.period_ticks", config.getLong("display_name.period_ticks", 1), DISPLAY_PERIOD_TICKS_MAX);
        displayNameLookaheadTicks = clampDouble("display_name.lookahead_ticks", config.getDouble("display_name.lookahead_ticks", 3.0), 0, DISPLAY_LOOKAHEAD_TICKS_MAX, 3.0);
        displayNameOffSetY = clampDouble("display_name.offset_y", config.getDouble("display_name.offset_y", 0), -DISPLAY_OFFSET_Y_MAX, DISPLAY_OFFSET_Y_MAX, 0);

        boolean prevDebugEnabled = isDebugEnabled;
        isDebugEnabled = config.getBoolean("debug.enabled", false);

        isUpdateCheckerEnabled = config.getBoolean("update_checker.enabled", true);

        antiMode = "whitelist";
        String rawMode = config.getString("anti_mode", "whitelist");
        if ("whitelist".equalsIgnoreCase(rawMode.trim()) || "blacklist".equalsIgnoreCase(rawMode.trim())) {
            antiMode = rawMode.trim().toLowerCase(Locale.ROOT);
        } else {
            plugin.getLogger().warning("Invalid anti_mode '" + rawMode + "' (expected whitelist or blacklist). Falling back to whitelist.");
        }
        isBlacklist = "blacklist".equals(antiMode);

        antiEntities = new HashSet<>();
        for (String entity : config.getStringList("anti_entities")) {
            if (entity == null) continue;
            String key = entity.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            try {
                EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("anti_entities contains unknown entity type '" + entity + "' - ignoring it. "
                        + "A misspelled entry means that entity is NOT protected.");
                continue;
            }
            antiEntities.add(key);
        }
        if (!isBlacklist && antiEntities.isEmpty()) {
            plugin.getLogger().warning("anti_mode is whitelist but anti_entities is empty: no entity type is protected right now.");
        }

        blacklistedWorlds = new HashSet<>();
        for (String world : config.getStringList("blacklisted_world")) {
            if (world == null) continue;
            blacklistedWorlds.add(world.toLowerCase(Locale.ROOT));
        }

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

    private static void warnClamped(String key, Object original, Object used) {
        plugin.getLogger().warning("Config value '" + key + "' (" + original + ") is out of range; using " + used + " instead.");
    }

    private static int clampInt(String key, int value, int min, int max) {
        int c = Math.clamp(value, min, max);
        if (c != value) warnClamped(key, value, c);
        return c;
    }

    private static long clampTicks(String key, long value, long max) {
        long c = Math.clamp(value, 1L, max);
        if (c != value) warnClamped(key, value, c);
        return c;
    }

    private static double clampDouble(String key, double value, double min, double max, double fallback) {
        double v = Double.isFinite(value) ? value : fallback;
        double c = Math.clamp(v, min, max);
        if (c != value) warnClamped(key, value, c);
        return c;
    }

    public static boolean isWorldAllowed(String worldName) {
        return !blacklistedWorlds.contains(worldName.toLowerCase(java.util.Locale.ROOT));
    }

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
        ConfigurationSection worldSettings = spigotConfig.getConfigurationSection("world-settings");
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

    public static void clearTrackingRangeCache() {
        trackingRangeCache.clear();
    }

    public static double getSpigotTrackingRange(Entity entity) {
        String worldName = entity.getWorld().getName();
        Class<?> entityClass = entity.getClass();

        var perWorld = trackingRangeCache.computeIfAbsent(worldName, w -> new ConcurrentHashMap<>());
        Double cached = perWorld.get(entityClass);
        if (cached != null) return cached;

        double range = computeTrackingRange(entity) + 16;
        perWorld.put(entityClass, range);
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
        sender.sendMessage(formatToString(sender, "&6RayTrace Anti Entity ESP Config (File):"));
        sender.sendMessage(formatToString(sender, "&econfig_version: &f" + cfg.getInt("config_version", -1) + " &7(jar: " + CONFIG_VERSION + ")"));
        sender.sendMessage(formatToString(sender, "&echecking.enabled: &f" + cfg.getBoolean("checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&echecking.period_ticks: &f" + cfg.getLong("checking.period_ticks", 1)));
        sender.sendMessage(formatToString(sender, "&echecking.stagger_groups: &f" + cfg.getInt("checking.stagger_groups", 3)));
        sender.sendMessage(formatToString(sender, "&echecking.distance_override: &f" + cfg.getDouble("checking.distance_override", 10)));
        sender.sendMessage(formatToString(sender, "&echecking.bounding_box_extra_value: &f" + cfg.getDouble("checking.bounding_box_extra_value", 0)));
        sender.sendMessage(formatToString(sender, "&echecking.vertices_layers: &f" + cfg.getInt("checking.vertices_layers", 4)));
        sender.sendMessage(formatToString(sender, "&easync.enabled: &f" + cfg.getBoolean("async.enabled", true)));
        sender.sendMessage(formatToString(sender, "&easync.threads: &f" + cfg.getInt("async.threads", 2)));
        sender.sendMessage(formatToString(sender, "&easync.chunk_snapshot_ttl_ticks: &f" + cfg.getInt("async.chunk_snapshot_ttl_ticks", 200)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.enabled: &f" + cfg.getBoolean("perspective_checking.enabled", true)));
        sender.sendMessage(formatToString(sender, "&eperspective_checking.distances_from_head: &f" + cfg.getDouble("perspective_checking.distances_from_head", 4)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.enabled: &f" + cfg.getBoolean("display_name.enabled", true)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.period_ticks: &f" + cfg.getLong("display_name.period_ticks", 1)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.offset_y: &f" + cfg.getDouble("display_name.offset_y", 0)));
        sender.sendMessage(formatToString(sender, "&edisplay_name.lookahead_ticks: &f" + cfg.getDouble("display_name.lookahead_ticks", 3.0)));
        sender.sendMessage(formatToString(sender, "&edebug.enabled: &f" + cfg.getBoolean("debug.enabled", false)));
        sender.sendMessage(formatToString(sender, "&eupdate_checker.enabled: &f" + cfg.getBoolean("update_checker.enabled", true)));
        sender.sendMessage(formatToString(sender, "&eanti_entities: &f" + String.join(", ", cfg.getStringList("anti_entities"))));
        sender.sendMessage(formatToString(sender, "&eanti_mode: &f" + cfg.getString("anti_mode", "whitelist")));
        sender.sendMessage(formatToString(sender, "&eblacklisted_world: &f" + String.join(", ", cfg.getStringList("blacklisted_world"))));
    }
}