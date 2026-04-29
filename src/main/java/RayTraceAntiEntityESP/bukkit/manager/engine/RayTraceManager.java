package RayTraceAntiEntityESP.bukkit.manager.engine;

import RayTraceAntiEntityESP.bukkit.misc.Maths;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.config.Config.*;

public class RayTraceManager {

    private static BukkitTask task;

    private static final ConcurrentHashMap<Long, Boolean> blockCache = new ConcurrentHashMap<>();

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    public static boolean isVisible(Player viewer, Vector endpoint) {
        World world = viewer.getWorld();
        Vector eyePos = viewer.getEyeLocation().toVector();
        Vector lookDir = viewer.getLocation().getDirection();
        if (!isPerspectiveCheckingEnabled) return !hitsBlock(world, eyePos, endpoint);
        return !hitsBlock(world, eyePos, endpoint)
                || !hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir.clone().multiply(-1), perspectiveCheckingDistance), endpoint)
                || !hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir, perspectiveCheckingDistance), endpoint);
    }

    private static int[] initBlockPos(Vector origin, Vector direction) {
        return new int[]{
                (int) Math.floor(origin.getX()),
                (int) Math.floor(origin.getY()),
                (int) Math.floor(origin.getZ()),
                direction.getX() > 0 ? 1 : -1,
                direction.getY() > 0 ? 1 : -1,
                direction.getZ() > 0 ? 1 : -1
        };
    }

    private static double[] initTValues(Vector origin, Vector direction, int[] pos) {
        double tDeltaX = direction.getX() == 0 ? Double.MAX_VALUE : Math.abs(1.0 / direction.getX());
        double tDeltaY = direction.getY() == 0 ? Double.MAX_VALUE : Math.abs(1.0 / direction.getY());
        double tDeltaZ = direction.getZ() == 0 ? Double.MAX_VALUE : Math.abs(1.0 / direction.getZ());
        double tMaxX = direction.getX() == 0 ? Double.MAX_VALUE : Math.abs((pos[3] > 0 ? (pos[0] + 1 - origin.getX()) : (origin.getX() - pos[0])) / direction.getX());
        double tMaxY = direction.getY() == 0 ? Double.MAX_VALUE : Math.abs((pos[4] > 0 ? (pos[1] + 1 - origin.getY()) : (origin.getY() - pos[1])) / direction.getY());
        double tMaxZ = direction.getZ() == 0 ? Double.MAX_VALUE : Math.abs((pos[5] > 0 ? (pos[2] + 1 - origin.getZ()) : (origin.getZ() - pos[2])) / direction.getZ());
        return new double[]{tDeltaX, tDeltaY, tDeltaZ, tMaxX, tMaxY, tMaxZ};
    }

    private static double stepDDA(int[] pos, double[] t) {
        if (t[3] < t[4] && t[3] < t[5]) {
            double cur = t[3];
            pos[0] += pos[3];
            t[3] += t[0];
            return cur;
        } else if (t[4] < t[5]) {
            double cur = t[4];
            pos[1] += pos[4];
            t[4] += t[1];
            return cur;
        } else {
            double cur = t[5];
            pos[2] += pos[5];
            t[5] += t[2];
            return cur;
        }
    }

    private static boolean isOccluding(World world, int x, int y, int z) {
        long key = blockKey(x, y, z);
        Boolean cached = blockCache.get(key);
        if (cached != null) return cached;
        boolean result = world.getBlockAt(x, y, z).getType().isOccluding();
        blockCache.put(key, result);
        return result;
    }

    public static boolean hitsBlock(World world, Vector origin, Vector endpoint) {
        Vector direction = endpoint.clone().subtract(origin);
        double distance = direction.length();
        if (distance == 0) return false;
        direction = direction.normalize();

        int[] pos = initBlockPos(origin, direction);
        double[] t = initTValues(origin, direction, pos);
        int endX = (int) Math.floor(endpoint.getX());
        int endY = (int) Math.floor(endpoint.getY());
        int endZ = (int) Math.floor(endpoint.getZ());
        int maxSteps = (int) (distance + 2) * 3;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int step = 0; step < maxSteps; step++) {
            if (pos[0] == endX && pos[1] == endY && pos[2] == endZ) return false;
            if (pos[1] >= minY && pos[1] <= maxY && isOccluding(world, pos[0], pos[1], pos[2])) return true;
            stepDDA(pos, t);
        }
        return false;
    }

    public static Vector getThirdPersonPos(World world, Vector eyePos, Vector direction, double maxDistance) {
        direction = direction.clone().normalize();

        int[] pos = initBlockPos(eyePos, direction);
        double[] t = initTValues(eyePos, direction, pos);
        int maxSteps = (int) (maxDistance + 2) * 3;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        double curT = 0;

        for (int step = 0; step < maxSteps; step++) {
            if (curT >= maxDistance) break;
            if (pos[1] >= minY && pos[1] <= maxY && isOccluding(world, pos[0], pos[1], pos[2])) {
                return eyePos.clone().add(direction.clone().multiply(Math.max(0, curT - 0.1)));
            }
            curT = stepDDA(pos, t);
        }
        return eyePos.clone().add(direction.multiply(maxDistance));
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
            if (isDebugEnabled) VerticesDebugManager.removeDisplay(viewer, entity);
            return true;
        }

        List<Vector> vertices = getEntityVertices(viewer, entity, range);

        boolean visible = false;

        if (isDebugEnabled) {
            List<Boolean> visibilities = new ArrayList<>(vertices.size());
            for (Vector vertex : vertices) {
                boolean v = isVisible(viewer, vertex);
                if (v) visible = true;
                visibilities.add(v);
            }
            VerticesDebugManager.applyDisplay(viewer, entity, vertices, visibilities);
        } else {
            for (Vector vertex : vertices) {
                boolean v = isVisible(viewer, vertex);
                if (v) visible = true;
                break;
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

        if (checkingVerticesLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");

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

        int scaledSampleLayers = Math.max(2, (int) Math.round(checkingVerticesLayers * (1.0 - ratio)));

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
    //  | not visible  | not visible  | update         |

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer) {
        boolean visibleClient = viewer.canSee(entity);

        if (visibleServer && !visibleClient) {

            VisibilityUtils.setNotHidden(viewer, entity);

            if (isDisplayNameEnabled) {
                NametagCloneManager.removeDisplay(viewer, entity);
            }

        } else if (!visibleServer && visibleClient) {

            VisibilityUtils.setHidden(viewer, entity);

            if (isDisplayNameEnabled) {
                NametagCloneManager.applyDisplay(viewer, entity);
            }

        } else if (!visibleServer) {

             if (isDisplayNameEnabled) {
                 NametagCloneManager.applyDisplay(viewer, entity);
             }

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
        }
        NametagCloneManager.removeAllDisplays();
        VerticesDebugManager.removeAllDisplays();
        PacketManager.bypassPacketSet.clear();
    }

    public static void startTask() {
        killTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            blockCache.clear();
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
