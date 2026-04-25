package RayTraceAntiEntityESP.utils;

import RayTraceAntiEntityESP.config.Config;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.utils.FakeNameDisplay.FAKE_DISPLAY_NAME_KEY;

public class DebugsUtils {

    private static BukkitTask task;

    public static final NamespacedKey DEBUG_KEY = new NamespacedKey(plugin, "is_debug_vertex");
    public static final Map<UUID, Map<UUID, DebugEntry>> debugs = new HashMap<>();

    public record DebugEntry(List<BlockDisplay> display, List<Vector> vertices, Set<Integer> visibleVertices) { }

    public static void killTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAllDisplays();
    }

    public static void startTask() {
        killTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, DebugsUtils::updateDisplays, 0L, Config.debugPeriodTicks);
    }

    public static void updateDisplays() {
        for (Map.Entry<UUID, Map<UUID, DebugEntry>> viewerEntry : debugs.entrySet()) {
            Player viewer = Bukkit.getPlayer(viewerEntry.getKey());
            if (viewer == null) continue;

            for (Map.Entry<UUID, DebugEntry> debugEntry : viewerEntry.getValue().entrySet()) {
                Entity entity = Bukkit.getEntity(debugEntry.getKey());
                if (entity == null) continue;

                DebugEntry entry = debugEntry.getValue();

                List<BlockDisplay> displays = entry.display();
                List<Vector> vertices = entry.vertices();
                Set<Integer> visibleVertices = entry.visibleVertices();

                for (int i = vertices.size(); i < displays.size(); i++) displays.get(i).remove();
                if (vertices.size() < displays.size()) displays.subList(vertices.size(), displays.size()).clear();

                for (int i = 0; i < vertices.size(); i++) {

                    boolean visible = visibleVertices.contains(i);
                    Material mat = visible ? Material.LIME_WOOL : Material.RED_WOOL;
                    Vector vertex = vertices.get(i);

                    if (i < displays.size()) {
                        displays.get(i).setBlock(mat.createBlockData());
                        displays.get(i).teleport(new Location(entity.getWorld(), vertex.getX() - 0.025, vertex.getY() - 0.025, vertex.getZ() - 0.025));
                    } else {
                        displays.add(spawnDisplay(entity));
                    }
                }
            }
        }
    }

    public static void applyDisplay(Player viewer, Entity entity, List<Vector> vertices, Set<Integer> visibleVertices) {
        if (entity.getPersistentDataContainer().has(DEBUG_KEY, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE)) return;
        if (!entity.isValid() || entity.isDead()) {
            removeDisplay(viewer, entity);
            return;
        }
        Map<UUID, DebugEntry> viewerDisplays = debugs.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        DebugEntry existing = viewerDisplays.get(entity.getUniqueId());
        List<BlockDisplay> displays = existing != null ? existing.display() : new ArrayList<>();

        for (int i = vertices.size(); i < displays.size(); i++) displays.get(i).remove();
        if (vertices.size() < displays.size()) displays.subList(vertices.size(), displays.size()).clear();

        for (int i = 0; i < vertices.size(); i++) {
            if (i > displays.size()) {
                displays.add(spawnDisplay(entity));
            }
        }

        viewerDisplays.put(entity.getUniqueId(), new DebugEntry(displays, new ArrayList<>(vertices), new HashSet<>(visibleVertices)));
    }

    public static BlockDisplay spawnDisplay(Entity entity) {
        Location loc = entity.getLocation();
        return entity.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.getPersistentDataContainer().set(DEBUG_KEY, PersistentDataType.BYTE, (byte) 1);
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.05f, 0.05f, 0.05f), new AxisAngle4f(0, 0, 0, 1)
            ));
        });
    }

    public static void removeDisplay(Player viewer, Entity entity) {
        Map<UUID, DebugEntry> viewerDebug = debugs.get(viewer.getUniqueId());
        if (viewerDebug == null) return;
        DebugEntry entry = viewerDebug.remove(entity.getUniqueId());
        if (entry == null) return;
        for (BlockDisplay display : entry.display()) display.remove();
        if (viewerDebug.isEmpty()) debugs.remove(viewer.getUniqueId());
    }

    public static void removeDisplay(Player viewer) {
        Map<UUID, DebugEntry> viewerDebug = debugs.remove(viewer.getUniqueId());
        if (viewerDebug == null) return;
        for (DebugEntry entry : viewerDebug.values()) {
            for (BlockDisplay display : entry.display()) display.remove();
        }
    }

    public static void removeAllDisplays() {
        for (Map<UUID, DebugEntry> viewerDebug : debugs.values()) {
            for (DebugEntry entry : viewerDebug.values()) {
                for (BlockDisplay display : entry.display()) display.remove();
            }
        }
        debugs.clear();
    }

}
