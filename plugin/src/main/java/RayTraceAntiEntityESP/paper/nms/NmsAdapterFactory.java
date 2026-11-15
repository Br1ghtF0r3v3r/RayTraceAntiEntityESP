package RayTraceAntiEntityESP.paper.nms;

import org.bukkit.Bukkit;

import static RayTraceAntiEntityESP.paper.Main.plugin;

public final class NmsAdapterFactory {

    private static volatile NmsAdapter instance;

    private NmsAdapterFactory() {}

    public static NmsAdapter get() {
        if (instance == null) throw new IllegalStateException("NmsAdapter not initialized — call NmsAdapterFactory.init() in onLoad()");
        return instance;
    }

    public static void init() {
        String v = Bukkit.getServer().getMinecraftVersion();
        String className;
        if (v.startsWith("1.21")) {
            className = "RayTraceAntiEntityESP.paper.nms.v1_21.NmsAdapter_1_21_x";
        } else if (isSupported26_1(v)) {
            className = "RayTraceAntiEntityESP.paper.nms.v26_1.NmsAdapter_26_1_x";
        } else if (isVersionLine(v, "26.2")) {
            className = "RayTraceAntiEntityESP.paper.nms.v26_2.NmsAdapter_26_2";
        } else if (isVersionLine(v, "26.3") || v.startsWith("1.22") || v.startsWith("1.23")) {
            className = "RayTraceAntiEntityESP.paper.nms.v26_3.NmsAdapter_26_3";
        } else {
            throw new IllegalStateException("Unsupported Minecraft version: " + v + " — RayTraceAntiEntityESP supports 1.21.x, 26.1 through 26.1.2, 26.2.x, and 26.3.x");
        }
        try {
            instance = (NmsAdapter) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load NMS adapter for " + v, e);
        }
        plugin.getLogger().info("[RayTraceAntiEntityESP] Loaded NMS adapter for Minecraft " + v);
    }

    private static boolean isVersionLine(String version, String line) {
        return version.equals(line) || version.startsWith(line + ".");
    }

    private static boolean isSupported26_1(String version) {
        return version.equals("26.1")
                || version.equals("26.1.1")
                || version.equals("26.1.2");
    }
}
