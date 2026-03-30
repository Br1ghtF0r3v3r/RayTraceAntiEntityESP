package RayTraceAntiEntityESP.utils;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.utils.FakeNameDisplayUtils.FAKE_DISPLAY_NAME_KEY;

public class RayTraceDebugsUtils {

    public static final NamespacedKey DEBUG_KEY = new NamespacedKey(plugin, "is_debug_vertex");
    private static final NamespacedKey UUID_LIST_KEY = new NamespacedKey(plugin, "debug_display_uuids");

    public static void stopDebug() {
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (shouldSkipDebug(display)) {
                    display.remove();
                }
            }
        }
    }

    public static void despawnVertexDebugDisplays(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(UUID_LIST_KEY, PersistentDataType.STRING)) {
            String uuidString = pdc.get(UUID_LIST_KEY, PersistentDataType.STRING);
            if (uuidString != null && !uuidString.isEmpty()) {
                for (String id : uuidString.split(",")) {
                    try {
                        Entity display = Bukkit.getEntity(UUID.fromString(id));
                        if (display != null) display.remove();
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            pdc.remove(UUID_LIST_KEY);
        }
    }

    public static void spawnVertexDebugDisplays(Player player, Entity entity, List<Vector> vertices, Set<Integer> visibleVertices) {
        if (shouldSkipDebug(entity)) return;

        if (!entity.isValid() || entity.isDead()) {
            despawnVertexDebugDisplays(entity);
            return;
        }

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        List<BlockDisplay> existing = new ArrayList<>();

        if (pdc.has(UUID_LIST_KEY, PersistentDataType.STRING)) {
            String uuidString = pdc.get(UUID_LIST_KEY, PersistentDataType.STRING);
            if (uuidString != null && !uuidString.isEmpty()) {
                for (String id : uuidString.split(",")) {
                    try {
                        Entity d = Bukkit.getEntity(UUID.fromString(id));
                        if (d instanceof BlockDisplay) existing.add((BlockDisplay) d);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        updateExistingDisplays(player, vertices, visibleVertices, existing);

        if (vertices.size() > existing.size()) {
            spawnNewDisplays(player, entity, vertices, visibleVertices, existing);
        }

        if (existing.size() > vertices.size()) {
            cleanupExcessDisplays(existing, vertices.size());
        }

        if (existing.isEmpty()) {
            pdc.remove(UUID_LIST_KEY);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < existing.size(); i++) {
                sb.append(existing.get(i).getUniqueId());
                if (i < existing.size() - 1) sb.append(",");
            }
            pdc.set(UUID_LIST_KEY, PersistentDataType.STRING, sb.toString());
        }
    }

    public static boolean shouldSkipDebug(Entity entity) {
        return entity.getPersistentDataContainer().has(DEBUG_KEY, PersistentDataType.BYTE) || entity.getPersistentDataContainer().has(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE);
    }

    public static void updateExistingDisplays(Player player, List<Vector> vertices, Set<Integer> visibleVertices, List<BlockDisplay> existing) {
        int count = Math.min(existing.size(), vertices.size());
        for (int i = 0; i < count; i++) {
            BlockDisplay display = existing.get(i);
            applyDisplayProperties(player, display, vertices.get(i), visibleVertices.contains(i));
        }
    }

    public static void spawnNewDisplays(Player player, Entity entity, List<Vector> vertices, Set<Integer> visibleVertices, List<BlockDisplay> existing) {
        for (int i = existing.size(); i < vertices.size(); i++) {
            boolean visible = visibleVertices.contains(i);
            Vector vertex = vertices.get(i);
            Location loc = getVertexLocation(entity.getWorld(), vertex);

            BlockDisplay display = entity.getWorld().spawn(loc, BlockDisplay.class, d -> {
                d.setBlock((visible ? Material.LIME_WOOL : Material.RED_WOOL).createBlockData());
                d.getPersistentDataContainer().set(DEBUG_KEY, PersistentDataType.BYTE, (byte) 1);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(0.05f, 0.05f, 0.05f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                d.setPersistent(false);
                d.setVisibleByDefault(false);
            });

            player.showEntity(plugin, display);
            existing.add(display);
        }
    }

    public static void cleanupExcessDisplays(List<BlockDisplay> existing, int targetSize) {
        for (int i = targetSize; i < existing.size(); i++) {
            existing.get(i).remove();
        }
        existing.subList(targetSize, existing.size()).clear();
    }

    public static void applyDisplayProperties(Player player, BlockDisplay display, Vector vertex, boolean isVisible) {
        Material mat = isVisible ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        display.setBlock(mat.createBlockData());
        display.teleport(getVertexLocation(display.getWorld(), vertex));
        display.setVisibleByDefault(false);
        player.showEntity(plugin, display);
    }

    public static Location getVertexLocation(World world, Vector vertex) {
        return new Location(world, vertex.getX() - 0.025, vertex.getY() - 0.025, vertex.getZ() - 0.025);
    }
}
