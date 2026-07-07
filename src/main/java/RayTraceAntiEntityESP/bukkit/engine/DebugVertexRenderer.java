package RayTraceAntiEntityESP.bukkit.engine;

import RayTraceAntiEntityESP.bukkit.utils.DebugVertexUtils;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DebugVertexRenderer {

    private static final ConcurrentHashMap<UUID, Map<UUID, List<DebugVertexUtils>>> markers = new ConcurrentHashMap<>();

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
        Map<UUID, List<DebugVertexUtils>> inner = markers.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
        List<DebugVertexUtils> existing = inner.get(entity.getUniqueId());
        if (existing != null && existing.size() == vertices.size()) {
            for (int i = 0; i < vertices.size(); i++) {
                Vector v = vertices.get(i);
                existing.get(i).teleport(v.getX(), v.getY(), v.getZ());
                existing.get(i).updateMeta(visibilities.get(i));
            }
        } else {
            despawnList(existing);
            List<DebugVertexUtils> fresh = new ArrayList<>(vertices.size());
            for (int i = 0; i < vertices.size(); i++) {
                Vector v = vertices.get(i);
                DebugVertexUtils marker = new DebugVertexUtils(viewer);
                marker.setPos(v.getX(), v.getY(), v.getZ());
                marker.spawn(visibilities.get(i));
                fresh.add(marker);
            }
            inner.put(entity.getUniqueId(), fresh);
        }
    }

    public static void removeDisplay(UUID viewerUUID, UUID entityUUID) {
        Map<UUID, List<DebugVertexUtils>> inner = markers.get(viewerUUID);
        if (inner == null) return;
        List<DebugVertexUtils> list = inner.remove(entityUUID);
        if (list == null) return;
        despawnList(list);
    }

    public static void removeDisplay(UUID viewerUUID) {
        Map<UUID, List<DebugVertexUtils>> inner = markers.remove(viewerUUID);
        if (inner == null) return;
        for (List<DebugVertexUtils> list : inner.values()) {
            if (list == null) continue;
            despawnList(list);
        }
    }

    public static void removeDisplayForEntity(UUID entityUUID) {
        for (Map<UUID, List<DebugVertexUtils>> inner : markers.values()) {
            if (inner == null) continue;
            List<DebugVertexUtils> list = inner.remove(entityUUID);
            if (list == null) continue;
            despawnList(list);
        }
    }

    public static void removeAllDisplays() {
        for (Map<UUID, List<DebugVertexUtils>> inner : markers.values()) {
            if (inner == null) continue;
            for (List<DebugVertexUtils> list : inner.values()) {
                if (list == null) continue;
                despawnList(list);
            }
        }
        markers.clear();
    }

    private static void despawnList(List<DebugVertexUtils> list) {
        if (list == null) return;
        for (DebugVertexUtils marker : list) {
            try {
                marker.despawn();
            } catch (Throwable ignored) {
            }
        }
    }
}