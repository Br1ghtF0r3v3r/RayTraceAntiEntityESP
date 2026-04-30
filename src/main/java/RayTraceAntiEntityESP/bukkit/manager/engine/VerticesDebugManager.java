package RayTraceAntiEntityESP.bukkit.manager.engine;

import RayTraceAntiEntityESP.bukkit.utils.VerticesDebugUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VerticesDebugManager {

    private static final Map<UUID, Map<UUID, List<VerticesDebugUtils>>> markers = new ConcurrentHashMap<>();

    private static boolean shouldShow(Entity entity) {
        if (entity.isDead()) return false;
        if (entity instanceof Player player && ((CraftPlayer) player).getHandle().hasDisconnected()) return false;
        return entity.isValid();
    }

    public static void applyDisplay(Player viewer, Entity entity, List<Vector> vertices, List<Boolean> visibilities) {
        if (!shouldShow(entity)) {
            removeDisplay(viewer.getUniqueId(), entity.getUniqueId());
            return;
        }
        Map<UUID, List<VerticesDebugUtils>> inner = markers.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
        List<VerticesDebugUtils> existing = inner.get(entity.getUniqueId());
        if (existing != null && existing.size() == vertices.size()) {
            for (int i = 0; i < vertices.size(); i++) {
                Vector v = vertices.get(i);
                existing.get(i).teleport(v.getX(), v.getY(), v.getZ());
                existing.get(i).updateMeta(visibilities.get(i));
            }
        } else {
            despawnList(existing);
            List<VerticesDebugUtils> fresh = new ArrayList<>(vertices.size());
            for (int i = 0; i < vertices.size(); i++) {
                Vector v = vertices.get(i);
                VerticesDebugUtils marker = new VerticesDebugUtils(viewer);
                marker.setPos(v.getX(), v.getY(), v.getZ());
                marker.spawn(visibilities.get(i));
                fresh.add(marker);
            }
            inner.put(entity.getUniqueId(), fresh);
        }
    }

    public static void removeDisplay(UUID viewerUUID, UUID entityUUID) {
        Map<UUID, List<VerticesDebugUtils>> inner = markers.get(viewerUUID);
        if (inner == null) return;
        List<VerticesDebugUtils> list = inner.remove(entityUUID);
        if (list == null) return;
        despawnList(list);
    }

    public static void removeDisplay(UUID viewerUUID) {
        Map<UUID, List<VerticesDebugUtils>> inner = markers.remove(viewerUUID);
        if (inner == null) return;
        for (List<VerticesDebugUtils> list : inner.values()) {
            if (list == null) continue;
            despawnList(list);
        }
    }

    public static void removeDisplayForEntity(UUID entityUUID) {
        for (Map<UUID, List<VerticesDebugUtils>> inner : markers.values()) {
            if (inner == null) continue;
            List<VerticesDebugUtils> list = inner.remove(entityUUID);
            if (list == null) continue;
            despawnList(list);
        }
    }

    public static void removeAllDisplays() {
        for (Map<UUID, List<VerticesDebugUtils>> inner : markers.values()) {
            if (inner == null) continue;
            for (List<VerticesDebugUtils> list : inner.values()) {
                if (list == null) continue;
                despawnList(list);
            }
        }
        markers.clear();
    }

    private static void despawnList(List<VerticesDebugUtils> list) {
        if (list == null) return;
        for (VerticesDebugUtils marker : list) {
            try {
                marker.despawn();
            } catch (Throwable ignored) {}
        }
    }
}