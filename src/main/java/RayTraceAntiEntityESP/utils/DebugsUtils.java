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

    public record DebugEntry(List<BlockDisplay> displays, List<Vector> vertices, Set<Integer> visibleVertices) {
    }

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

                List<BlockDisplay> displays = entry.displays();
                List<Vector> vertices = entry.vertices();
                Set<Integer> visibleVertices = entry.visibleVertices();

                for (int i = vertices.size(); i < displays.size(); i++) displays.get(i).remove();
                if (vertices.size() < displays.size()) displays.subList(vertices.size(), displays.size()).clear();

                for (int i = 0; i < vertices.size(); i++) {
                    boolean visible = visibleVertices.contains(i);
                    Material mat = visible ? Material.LIME_WOOL : Material.RED_WOOL;
                    if (i < displays.size()) {
                        displays.get(i).setBlock(mat.createBlockData());
                        displays.get(i).teleport(getLocation(entity.getWorld(), vertices.get(i)));
                    } else {
                        displays.add(spawnDisplay(viewer, entity, vertices.get(i), visible));
                    }
                }
            }
        }
    }

    public static void applyDisplay(Player viewer, Entity entity, List<Vector> vertices, Set<Integer> visibleVertices) {
        boolean shouldSkipDebug = entity.getPersistentDataContainer().has(DEBUG_KEY, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE);
        if (shouldSkipDebug) return;
        if (!entity.isValid() || entity.isDead()) {
            removeDisplay(viewer, entity);
            return;
        }
        Map<UUID, DebugEntry> viewerDebug = debugs.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        DebugEntry existingDisplay = viewerDebug.get(entity.getUniqueId());
        List<BlockDisplay> displays;
        if (existingDisplay == null) {
            displays = new ArrayList<>();
        } else {
            displays = existingDisplay.displays();
        }
        viewerDebug.put(entity.getUniqueId(), new DebugEntry(displays, new ArrayList<>(vertices), new HashSet<>(visibleVertices)));
    }

    public static BlockDisplay spawnDisplay(Player viewer, Entity entity, Vector vertex, boolean visible) {
        BlockDisplay display = entity.getWorld().spawn(getLocation(entity.getWorld(), vertex), BlockDisplay.class, d -> {
            d.setBlock((visible ? Material.LIME_WOOL : Material.RED_WOOL).createBlockData());
            d.getPersistentDataContainer().set(DEBUG_KEY, PersistentDataType.BYTE, (byte) 1);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.05f, 0.05f, 0.05f), new AxisAngle4f(0, 0, 0, 1)
            ));
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    public static void removeDisplay(Player viewer, Entity entity) {
        Map<UUID, DebugEntry> viewerDebug = debugs.get(viewer.getUniqueId());
        if (viewerDebug == null) return;
        DebugEntry entry = viewerDebug.remove(entity.getUniqueId());
        if (entry == null) return;
        for (BlockDisplay display : entry.displays()) display.remove();
        if (viewerDebug.isEmpty()) debugs.remove(viewer.getUniqueId());
    }

    public static void removeDisplay(Player viewer) {
        Map<UUID, DebugEntry> viewerDebug = debugs.remove(viewer.getUniqueId());
        if (viewerDebug == null) return;
        for (DebugEntry entry : viewerDebug.values()) {
            for (BlockDisplay display : entry.displays()) display.remove();
        }
    }

    public static void removeAllDisplays() {
        for (Map<UUID, DebugEntry> viewerDebug : debugs.values()) {
            for (DebugEntry entry : viewerDebug.values()) {
                for (BlockDisplay display : entry.displays()) display.remove();
            }
        }
        debugs.clear();
    }

    public static Location getLocation(World world, Vector vertex) {
        return new Location(world, vertex.getX() - 0.025, vertex.getY() - 0.025, vertex.getZ() - 0.025);
    }
}

