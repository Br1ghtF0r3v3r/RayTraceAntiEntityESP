package RayTraceAntiEntityESP.engine;

import RayTraceAntiEntityESP.misc.lycopod.Math;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;


public class RaycastUtils {
    public static boolean collideSolid(Player player, Vector endpoint) {
        Vector direction = endpoint.subtract(player.getEyeLocation().toVector());

        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                direction,
                direction.length(),
                FluidCollisionMode.NEVER,
                true
        );

        if (result == null) {
            return false;
        }

        if (!result.getHitBlock().isSolid()) {
            return false;
        }

        return true;
    }

    public static boolean isEntityVisible(Player lookingPlayer, LivingEntity entity) {
        List<Vector> vertices = getEntityVertices(entity);

        for (Vector vertex : vertices) {
            if (!collideSolid(lookingPlayer, vertex)) {
                return true;
            }
        }
        return false;
    }

    public static List<Vector> getEntityVertices(LivingEntity entity) {
        ArrayList<Vector> vertices = new ArrayList<>();

        BoundingBox boundingBox = entity.getBoundingBox();
        double minX = boundingBox.getMinX();
        double minY = boundingBox.getMinY();
        double minZ = boundingBox.getMinZ();
        double maxX = boundingBox.getMaxX();
        double maxY = boundingBox.getMaxY();
        double maxZ = boundingBox.getMaxZ();

        double midX = boundingBox.getCenterX();
        double midY = boundingBox.getCenterY();
        double midZ = boundingBox.getCenterZ();

//        // bottom face
//        vertices.add(new Vector(minX, minY, minZ));
//        vertices.add(new Vector(minX, minY, maxZ));
//        vertices.add(new Vector(maxX, minY, maxZ));
//        vertices.add(new Vector(maxX, minY, minZ));
//
//
//        // top face
//        vertices.add(new Vector(minX, maxY, minZ));
//        vertices.add(new Vector(minX, maxY, maxZ));
//        vertices.add(new Vector(maxX, maxY, maxZ));
//        vertices.add(new Vector(maxX, maxY, minZ));

        int extras = 8;

        // mid spine (extra*4 points)
        for (int i = 0; i < extras; i++) {
            double y = Math.lerp(minY, maxY, ((double) i) / (extras-1));
            vertices.add(new Vector(minX, y, minZ));
            vertices.add(new Vector(minX, y, maxZ));
            vertices.add(new Vector(maxX, y, maxZ));
            vertices.add(new Vector(maxX, y, minZ));
        }


        return vertices;
    }
}
