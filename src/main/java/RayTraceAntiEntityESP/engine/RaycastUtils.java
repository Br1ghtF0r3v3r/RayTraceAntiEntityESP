package RayTraceAntiEntityESP.engine;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

import static RayTraceAntiEntityESP.config.Config.*;
import static RayTraceAntiEntityESP.misc.Math.lerp;

public class RaycastUtils {

    public static boolean collideSolid(Player player, Vector endpoint) {
        World world = player.getWorld();
        Vector eyePos = player.getEyeLocation().toVector();
        Vector lookDir = player.getLocation().getDirection();
        if (!perspectiveCheckingEnabled) return hitsBlock(world, eyePos, endpoint);
        return hitsBlock(world, eyePos, endpoint)
                && hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir.clone().multiply(-1), perspectiveCheckingDistance), endpoint)
                && hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir, perspectiveCheckingDistance), endpoint);
    }

    private static Vector getThirdPersonPos(World world, Vector eyePos, Vector direction, double maxDistance) {
        direction = direction.normalize();
        RayTraceResult result = rayTrace(world, eyePos, direction, maxDistance);
        return result != null && result.getHitBlock() != null
                ? result.getHitPosition().subtract(direction.multiply(0.1))
                : eyePos.clone().add(direction.multiply(maxDistance));
    }

    private static boolean hitsBlock(World world, Vector origin, Vector endpoint) {
        Vector direction = endpoint.clone().subtract(origin);
        double distance = direction.length();
        if (distance == 0) return false;
        RayTraceResult result = rayTrace(world, origin, direction, distance);
        return result != null && result.getHitBlock() != null && result.getHitBlock().isSolid();
    }

    private static RayTraceResult rayTrace(World world, Vector origin, Vector direction, double distance) {
        return world.rayTraceBlocks(
                new Location(world, origin.getX(), origin.getY(), origin.getZ()),
                direction,
                distance,
                FluidCollisionMode.NEVER,
                true
        );
    }

    public static boolean isEntityVisible(Player player, LivingEntity entity) {
        List<Vector> vertices = getEntityVertices(entity);

        if (distanceOverride > 0 && player.getLocation().distanceSquared(entity.getLocation()) < distanceOverride*distanceOverride) {
            return true;
        }
        for (Vector vertex : vertices) {
            if (!collideSolid(player, vertex)) {
                return true;
            }
        }
        return false;
    }

    public static List<Vector> getEntityVertices(LivingEntity entity) {
        ArrayList<Vector> vertices = new ArrayList<>();

        BoundingBox boundingBox = entity.getBoundingBox();

//        // bottom face
//        vertices.add(new Vector(minX, minY, minZ))
//        double midX = boundingBox.getCenterX();
//        double midY = boundingBox.getCenterY();
//        double midZ = boundingBox.getCenterZ();;
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

        double maxX = boundingBox.getMaxX();
        double maxY = boundingBox.getMaxY();
        double maxZ = boundingBox.getMaxZ();

        double midX = boundingBox.getCenterX();
        double midY = boundingBox.getCenterY();
        double midZ = boundingBox.getCenterZ();

        double minX = boundingBox.getMinX();
        double minY = boundingBox.getMinY();
        double minZ = boundingBox.getMinZ();

        // mid spine (extras * 4 points)
        if (samplePointsPerCorner < 2) throw new ExceptionInInitializerError("samplePointsPerCorner must be at least 2");
        for (int i = 0; i < samplePointsPerCorner; i++) {
            double y = lerp(minY, maxY, ((double) i) / (samplePointsPerCorner-1));

            if (boundingBoxExtraValue > 0) {
                vertices.add(new Vector(minX - boundingBoxExtraValue, y, minZ - boundingBoxExtraValue));
                vertices.add(new Vector(minX - boundingBoxExtraValue, y, maxZ + boundingBoxExtraValue));

                vertices.add(new Vector(maxX + boundingBoxExtraValue, y, maxZ + boundingBoxExtraValue));
                vertices.add(new Vector(maxX + boundingBoxExtraValue, y, minZ - boundingBoxExtraValue));
            }

            vertices.add(new Vector(minX, y, minZ));
            vertices.add(new Vector(minX, y, maxZ));

            vertices.add(new Vector(midX, y, midZ));

            vertices.add(new Vector(maxX, y, maxZ));
            vertices.add(new Vector(maxX, y, minZ));
        }

        return vertices;
    }
}
