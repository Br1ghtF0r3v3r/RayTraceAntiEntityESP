package RayTraceAntiEntityESP.misc;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;


public class RaytraceDebugs {

    private static final Map<UUID, List<BlockDisplay>> debugDisplays = new HashMap<>();

    public static void stopDebug() {
        debugDisplays.values().forEach(list -> list.forEach(Entity::remove));
        debugDisplays.clear();
    }

    public static void despawnVertexDisplays(LivingEntity entity) {
        List<BlockDisplay> displays = debugDisplays.remove(entity.getUniqueId());
        if (displays != null) displays.forEach(Entity::remove);
    }

    public static void spawnVertexDisplays(Player player, LivingEntity entity, List<Vector> vertices, Set<Integer> visibleVertices) {
        if (!entity.isValid() || entity.isDead()) {
            despawnVertexDisplays(entity);
            return;
        }

        List<BlockDisplay> existing = debugDisplays.getOrDefault(entity.getUniqueId(), new ArrayList<>());

        for (int i = 0; i < Math.min(existing.size(), vertices.size()); i++) {
            Vector vertex = vertices.get(i);
            Material mat = visibleVertices.contains(i) ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            BlockDisplay display = existing.get(i);
            display.setBlock(mat.createBlockData());
            display.teleport(new Location(entity.getWorld(),
                    vertex.getX() - 0.025,
                    vertex.getY() - 0.025,
                    vertex.getZ() - 0.025));
            display.setVisibleByDefault(false);
            player.showEntity(plugin, display);
        }

        for (int i = existing.size(); i < vertices.size(); i++) {
            Vector vertex = vertices.get(i);
            Material mat = visibleVertices.contains(i) ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            Location loc = new Location(entity.getWorld(),
                    vertex.getX() - 0.025,
                    vertex.getY() - 0.025,
                    vertex.getZ() - 0.025);
            BlockDisplay display = entity.getWorld().spawn(loc, BlockDisplay.class, d -> {
                d.setBlock(mat.createBlockData());
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

        for (int i = vertices.size(); i < existing.size(); i++) {
            existing.get(i).remove();
        }
        if (vertices.size() < existing.size()) {
            existing.subList(vertices.size(), existing.size()).clear();
        }

        debugDisplays.put(entity.getUniqueId(), existing);
    }

}
