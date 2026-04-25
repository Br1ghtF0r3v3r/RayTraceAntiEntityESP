package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.misc.Maths;
import RayTraceAntiEntityESP.utils.DebugsUtils;
import RayTraceAntiEntityESP.utils.FakeNameDisplay;
import RayTraceAntiEntityESP.utils.VisibilityUtils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.config.Config.*;

public class RayTraceManager {

    private static BukkitTask task;

    public static boolean isVisible(Player viewer, Vector endpoint) {
        World world = viewer.getWorld();
        Vector eyePos = viewer.getEyeLocation().toVector();
        Vector lookDir = viewer.getLocation().getDirection();
        if (!isPerspectiveCheckingEnabled) return !hitsBlock(world, eyePos, endpoint);
        return !hitsBlock(world, eyePos, endpoint)
                || !hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir.clone().multiply(-1), perspectiveCheckingDistance), endpoint)
                || !hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir, perspectiveCheckingDistance), endpoint);
    }

    public static boolean hitsBlock(World world, Vector origin, Vector endpoint) {
        Vector direction = endpoint.clone().subtract(origin);
        double distance = direction.length();
        if (distance == 0) return false;

        Vector rayStart = origin.clone();
        double distanceLeft = distance;

        while (distanceLeft > 0) {
            RayTraceResult result = rayTrace(world, rayStart, direction, distanceLeft);

            if (result == null || result.getHitBlock() == null) return false;

            Material blockType = result.getHitBlock().getType();

            if (blockType.isOccluding()) return true;

            Vector pastTheBlock = result.getHitPosition().add(direction.clone().normalize().multiply(0.05));
            distanceLeft -= rayStart.distance(pastTheBlock);
            rayStart = pastTheBlock;
        }

        return false;
    }

    public static Vector getThirdPersonPos(World world, Vector eyePos, Vector direction, double maxDistance) {
        direction = direction.normalize();
        RayTraceResult result = rayTrace(world, eyePos, direction, maxDistance);
        return result != null && result.getHitBlock() != null
                ? result.getHitPosition().subtract(direction.multiply(0.1))
                : eyePos.clone().add(direction.multiply(maxDistance));
    }

    public static RayTraceResult rayTrace(World world, Vector origin, Vector direction, double distance) {
        return world.rayTraceBlocks(
                new Location(world, origin.getX(), origin.getY(), origin.getZ()),
                direction,
                distance,
                FluidCollisionMode.NEVER,
                true
        );
    }

    public static boolean isEntityGlowing(Player player, Entity entity) {
        Set<Integer> playerSet = PacketManager.glowingEntities.get(player.getUniqueId());
        if (playerSet == null) return entity.isGlowing();
        return entity.isGlowing() || playerSet.contains(entity.getEntityId());
    }

    public static boolean isEntityInSight(Player viewer, Entity entity) {
        double range = getSpigotTrackingRange(entity);
        double distSq = viewer.getLocation().distanceSquared(entity.getLocation());

        if (!isAntiEntity(entity)
                || isEntityGlowing(viewer, entity)
                || distSq > range * range
                || (checkingDistanceOverride > 0 && distSq < checkingDistanceOverride * checkingDistanceOverride)) {
            DebugsUtils.removeDisplay(viewer, entity);
            return true;
        }

        List<Vector> vertices = getEntityVertices(viewer, entity, range);
        boolean visible = false;

        if (isDebugEnabled) {
            Set<Integer> visibleVertices = new HashSet<>();
            int i = 0;
            for (Vector vertex : vertices) {
                if (isVisible(viewer, vertex)) {
                    visibleVertices.add(i);
                    visible = true;
                }
                i++;
            }
            DebugsUtils.applyDisplay(viewer, entity, vertices, visibleVertices);
        } else {
            for (Vector vertex : vertices) {
                if (isVisible(viewer, vertex)) {
                    visible = true;
                    break;
                }
            }
        }
        return visible;
    }

    public static boolean isAntiEntity(Entity entity) {
        if (!bypassTag.isEmpty() && entity.getScoreboardTags().contains(bypassTag)) return false;

        String typeName = entity.getType().name().toLowerCase();
        boolean whiteListed = antiEntities.contains(typeName);
        if (antiMode.equalsIgnoreCase("blacklist")) {
            return !whiteListed;
        } else if (antiMode.equalsIgnoreCase("whitelist")) {
            return whiteListed;
        }
        return whiteListed;
    }

    public static List<Vector> getEntityVertices(Player viewer, Entity entity, double checkingRange) {

        if (checkingSampleLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");

        ArrayList<Vector> vertices = new ArrayList<>();
        BoundingBox boundingBox = entity.getBoundingBox();
        double maxX = boundingBox.getMaxX();
        double maxY = boundingBox.getMaxY();
        double maxZ = boundingBox.getMaxZ();
        double midX = boundingBox.getCenterX();
        double midZ = boundingBox.getCenterZ();
        double minX = boundingBox.getMinX();
        double minY = boundingBox.getMinY();
        double minZ = boundingBox.getMinZ();

        double distance = viewer.getLocation().distance(entity.getLocation());
        double ratio = checkingRange > 0 ? Math.min(distance / checkingRange, 1.0) : 0.0;

        int scaledSampleLayers = Math.max(2, (int) Math.round(checkingSampleLayers * (1.0 - ratio)));

        boolean includeCorners = ratio < 0.5;

        for (int i = 0; i < scaledSampleLayers; i++) {
            double y = Maths.lerp(minY, maxY, ((double) i) / (scaledSampleLayers - 1));
            vertices.add(new Vector(midX, y, midZ));

            if (includeCorners) {
                if (checkingBoundingBoxExtraValue > 0) {
                    double eMinX = minX - checkingBoundingBoxExtraValue;
                    double eMaxX = maxX + checkingBoundingBoxExtraValue;
                    double eMinZ = minZ - checkingBoundingBoxExtraValue;
                    double eMaxZ = maxZ + checkingBoundingBoxExtraValue;

                    vertices.add(new Vector(eMinX, y, eMinZ));
                    vertices.add(new Vector(eMinX, y, eMaxZ));
                    vertices.add(new Vector(eMaxX, y, eMaxZ));
                    vertices.add(new Vector(eMaxX, y, eMinZ));

                    vertices.add(new Vector(midX, y, eMinZ));
                    vertices.add(new Vector(midX, y, eMaxZ));
                    vertices.add(new Vector(eMinX, y, midZ));
                    vertices.add(new Vector(eMaxX, y, midZ));
                }
                vertices.add(new Vector(minX, y, minZ));
                vertices.add(new Vector(minX, y, maxZ));
                vertices.add(new Vector(maxX, y, maxZ));
                vertices.add(new Vector(maxX, y, minZ));

                vertices.add(new Vector(midX, y, minZ));
                vertices.add(new Vector(midX, y, maxZ));
                vertices.add(new Vector(minX, y, midZ));
                vertices.add(new Vector(maxX, y, midZ));
            }
        }
        return vertices;
    }

    //  | client state | server state | action         |
    //  |--------------|--------------|----------------|
    //  | visible      | visible      | nothing        |
    //  | visible      | not visible  | destroy packet |
    //  | not visible  | visible      | spawn packet   |
    //  | not visible  | not visible  | nothing        |

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer) {
        boolean visibleClient = viewer.canSee(entity);
        if (visibleServer && !visibleClient) {
            VisibilityUtils.setNotHidden(viewer, entity);
        } else if (!visibleServer && visibleClient) {
            VisibilityUtils.setHidden(viewer, entity);
        }
    }

    public static void killTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Entity entity : viewer.getWorld().getEntities()) {
                if (!viewer.canSee(entity)) VisibilityUtils.setNotHidden(viewer, entity);
            }
            FakeNameDisplay.removeDisplay(viewer);
        }
        PacketManager.bypassPacketSet.clear();
    }

    public static void startTask() {
        killTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                for (Entity entity : viewer.getWorld().getEntities()) {
                    if (entity != viewer) {
                        RayTraceManager.updateRayTraceChecking(viewer, entity, RayTraceManager.isEntityInSight(viewer, entity));
                    }
                }
            }
        }, 0L, checkingPeriodTicks);
    }

}
