package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.misc.Maths;
import RayTraceAntiEntityESP.utils.RayTraceDebugsUtils;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
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
    public static long currentCheckingIntervalTicks;
    public static boolean currentIsDebugEnabled;

    public static boolean notCollideSolid(Player player, Vector endpoint) {
        World world = player.getWorld();
        Vector eyePos = player.getEyeLocation().toVector();
        Vector lookDir = player.getLocation().getDirection();
        if (!isPerspectiveCheckingEnabled) return hitsBlock(world, eyePos, endpoint);
        return hitsBlock(world, eyePos, endpoint)
                || hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir.clone().multiply(-1), perspectiveCheckingDistance), endpoint)
                || hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir, perspectiveCheckingDistance), endpoint);
    }

    public static Vector getThirdPersonPos(World world, Vector eyePos, Vector direction, double maxDistance) {
        direction = direction.normalize();
        RayTraceResult result = rayTrace(world, eyePos, direction, maxDistance);
        return result != null && result.getHitBlock() != null
                ? result.getHitPosition().subtract(direction.multiply(0.1))
                : eyePos.clone().add(direction.multiply(maxDistance));
    }

    public static boolean hitsBlock(World world, Vector origin, Vector endpoint) {
        Vector direction = endpoint.clone().subtract(origin);
        double distance = direction.length();
        if (distance == 0) return true;
        RayTraceResult result = rayTrace(world, origin, direction, distance);
        return result == null || result.getHitBlock() == null || !result.getHitBlock().getType().isOccluding();
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

    public static boolean isEntityVisible(Player player, LivingEntity entity) {
        double range = getSpigotTrackingRange(entity);

        if (!isAntiEntity(entity)) {
            if (isDebugEnabled) RayTraceDebugsUtils.despawnVertexDebugDisplays(entity);
            return true;
        }
        if (player.getLocation().distanceSquared(entity.getLocation()) > range * range) {
            if (isDebugEnabled) RayTraceDebugsUtils.despawnVertexDebugDisplays(entity);
            return true;
        }
        if (checkingDistanceOverride > 0 && player.getLocation().distanceSquared(entity.getLocation()) < checkingDistanceOverride * checkingDistanceOverride) {
            if (isDebugEnabled) RayTraceDebugsUtils.despawnVertexDebugDisplays(entity);
            return true;
        }

        List<Vector> vertices = getEntityVertices(player, entity, range);
        boolean visible = false;

        if (isDebugEnabled) {
            Set<Integer> visibleVertices = new HashSet<>();
            for (int i = 0; i < vertices.size(); i++) {
                if (notCollideSolid(player, vertices.get(i))) {
                    visibleVertices.add(i);
                    visible = true;
                }
            }
            RayTraceDebugsUtils.spawnVertexDebugDisplays(player, entity, vertices, visibleVertices);
        }
        else {
            RayTraceDebugsUtils.stopDebug();
            for (Vector vertex : vertices) {
                if (notCollideSolid(player, vertex)) {
                    visible = true;
                    break;
                }
            }
        }
        return visible;
    }

    public static boolean isAntiEntity(LivingEntity entity) {
        String typeName = entity.getType().name().toLowerCase();
        boolean whiteListed = antiEntities.contains(typeName);
        if (antiMode.equalsIgnoreCase("blacklist")) {
            return !whiteListed;
        } else if (antiMode.equalsIgnoreCase("whitelist")) {
            return whiteListed;
        }
        return whiteListed;
    }

    public static List<Vector> getEntityVertices(Player player, LivingEntity entity, double checkingRange) {

        if (samplePointsPerCorner < 2) throw new ExceptionInInitializerError("samplePoints must be at least 2");

        ArrayList<Vector> vertices = new ArrayList<>();
        BoundingBox boundingBox = entity.getBoundingBox();
        double maxX = boundingBox.getMaxX();
        double maxY = boundingBox.getMaxY();
        double maxZ = boundingBox.getMaxZ();
        double midX = boundingBox.getCenterX();
        double midY = boundingBox.getCenterY();
        double midZ = boundingBox.getCenterZ();
        double minX = boundingBox.getMinX();
        double minY = boundingBox.getMinY();
        double minZ = boundingBox.getMinZ();

        double distance = player.getLocation().distance(entity.getLocation());
        double ratio = checkingRange > 0 ? Math.min(distance / checkingRange, 1.0) : 0.0;

        int scaledSamplePoints = Math.max(2, (int) Math.round(samplePointsPerCorner * (1.0 - ratio)));

        boolean includeCorners = ratio < 0.5;

        for (int i = 0; i < scaledSamplePoints; i++) {
            double y = Maths.lerp(minY, maxY, ((double) i) / (scaledSamplePoints - 1));
            vertices.add(new Vector(midX, y, midZ));

            if (includeCorners) {
                if (boundingBoxExtraValue > 0) {
                    double eMinX = minX - boundingBoxExtraValue;
                    double eMaxX = maxX + boundingBoxExtraValue;
                    double eMinZ = minZ - boundingBoxExtraValue;
                    double eMaxZ = maxZ + boundingBoxExtraValue;

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

    public static void updateRayTraceChecking(Player player, LivingEntity entity, boolean visibleServer) {
        boolean visibleClient = player.canSee(entity);
        if (visibleServer && !visibleClient) {
            VisibilityManager.setNotHidden(player, entity);
        } else if (!visibleServer && visibleClient) {
            VisibilityManager.setHidden(player, entity);
        }
    }

    public static void startRayTraceChecking() {
        if (task != null) task.cancel();
        currentCheckingIntervalTicks = checkingPeriodTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentIsDebugEnabled && !isDebugEnabled) {
                RayTraceDebugsUtils.stopDebug();
            }
            currentIsDebugEnabled = isDebugEnabled;

            if (!isCheckingEnabled) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (LivingEntity entity : player.getWorld().getLivingEntities()) {
                        if (!player.canSee(entity)) VisibilityManager.setNotHidden(player, entity);
                    }
                    FakeNameDisplayManager.removeAllNameplates(player);
                }
                PacketFilterManager.bypassSet.clear();
                return;
            }
            if (currentCheckingIntervalTicks != checkingPeriodTicks) {
                startRayTraceChecking();
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (LivingEntity entity : player.getWorld().getLivingEntities()) {
                    if (entity != player) {
                        RayTraceManager.updateRayTraceChecking(player, entity, RayTraceManager.isEntityVisible(player, entity));
                    }
                }
            }
        }, 0L, checkingPeriodTicks);
    }

}
