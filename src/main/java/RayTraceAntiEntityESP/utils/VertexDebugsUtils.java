package RayTraceAntiEntityESP.utils;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.utils.FakeNameDisplayUtils.FAKE_DISPLAY_NAME_KEY;

public class VertexDebugsUtils {
    public static final NamespacedKey DEBUG_KEY = new NamespacedKey(plugin, "is_debug_vertex");
    public static final Map<UUID, Map<UUID, List<BlockDisplay>>> VertexDebugBlockDisplays = new HashMap<>();

    public static boolean shouldSkipSpawningVertexDebugBlockDisplays(Entity entity) {
        return entity.getPersistentDataContainer().has(DEBUG_KEY, PersistentDataType.BYTE) || entity.getPersistentDataContainer().has(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE);
    }

    public static void applyVertexDebugBlockDisplays(Player viewer, Entity entity, List<Vector> vertices, Set<Integer> visibleVertices) {
        if (shouldSkipSpawningVertexDebugBlockDisplays(entity)) return;
        if (!entity.isValid() || entity.isDead()) {
            removeVertexDebugBlockDisplays(viewer, entity);
            return;
        }
        Map<UUID, List<BlockDisplay>> viewerDisplays = VertexDebugBlockDisplays.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        List<BlockDisplay> entityDisplays = viewerDisplays.computeIfAbsent(entity.getUniqueId(), k -> new ArrayList<>());
        spawnVertexDebugBlockDisplays(viewer, entity, vertices, visibleVertices, entityDisplays);
        cleanupExcessVertexDebugBlockDisplays(entityDisplays, vertices.size());
        for (int i = 0; i < Math.min(entityDisplays.size(), vertices.size()); i++) {
            boolean visible = visibleVertices.contains(i);
            entityDisplays.get(i).setBlock((visible ? Material.LIME_WOOL : Material.RED_WOOL).createBlockData());
            entityDisplays.get(i).teleport(getVertexLocation(entity.getWorld(), vertices.get(i)));
        }
    }

    public static void spawnVertexDebugBlockDisplays(Player viewer, Entity entity, List<Vector> vertices, Set<Integer> visibleVertices, List<BlockDisplay> existing) {
        for (int i = existing.size(); i < vertices.size(); i++) {
            boolean visible = visibleVertices.contains(i);
            Vector vertex = vertices.get(i);
            Location loc = getVertexLocation(entity.getWorld(), vertex);
            BlockDisplay display = entity.getWorld().spawn(loc, BlockDisplay.class, d -> {
                d.setBlock((visible ? Material.LIME_WOOL : Material.RED_WOOL).createBlockData());
                d.getPersistentDataContainer().set(DEBUG_KEY, PersistentDataType.BYTE, (byte) 1);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(0.05f, 0.05f, 0.05f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
            });
            viewer.showEntity(plugin, display);
            existing.add(display);
        }
    }

    public static void cleanupExcessVertexDebugBlockDisplays(List<BlockDisplay> existing, int targetSize) {
        for (int i = targetSize; i < existing.size(); i++) existing.get(i).remove();
        if (targetSize < existing.size()) existing.subList(targetSize, existing.size()).clear();
    }

    public static void removeVertexDebugBlockDisplays(Player viewer, Entity entity) {
        Map<UUID, List<BlockDisplay>> viewerDisplays = VertexDebugBlockDisplays.get(viewer.getUniqueId());
        if (viewerDisplays == null) return;

        List<BlockDisplay> entityDisplays = viewerDisplays.remove(entity.getUniqueId());
        if (entityDisplays == null) return;

        for (BlockDisplay display : entityDisplays) display.remove();
        if (viewerDisplays.isEmpty()) VertexDebugBlockDisplays.remove(viewer.getUniqueId());
    }

    public static void removeVertexDebugBlockDisplays(Player viewer) {
        Map<UUID, List<BlockDisplay>> viewerDisplays = VertexDebugBlockDisplays.remove(viewer.getUniqueId());
        if (viewerDisplays == null) return;
        for (List<BlockDisplay> entityDisplays : viewerDisplays.values()) {
            for (BlockDisplay display : entityDisplays) display.remove();
        }
    }

    public static void removeAllVertexDebugBlockDisplays() {
        for (Map<UUID, List<BlockDisplay>> viewerDebugBlockDisplay : VertexDebugBlockDisplays.values()) {
            for (List<BlockDisplay> blockDisplayList : viewerDebugBlockDisplay.values()) {
                for (BlockDisplay blockDisplay : blockDisplayList) blockDisplay.remove();
            }
        }
        VertexDebugBlockDisplays.clear();
    }

    public static Location getVertexLocation(World world, Vector vertex) {
        return new Location(world, vertex.getX() - 0.025, vertex.getY() - 0.025, vertex.getZ() - 0.025);
    }
}

