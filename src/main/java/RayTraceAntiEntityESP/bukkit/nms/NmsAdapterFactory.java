package RayTraceAntiEntityESP.bukkit.nms;

import org.bukkit.Bukkit;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public final class NmsAdapterFactory {

    private static volatile NmsAdapter instance;

    private NmsAdapterFactory() {}

    public static NmsAdapter get() {
        if (instance == null) throw new IllegalStateException("NmsAdapter not initialized — call NmsAdapterFactory.init() in onLoad()");
        return instance;
    }

    public static void init() {
        String v = Bukkit.getServer().getMinecraftVersion();
        NmsAdapter adapter;
        if (v.startsWith("1.21")) {
            adapter = new RayTraceAntiEntityESP.bukkit.nms.v1_21.NmsAdapter_1_21_x();
        } else if (v.startsWith("26") || v.startsWith("1.22") || v.startsWith("1.23")) {
            adapter = new RayTraceAntiEntityESP.bukkit.nms.v26.NmsAdapter_26_x();
        } else {
            throw new IllegalStateException("Unsupported Minecraft version: " + v + " — RayTraceAntiEntityESP supports 1.21.x and 26.x");
        }
        instance = adapter;
        plugin.getLogger().info("[RayTraceAntiEntityESP] Loaded NMS adapter for Minecraft " + v);
    }
}
