package RayTraceAntiEntityESP.bukkit.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public final class PacketEventsBridge {

    private PacketEventsBridge() {
    }

    public static void registerIfAvailable() {
        if (isPresent()) {
            tryHook();
            return;
        }

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                if (isPresent()) tryHook();
            }
        }, plugin);
    }

    private static boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("packetevents") != null;
    }

    private static void tryHook() {
        try {
            PacketEventsHook.install();
        } catch (Throwable t) {
            plugin.getLogger().warning("Found PacketEvents but failed to hook into it for team-color/glow " + "compatibility (mismatched version?): " + t);
        }
    }
}