package RayTraceAntiEntityESP.config;

import static RayTraceAntiEntityESP.Main.plugin;

public class Config {

    public static double boundingBoxExtraValue;
    public static int samplePointsPerCorner;
    public static double distanceOverride;
    public static boolean perspectiveCheckingEnabled;
    public static double perspectiveCheckingDistance;
    public static long checkingIntervalTicks;

    public static void setConfig() {

        boundingBoxExtraValue = plugin.getConfig().getDouble("bounding_box_extra_value", 0.1);
        samplePointsPerCorner = plugin.getConfig().getInt("sample_points_per_corner", 8);
        distanceOverride = plugin.getConfig().getDouble("distances_override", -1);
        perspectiveCheckingEnabled = plugin.getConfig().getBoolean("perspective_checking.enabled", true);
        perspectiveCheckingDistance = plugin.getConfig().getDouble("perspective_checking.distances", 4);
        checkingIntervalTicks = plugin.getConfig().getLong("checking_interval_ticks", 1);

    }

}
