package RayTraceAntiEntityESP.config;

import static RayTraceAntiEntityESP.Main.plugin;

public class Config {

    public static boolean isCheckingEnabled;
    public static long checkingIntervalTicks;
    public static double checkingRange;
    public static double checkingDistanceOverride;

    public static double boundingBoxExtraValue;
    public static int samplePointsPerCorner;
    public static boolean isPerspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;

    public static void setConfig() {

        isCheckingEnabled = plugin.getConfig().getBoolean("enabled", true);
        checkingIntervalTicks = plugin.getConfig().getLong("checking_interval_ticks", 1);
        checkingRange = plugin.getConfig().getDouble("checking_range", 64);
        checkingDistanceOverride = plugin.getConfig().getDouble("checking_distance_override", -1);

        boundingBoxExtraValue = plugin.getConfig().getDouble("bounding_box_extra_value", 0.1);
        samplePointsPerCorner = plugin.getConfig().getInt("sample_points", 8);
        isPerspectiveCheckingEnabled = plugin.getConfig().getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = plugin.getConfig().getDouble("perspective_checking.distances", 4);

    }

}
