package RayTraceAntiEntityESP.bukkit.manager.engine;

import RayTraceAntiEntityESP.bukkit.Main;
import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.misc.Maths;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class RayTraceManager {

    private static BukkitTask task;

    private static final Map<Long, Boolean> blockCache = new ConcurrentHashMap<>();

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    public static boolean isVisible(World world, Vector eyePos, Vector lookDir, Vector endpoint) {
        if (!Config.isPerspectiveCheckingEnabled) return !hitsBlock(world, eyePos, endpoint);
        return !hitsBlock(world, eyePos, endpoint)
                || !hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir.clone().multiply(-1), Config.perspectiveCheckingDistance), endpoint)
                || !hitsBlock(world, getThirdPersonPos(world, eyePos, lookDir, Config.perspectiveCheckingDistance), endpoint);
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

    public static boolean isEntityInSight(Player viewer, Entity entity, Vector eyePos, Vector lookDir, Location viewerLoc, World world) {
        double range = Config.getSpigotTrackingRange(entity);

        double dx = viewerLoc.getX() - entity.getLocation().getX();
        double dz = viewerLoc.getZ() - entity.getLocation().getZ();
        double horizDistSq = dx * dx + dz * dz;

        double distSq = horizDistSq + Math.pow(viewerLoc.getY() - entity.getLocation().getY(), 2);
        double distance = Math.sqrt(distSq);

        if (!isAntiEntity(entity)
                || isEntityGlowing(viewer, entity)
                || horizDistSq > range * range
                || (Config.checkingDistanceOverride > 0 && distSq < Config.checkingDistanceOverride * Config.checkingDistanceOverride)
                || (hasBelowNameScore(viewer, entity) && distSq <= 10 * 10)) {
            if (Config.isDebugEnabled) VerticesDebugManager.removeDisplay(viewer.getUniqueId(), entity.getUniqueId());
            return true;
        }

        List<Vector> vertices = getEntityVertices(distance, entity, range);

        if (Config.isDebugEnabled) {
            List<Boolean> visibilities = new ArrayList<>(vertices.size());
            boolean visible = false;
            for (Vector vertex : vertices) {
                boolean v = isVisible(world, eyePos, lookDir, vertex);
                visibilities.add(v);
                if (v) visible = true;
            }
            VerticesDebugManager.applyDisplay(viewer, entity, vertices, visibilities);
            return visible;
        }

        for (Vector vertex : vertices) {
            if (isVisible(world, eyePos, lookDir, vertex)) return true;
        }
        return false;
    }

    private static boolean hasBelowNameScore(Player viewer, Entity entity) {
        String objective = PacketManager.belowNameObjective.get(viewer.getUniqueId());
        if (objective == null) return false;

        net.minecraft.world.scores.Scoreboard nmsScoreboard =
                net.minecraft.server.MinecraftServer.getServer().getScoreboard();
        net.minecraft.world.scores.Objective nmsObjective =
                nmsScoreboard.getObjective(objective);
        if (nmsObjective == null) return false;

        String entry = entity instanceof Player p ? p.getName() : entity.getUniqueId().toString();
        net.minecraft.world.scores.ScoreHolder holder =
                net.minecraft.world.scores.ScoreHolder.forNameOnly(entry);

        return nmsScoreboard.getPlayerScoreInfo(holder, nmsObjective) != null;
    }

    public static boolean isAntiEntity(Entity entity) {
        if (!Config.bypassTag.isEmpty() && entity.getScoreboardTags().contains(Config.bypassTag)) return false;
        boolean listed = Config.antiEntities.contains(entity.getType().name().toLowerCase());
        return Config.isBlacklist != listed;
    }

    public static List<Vector> getEntityVertices(double distance, Entity entity, double checkingRange) {

        if (Config.checkingVerticesLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");

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

        double ratio = checkingRange > 0 ? Math.min(distance / checkingRange, 1.0) : 0.0;

        int scaledSampleLayers = Math.max(2, (int) Math.round(Config.checkingVerticesLayers * (1.0 - ratio)));

        boolean includeCorners = ratio < 0.5;

        for (int i = 0; i < scaledSampleLayers; i++) {
            double y = Maths.lerp(minY, maxY, ((double) i) / (scaledSampleLayers - 1));
            vertices.add(new Vector(midX, y, midZ));

            if (includeCorners) {
                if (Config.checkingBoundingBoxExtraValue > 0) {
                    double eMinX = minX - Config.checkingBoundingBoxExtraValue;
                    double eMaxX = maxX + Config.checkingBoundingBoxExtraValue;
                    double eMinZ = minZ - Config.checkingBoundingBoxExtraValue;
                    double eMaxZ = maxZ + Config.checkingBoundingBoxExtraValue;

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

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer, int viewerEntityId, int targetEntityId) {
        boolean visibleClient = !VisibilityUtils.isHidden(viewerEntityId, targetEntityId);

        if (visibleServer && !visibleClient) {
            VisibilityUtils.setNotHidden(viewer, entity);
            if (Config.isDisplayNameEnabled) NametagCloneManager.removeDisplay(viewer.getUniqueId(), entity.getUniqueId());

        } else if (!visibleServer && visibleClient) {
            VisibilityUtils.setHidden(viewer, entity);
            if (Config.isDisplayNameEnabled) NametagCloneManager.applyDisplay(viewer, entity);

        } else if (!visibleServer) {
            if (Config.isDisplayNameEnabled) NametagCloneManager.applyDisplay(viewer, entity);
        }
    }

    public static void killTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            int viewerEntityId = ((CraftPlayer) viewer).getHandle().getId();
            ServerLevel nmsWorld = ((CraftWorld) viewer.getWorld()).getHandle();

            for (net.minecraft.world.entity.Entity nmsEntity : nmsWorld.getAllEntities()) {
                int targetId = nmsEntity.getId();
                if (targetId == viewerEntityId) continue;
                if (VisibilityUtils.isHidden(viewerEntityId, targetId)) {
                    Entity entity = nmsEntity.getBukkitEntity();
                    VisibilityUtils.setNotHidden(viewer, entity);
                }
            }
            VisibilityUtils.clearViewer(viewerEntityId);
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
                ServerLevel nmsWorld = ((CraftWorld) viewer.getWorld()).getHandle();
                final int viewerEntityId = ((CraftPlayer) viewer).getHandle().getId();
                AABB aabb = AABB.ofSize(
                        new Vec3(viewer.getX(), viewer.getY(), viewer.getZ()),
                        288, 288, 288
                );
                List<net.minecraft.world.entity.Entity> nearby = nmsWorld.getEntities(
                        ((CraftPlayer) viewer).getHandle(), aabb
                );
                if (nearby.isEmpty()) continue;

                int count = 0;
                Entity[] snapshot = new Entity[nearby.size()];
                int[] entityIds = new int[nearby.size()];
                for (net.minecraft.world.entity.Entity nmsEntity : nearby) {
                    snapshot[count] = nmsEntity.getBukkitEntity();
                    entityIds[count] = nmsEntity.getId();
                    count++;
                }

                final int entityCount = count;
                final Entity[] entitySnapshot = snapshot;
                final int[] entityIdSnapshot = entityIds;
                final Vector eyePos = viewer.getEyeLocation().toVector();
                final Vector lookDir = viewer.getLocation().getDirection();
                final Location viewerLoc = viewer.getLocation().clone();
                final World world = viewer.getWorld();
                final Player v = viewer;

                CompletableFuture.runAsync(() -> {
                    boolean[] results = new boolean[entityCount];
                    for (int i = 0; i < entityCount; i++) {
                        results[i] = isEntityInSight(v, entitySnapshot[i], eyePos, lookDir, viewerLoc, world);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (int i = 0; i < entityCount; i++) {
                            updateRayTraceChecking(v, entitySnapshot[i], results[i], viewerEntityId, entityIdSnapshot[i]);
                        }
                    });
                }, Main.executor);
            }
        }, 0L, Config.checkingPeriodTicks);
    }

}
