package RayTraceAntiEntityESP.paper.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

import static RayTraceAntiEntityESP.paper.Main.plugin;

public final class PacketEventsBridge {

    private static Listener pendingListener;

    private PacketEventsBridge() {
    }

    public static void registerIfAvailable() {
        if (tryHook()) return;
        if (pendingListener != null) return;

        pendingListener = new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                tryPendingHook(this);
            }
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if (event.getPlugin().getName().equalsIgnoreCase("packetevents")) {
                    tryPendingHook(this);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(pendingListener, plugin);
    }

    private static boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("packetevents") != null;
    }

    private static boolean tryHook() {
        if (!isPresent()) return false;
        try {
            return PacketEventsHook.install();
        } catch (Throwable t) {
            plugin.getLogger().warning("Found PacketEvents but failed to hook into it for team-color/glow " + "compatibility (mismatched version?): " + t);
            return false;
        }
    }

    private static void tryPendingHook(Listener listener) {
        if (!tryHook()) return;
        HandlerList.unregisterAll(listener);
        if (pendingListener == listener) pendingListener = null;
    }

    public static void shutdown() {
        Listener pending = pendingListener;
        pendingListener = null;
        if (pending != null) HandlerList.unregisterAll(pending);
        if (isPresent()) {
            try {
                PacketEventsHook.uninstall();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not remove PacketEvents compatibility listener: " + t);
            }
        }
    }
}
