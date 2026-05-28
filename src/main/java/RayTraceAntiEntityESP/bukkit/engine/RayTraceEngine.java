package RayTraceAntiEntityESP.bukkit.engine;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.listener.packet.AddEntityPacketListener;
import RayTraceAntiEntityESP.bukkit.misc.Maths;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class RayTraceEngine {

    private static BukkitTask task;

    private static final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap blockCacheA =
            new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(2048);
    private static final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap blockCacheB =
            new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(2048);
    private static volatile it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap blockCache = blockCacheA;
    private static boolean blockCacheUseA = true;
    private static final byte CACHE_MISS = 0, CACHE_TRUE = 1, CACHE_FALSE = 2;

    private static final net.minecraft.world.scores.Scoreboard NMS_SCOREBOARD =
            net.minecraft.server.MinecraftServer.getServer().getScoreboard();

    private static final double VIEWER_POS_EPSILON_SQ = 0.01 * 0.01;
    private static final double ENTITY_POS_EPSILON_SQ = 0.01 * 0.01;
    private static final float ROT_EPSILON = 0.5f;
    private static final int AABB_REFRESH_TICKS = 20;
    private static final double AABB_REFRESH_MOVE_SQ = 64.0;

    private static final double PERSPECTIVE_CHECK_MAX_DIST_SQ = 16.0 * 16.0;

    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ViewerCache> viewerCaches =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

    private static class WorldEntitySnapshot {
        net.minecraft.world.entity.Entity[] entities = new net.minecraft.world.entity.Entity[128];
        int entityCount = 0;
        double centerX, centerY, centerZ;
        int age;
    }

    private static final java.util.IdentityHashMap<ServerLevel, WorldEntitySnapshot> worldEntityCache =
            new java.util.IdentityHashMap<>();

    private static class ViewerCache {
        boolean initialized = false;
        double prevX, prevY, prevZ;
        float prevYaw, prevPitch;
        float accumYaw = 0f, accumPitch = 0f;

        final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap entityIndexMap =
                new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        double[] cachedX = new double[64];
        double[] cachedY = new double[64];
        double[] cachedZ = new double[64];
        boolean[] cachedVisible = new boolean[64];
        int cachedCount = 0;

        org.bukkit.entity.Entity[] snapshotBuffer = new org.bukkit.entity.Entity[64];
        int[] entityIdBuffer = new int[64];
        boolean[] clientVisBuffer = new boolean[64];
        boolean[] asyncResults = new boolean[64];

        java.util.ArrayList<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>>
                outboxBuffer = new java.util.ArrayList<>(32);
        java.util.ArrayList<org.bukkit.entity.Entity> pendingShowsBuffer = new java.util.ArrayList<>(16);
    }

    private static final ThreadLocal<ArrayList<Vector>> vertexBuffer =
            ThreadLocal.withInitial(() -> new ArrayList<>(32));

    private static final it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap<EntityType> antiEntityTypeCache =
            new it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap<>();

    private static final it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap excludeTagCache =
            new it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap();
    private static final it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap prevExcludeTagCache =
            new it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap();
    private static int excludeTagCacheTick = 0;

    public static void clearViewerCache(int entityId) {
        viewerCaches.remove(entityId);
    }

    public static void clearAntiEntityCache() {
        antiEntityTypeCache.clear();
        excludeTagCache.clear();
        prevExcludeTagCache.clear();
    }

    public static void clearAllCaches() {
        viewerCaches.clear();
        worldEntityCache.clear();
        blockCacheA.clear();
        blockCacheB.clear();
        blockCache = blockCacheA;
        antiEntityTypeCache.clear();
        excludeTagCache.clear();
        prevExcludeTagCache.clear();
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private static boolean isOccluding(ServerLevel level, int x, int y, int z) {
        long key = blockKey(x, y, z);
        byte cached = blockCache.get(key);
        if (cached != CACHE_MISS) return cached == CACHE_TRUE;
        boolean result;
        try {
            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
            if (chunk == null) {
                result = false;
            } else {
                int si = level.getSectionIndex(y);
                net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                result = (si >= 0 && si < sections.length) &&
                        sections[si].getBlockState(x & 15, y & 15, z & 15).canOcclude();
            }
        } catch (Throwable t) {
            result = false;
        }
        blockCache.put(key, result ? CACHE_TRUE : CACHE_FALSE);
        return result;
    }

    public static boolean hitsBlock(ServerLevel level, int minY, int maxY, Vector origin, Vector endpoint) {
        return hitsBlock(level, minY, maxY,
                origin.getX(), origin.getY(), origin.getZ(),
                endpoint.getX(), endpoint.getY(), endpoint.getZ());
    }

    public static boolean hitsBlock(ServerLevel level, int minY, int maxY,
                                    double ox2, double oy2, double oz2,
                                    double ex2, double ey2, double ez2) {
        double dirX = ex2 - ox2, dirY = ey2 - oy2, dirZ = ez2 - oz2;
        double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (distance == 0) return false;
        double inv = 1.0 / distance;
        dirX *= inv;
        dirY *= inv;
        dirZ *= inv;
        double ox = ox2, oy = oy2, oz = oz2;
        int posX = (int) Math.floor(ox), posY = (int) Math.floor(oy), posZ = (int) Math.floor(oz);
        int stepX = dirX > 0 ? 1 : -1, stepY = dirY > 0 ? 1 : -1, stepZ = dirZ > 0 ? 1 : -1;
        double tDX = dirX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirX);
        double tDY = dirY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirY);
        double tDZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirZ);
        double tMX = dirX == 0 ? Double.MAX_VALUE : Math.abs((stepX > 0 ? (posX + 1 - ox) : (ox - posX)) / dirX);
        double tMY = dirY == 0 ? Double.MAX_VALUE : Math.abs((stepY > 0 ? (posY + 1 - oy) : (oy - posY)) / dirY);
        double tMZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs((stepZ > 0 ? (posZ + 1 - oz) : (oz - posZ)) / dirZ);
        int endX = (int) Math.floor(ex2), endY = (int) Math.floor(ey2), endZ = (int) Math.floor(ez2);
        int maxSteps = (int) (distance + 2) * 3;
        for (int s = 0; s < maxSteps; s++) {
            if (posX == endX && posY == endY && posZ == endZ) return false;
            if (posY >= minY && posY <= maxY && isOccluding(level, posX, posY, posZ)) return true;
            if (tMX < tMY && tMX < tMZ) {
                posX += stepX;
                tMX += tDX;
            } else if (tMY < tMZ) {
                posY += stepY;
                tMY += tDY;
            } else {
                posZ += stepZ;
                tMZ += tDZ;
            }
        }
        return false;
    }

    public static boolean isEntityGlowing(Player player, Entity entity) {
        if (entity.isGlowing()) return true;
        Set<Integer> s = PacketManager.glowingEntities.get(player.getUniqueId());
        return s != null && s.contains(entity.getEntityId());
    }

    public static boolean isEntityInSight(Player viewer, Entity entity, double ex, double ey, double ez,
                                          Vector eyePos, Vector lookDir, Vector negLookDir,
                                          double vx, double vy, double vz,
                                          ServerLevel level, int minY, int maxY) {
        double range = Config.getSpigotTrackingRange(entity);
        double dx = vx - ex, dy = vy - ey, dz = vz - ez;
        double horizDistSq = dx * dx + dz * dz, distSq = horizDistSq + dy * dy;
        double distance = Math.sqrt(distSq);

        if (!isAntiEntity(entity) || isEntityGlowing(viewer, entity)
                || horizDistSq > range * range
                || (Config.checkingDistanceOverride > 0 && distSq < Config.checkingDistanceOverride * Config.checkingDistanceOverride)
                || (hasBelowNameScore(viewer, entity) && distSq <= 100)) {
            if (Config.isDebugEnabled) DebugVertexRenderer.removeDisplay(viewer.getUniqueId(), entity.getUniqueId());
            return true;
        }

        Vector thirdBack = null, thirdFront = null;
        if (Config.isPerspectiveCheckingEnabled && distSq <= PERSPECTIVE_CHECK_MAX_DIST_SQ) {
            thirdBack = getThirdPersonPosNms(level, minY, maxY, eyePos, negLookDir, Config.perspectiveCheckingDistance);
            thirdFront = getThirdPersonPosNms(level, minY, maxY, eyePos, lookDir, Config.perspectiveCheckingDistance);
        }

        if (Config.isDebugEnabled) {
            List<Vector> vertices = getEntityVertices(distance, entity, range);
            List<Boolean> vis = new ArrayList<>(vertices.size());
            boolean visible = false;
            for (Vector v : vertices) {
                boolean r = isVisibleNms(level, minY, maxY, eyePos, thirdBack, thirdFront, v);
                vis.add(r);
                if (r) visible = true;
            }
            DebugVertexRenderer.applyDisplay(viewer, entity, vertices, vis);
            return visible;
        }

        net.minecraft.world.phys.AABB box = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle().getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        if (isVisibleNmsRaw(level, minY, maxY, eyePos, thirdBack, thirdFront, centerX, centerY, centerZ)) return true;

        List<Vector> vertices = getEntityVertices(distance, entity, range);
        for (Vector v : vertices) if (isVisibleNms(level, minY, maxY, eyePos, thirdBack, thirdFront, v)) return true;
        return false;
    }

    private static boolean isVisibleNms(ServerLevel level, int minY, int maxY,
                                        Vector eyePos, Vector thirdBack, Vector thirdFront, Vector endpoint) {
        if (!hitsBlock(level, minY, maxY, eyePos, endpoint)) return true;
        if (thirdBack == null) return false;
        if (!hitsBlock(level, minY, maxY, thirdBack, endpoint)) return true;
        return !hitsBlock(level, minY, maxY, thirdFront, endpoint);
    }

    private static boolean isVisibleNmsRaw(ServerLevel level, int minY, int maxY,
                                           Vector eyePos, Vector thirdBack, Vector thirdFront,
                                           double ex, double ey, double ez) {
        if (!hitsBlock(level, minY, maxY, eyePos.getX(), eyePos.getY(), eyePos.getZ(), ex, ey, ez)) return true;
        if (thirdBack == null) return false;
        if (!hitsBlock(level, minY, maxY, thirdBack.getX(), thirdBack.getY(), thirdBack.getZ(), ex, ey, ez))
            return true;
        return !hitsBlock(level, minY, maxY, thirdFront.getX(), thirdFront.getY(), thirdFront.getZ(), ex, ey, ez);
    }

    private static Vector getThirdPersonPosNms(ServerLevel level, int minY, int maxY,
                                               Vector eyePos, Vector direction, double maxDistance) {
        double dlen = direction.length();
        if (dlen == 0) return eyePos.clone();
        double inv = 1.0 / dlen;
        double dirX = direction.getX() * inv, dirY = direction.getY() * inv, dirZ = direction.getZ() * inv;
        double ox = eyePos.getX(), oy = eyePos.getY(), oz = eyePos.getZ();
        int posX = (int) Math.floor(ox), posY = (int) Math.floor(oy), posZ = (int) Math.floor(oz);
        int stepX = dirX > 0 ? 1 : -1, stepY = dirY > 0 ? 1 : -1, stepZ = dirZ > 0 ? 1 : -1;
        double tDX = dirX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirX);
        double tDY = dirY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirY);
        double tDZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirZ);
        double tMX = dirX == 0 ? Double.MAX_VALUE : Math.abs((stepX > 0 ? (posX + 1 - ox) : (ox - posX)) / dirX);
        double tMY = dirY == 0 ? Double.MAX_VALUE : Math.abs((stepY > 0 ? (posY + 1 - oy) : (oy - posY)) / dirY);
        double tMZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs((stepZ > 0 ? (posZ + 1 - oz) : (oz - posZ)) / dirZ);
        int maxSteps = (int) (maxDistance + 2) * 3;
        double curT = 0;
        for (int s = 0; s < maxSteps; s++) {
            if (curT >= maxDistance) break;
            if (posY >= minY && posY <= maxY && isOccluding(level, posX, posY, posZ)) {
                double t = Math.max(0, curT - 0.1);
                return new Vector(ox + dirX * t, oy + dirY * t, oz + dirZ * t);
            }
            if (tMX < tMY && tMX < tMZ) {
                curT = tMX;
                posX += stepX;
                tMX += tDX;
            } else if (tMY < tMZ) {
                curT = tMY;
                posY += stepY;
                tMY += tDY;
            } else {
                curT = tMZ;
                posZ += stepZ;
                tMZ += tDZ;
            }
        }
        return new Vector(ox + dirX * maxDistance, oy + dirY * maxDistance, oz + dirZ * maxDistance);
    }

    private static boolean hasBelowNameScore(Player viewer, Entity entity) {
        String objective = PacketManager.belowNameObjective.get(viewer.getUniqueId());
        if (objective == null) return false;
        net.minecraft.world.scores.Objective nmsObjective = NMS_SCOREBOARD.getObjective(objective);
        if (nmsObjective == null) return false;
        String entry = entity instanceof Player p ? p.getName() : entity.getUniqueId().toString();
        return NMS_SCOREBOARD.getPlayerScoreInfo(
                net.minecraft.world.scores.ScoreHolder.forNameOnly(entry), nmsObjective) != null;
    }

    private static boolean hasExcludeTag(Entity entity) {
        if (Config.excludeEntityTag.isEmpty()) return false;
        int eid = entity.getEntityId();

        if (excludeTagCache.containsKey(eid)) return excludeTagCache.get(eid);

        boolean result = entity.getScoreboardTags().contains(Config.excludeEntityTag);
        excludeTagCache.put(eid, result);

        if (prevExcludeTagCache.containsKey(eid)) {
            boolean prev = prevExcludeTagCache.get(eid);
            if (prev != result) {
                for (ViewerCache cache : viewerCaches.values()) {
                    cache.entityIndexMap.remove(eid);
                }
                if (result) {
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        int vid = ((CraftPlayer) viewer).getHandle().getId();
                        if (VisibilityUtils.isHidden(vid, eid)) {
                            VisibilityUtils.setNotHidden(viewer, entity);
                        }
                    }
                }
            }
        }

        prevExcludeTagCache.put(eid, result);
        return result;
    }

    public static boolean isAntiEntity(Entity entity) {
        if (hasExcludeTag(entity)) return false;
        EntityType type = entity.getType();
        if (antiEntityTypeCache.containsKey(type)) return antiEntityTypeCache.getBoolean(type);
        boolean listed = Config.antiEntities.contains(type.name().toLowerCase());
        boolean result = Config.isBlacklist != listed;
        antiEntityTypeCache.put(type, result);
        return result;
    }

    public static List<Vector> getEntityVertices(double distance, Entity entity, double checkingRange) {
        if (Config.checkingVerticesLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");
        ArrayList<Vector> vertices = vertexBuffer.get();
        vertices.clear();
        net.minecraft.world.phys.AABB box = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle().getBoundingBox();
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        double midX = (minX + maxX) * 0.5, midZ = (minZ + maxZ) * 0.5;
        double ratio = checkingRange > 0 ? Math.min(distance / checkingRange, 1.0) : 0.0;
        int scaledSampleLayers;
        if (ratio > 0.8) scaledSampleLayers = 2;
        else if (ratio > 0.6) scaledSampleLayers = 3;
        else if (ratio > 0.4) scaledSampleLayers = Math.max(2, Config.checkingVerticesLayers - 2);
        else scaledSampleLayers = Math.max(2, (int) Math.round(Config.checkingVerticesLayers * (1.0 - ratio * 0.5)));
        boolean includeCorners = ratio < 0.25;
        for (int i = 0; i < scaledSampleLayers; i++) {
            double y = Maths.lerp(minY, maxY, ((double) i) / (scaledSampleLayers - 1));
            vertices.add(new Vector(midX, y, midZ));
            if (includeCorners) {
                if (Config.checkingBoundingBoxExtraValue > 0) {
                    double eMinX = minX - Config.checkingBoundingBoxExtraValue, eMaxX = maxX + Config.checkingBoundingBoxExtraValue;
                    double eMinZ = minZ - Config.checkingBoundingBoxExtraValue, eMaxZ = maxZ + Config.checkingBoundingBoxExtraValue;
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

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer, boolean visibleClient,
                                              java.util.List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> outbox) {
        if (visibleServer && !visibleClient) {
            VisibilityUtils.setNotHidden(viewer, entity);
            if (Config.isDisplayNameEnabled)
                NametagCloneRenderer.removeDisplay(viewer.getUniqueId(), entity.getUniqueId(), outbox);
        } else if (!visibleServer && visibleClient) {
            VisibilityUtils.setHidden(viewer, entity);
            if (Config.isDisplayNameEnabled) NametagCloneRenderer.applyDisplay(viewer, entity, outbox);
        } else if (!visibleServer) {
            if (Config.isDisplayNameEnabled) NametagCloneRenderer.refreshDisplay(viewer, entity, outbox);
        }
    }

    public static void killTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            int vid = ((CraftPlayer) viewer).getHandle().getId();
            ServerLevel nmsWorld = ((CraftWorld) viewer.getWorld()).getHandle();
            for (net.minecraft.world.entity.Entity nmsEntity : nmsWorld.getAllEntities()) {
                int tid = nmsEntity.getId();
                if (tid == vid) continue;
                if (VisibilityUtils.isHidden(vid, tid))
                    VisibilityUtils.setNotHidden(viewer, nmsEntity.getBukkitEntity());
            }
            VisibilityUtils.clearViewer(vid);
        }
        NametagCloneRenderer.removeAllDisplays();
        DebugVertexRenderer.removeAllDisplays();
        PacketManager.clearAllBypasses();
        AddEntityPacketListener.pendingHides.clear();
        viewerCaches.clear();
        worldEntityCache.clear();
    }

    public static void startTask() {
        killTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            AddEntityPacketListener.drainPendingHides();

            blockCacheUseA = !blockCacheUseA;
            it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap next = blockCacheUseA ? blockCacheA : blockCacheB;
            next.clear();
            blockCache = next;

            if (++excludeTagCacheTick > 2) {
                excludeTagCache.clear();
                excludeTagCacheTick = 0;
            }

            List<net.minecraft.server.level.ServerPlayer> serverPlayers =
                    net.minecraft.server.MinecraftServer.getServer().getPlayerList().getPlayers();
            if (serverPlayers.isEmpty()) return;

            int playerCount = serverPlayers.size();
            Player[] viewers = new Player[playerCount];
            Entity[][] snapshots = new Entity[playerCount][];
            int[][] entityIdSnapshots = new int[playerCount][];
            int[] entityCounts = new int[playerCount];
            Vector[] eyePositions = new Vector[playerCount];
            Vector[] lookDirs = new Vector[playerCount];
            Vector[] negLookDirs = new Vector[playerCount];
            double[] vxArr = new double[playerCount];
            double[] vyArr = new double[playerCount];
            double[] vzArr = new double[playerCount];
            boolean[] viewerMoved = new boolean[playerCount];
            boolean[][] clientVisible = new boolean[playerCount][];
            ViewerCache[] caches = new ViewerCache[playerCount];
            ServerLevel[] levels = new ServerLevel[playerCount];
            int[] worldMinY = new int[playerCount];
            int[] worldMaxY = new int[playerCount];

            int vi = 0;
            for (net.minecraft.server.level.ServerPlayer sp : serverPlayers) {
                if (PacketManager.isBypassed(sp.getUUID())) continue;
                Player viewer = sp.getBukkitEntity();
                ServerLevel nmsWorld = sp.level();
                int vid = sp.getId();
                double vx = sp.getX(), vy = sp.getY(), vz = sp.getZ();

                ViewerCache cache = viewerCaches.get(vid);
                if (cache == null) {
                    cache = new ViewerCache();
                    viewerCaches.put(vid, cache);
                }

                double r = Config.getMaxTrackingRange(), rangeSq = r * r;

                WorldEntitySnapshot worldSnap = worldEntityCache.get(nmsWorld);
                boolean refreshWorld;
                if (worldSnap == null) {
                    refreshWorld = true;
                } else {
                    double ddx = vx - worldSnap.centerX, ddy = vy - worldSnap.centerY, ddz = vz - worldSnap.centerZ;
                    refreshWorld = worldSnap.age >= AABB_REFRESH_TICKS ||
                            (ddx * ddx + ddy * ddy + ddz * ddz) > AABB_REFRESH_MOVE_SQ;
                }
                if (refreshWorld) {
                    if (worldSnap == null) {
                        worldSnap = new WorldEntitySnapshot();
                        worldEntityCache.put(nmsWorld, worldSnap);
                    }
                    final WorldEntitySnapshot snap = worldSnap;
                    snap.entityCount = 0;
                    nmsWorld.getEntities().get(AABB.ofSize(new Vec3(vx, vy, vz), r * 2, r * 2, r * 2), e -> {
                        if (e.getId() == vid) return;
                        Entity bukkit = e.getBukkitEntity();
                        if (!isAntiEntity(bukkit) || PacketManager.isBypassed(bukkit.getUniqueId())) return;
                        if (snap.entityCount >= snap.entities.length)
                            snap.entities = java.util.Arrays.copyOf(snap.entities, snap.entities.length + (snap.entities.length >> 1));
                        snap.entities[snap.entityCount++] = e;
                    });
                    snap.centerX = vx;
                    snap.centerY = vy;
                    snap.centerZ = vz;
                    snap.age = 0;
                } else {
                    worldSnap.age++;
                }

                int aabbCount = worldSnap.entityCount;
                if (aabbCount == 0) continue;

                if (cache.snapshotBuffer.length < aabbCount) {
                    int nl = aabbCount + 16;
                    cache.snapshotBuffer = new org.bukkit.entity.Entity[nl];
                    cache.entityIdBuffer = new int[nl];
                    cache.clientVisBuffer = new boolean[nl];
                    cache.asyncResults = new boolean[nl];
                }
                Entity[] snapshot = cache.snapshotBuffer;
                int[] entityIds = cache.entityIdBuffer;
                int count = 0;
                for (int ei = 0; ei < aabbCount; ei++) {
                    net.minecraft.world.entity.Entity nmsEntity = worldSnap.entities[ei];
                    double ex = nmsEntity.getX(), ey = nmsEntity.getY(), ez = nmsEntity.getZ();
                    double dxe = ex - vx, dye = ey - vy, dze = ez - vz;
                    if ((dxe * dxe + dye * dye + dze * dze) > rangeSq) continue;
                    snapshot[count] = nmsEntity.getBukkitEntity();
                    entityIds[count] = nmsEntity.getId();
                    count++;
                }
                if (count == 0) continue;

                float yaw = sp.getYRot(), pitch = sp.getXRot();
                double cosPitch = Math.cos(Math.toRadians(pitch));
                double ldx = -Math.sin(Math.toRadians(yaw)) * cosPitch;
                double ldy = -Math.sin(Math.toRadians(pitch));
                double ldz = Math.cos(Math.toRadians(yaw)) * cosPitch;

                boolean moved;
                if (!cache.initialized) {
                    moved = true;
                    cache.initialized = true;
                    cache.accumYaw = 0f;
                    cache.accumPitch = 0f;
                } else {
                    double ddx = vx - cache.prevX, ddy = vy - cache.prevY, ddz = vz - cache.prevZ;
                    cache.accumYaw += Math.abs(yaw - cache.prevYaw);
                    cache.accumPitch += Math.abs(pitch - cache.prevPitch);
                    moved = (ddx * ddx + ddy * ddy + ddz * ddz) > VIEWER_POS_EPSILON_SQ
                            || cache.accumYaw > ROT_EPSILON || cache.accumPitch > ROT_EPSILON;
                }
                if (moved) {
                    cache.accumYaw = 0f;
                    cache.accumPitch = 0f;
                }
                cache.prevX = vx;
                cache.prevY = vy;
                cache.prevZ = vz;
                cache.prevYaw = yaw;
                cache.prevPitch = pitch;

                boolean[] clientVis = cache.clientVisBuffer;
                it.unimi.dsi.fastutil.ints.IntSet hiddenSet = VisibilityUtils.getHiddenSet(vid);
                for (int ci = 0; ci < count; ci++)
                    clientVis[ci] = hiddenSet == null || !hiddenSet.contains(entityIds[ci]);

                viewers[vi] = viewer;
                snapshots[vi] = snapshot;
                entityIdSnapshots[vi] = entityIds;
                entityCounts[vi] = count;
                eyePositions[vi] = new Vector(sp.getX(), sp.getEyeY(), sp.getZ());
                lookDirs[vi] = new Vector(ldx, ldy, ldz);
                negLookDirs[vi] = new Vector(-ldx, -ldy, -ldz);
                vxArr[vi] = vx;
                vyArr[vi] = vy;
                vzArr[vi] = vz;
                viewerMoved[vi] = moved;
                clientVisible[vi] = clientVis;
                caches[vi] = cache;
                levels[vi] = nmsWorld;
                worldMinY[vi] = viewer.getWorld().getMinHeight();
                worldMaxY[vi] = viewer.getWorld().getMaxHeight();
                vi++;
            }
            if (vi == 0) return;

            for (int i = 0; i < vi; i++) {
                int count = entityCounts[i];
                boolean[] results = caches[i].asyncResults;
                ViewerCache cache = caches[i];
                boolean vMoved = viewerMoved[i];
                for (int j = 0; j < count; j++) {
                    Entity entity = snapshots[i][j];
                    int eid = entityIdSnapshots[i][j];
                    net.minecraft.world.entity.Entity nmsEnt = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle();
                    double ex = nmsEnt.getX(), ey = nmsEnt.getY(), ez = nmsEnt.getZ();
                    int idx = cache.entityIndexMap.getOrDefault(eid, -1);
                    if (idx >= 0 && !vMoved && !Config.isDebugEnabled) {
                        double dxe = ex - cache.cachedX[idx], dye = ey - cache.cachedY[idx], dze = ez - cache.cachedZ[idx];
                        if ((dxe * dxe + dye * dye + dze * dze) <= ENTITY_POS_EPSILON_SQ) {
                            results[j] = cache.cachedVisible[idx];
                            continue;
                        }
                    }
                    boolean visible = isEntityInSight(viewers[i], entity, ex, ey, ez,
                            eyePositions[i], lookDirs[i], negLookDirs[i],
                            vxArr[i], vyArr[i], vzArr[i], levels[i], worldMinY[i], worldMaxY[i]);
                    results[j] = visible;
                    if (idx < 0) {
                        idx = cache.cachedCount++;
                        if (idx >= cache.cachedX.length) {
                            int nl = (idx + 1) * 2;
                            cache.cachedX = Arrays.copyOf(cache.cachedX, nl);
                            cache.cachedY = Arrays.copyOf(cache.cachedY, nl);
                            cache.cachedZ = Arrays.copyOf(cache.cachedZ, nl);
                            cache.cachedVisible = Arrays.copyOf(cache.cachedVisible, nl);
                        }
                        cache.entityIndexMap.put(eid, idx);
                    }
                    cache.cachedX[idx] = ex;
                    cache.cachedY[idx] = ey;
                    cache.cachedZ[idx] = ez;
                    cache.cachedVisible[idx] = visible;
                }
            }

            for (int i = 0; i < vi; i++) {
                int count = entityCounts[i];
                Player viewer = viewers[i];
                ViewerCache vcache = caches[i];
                java.util.ArrayList<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> outbox = vcache.outboxBuffer;
                outbox.clear();
                java.util.ArrayList<Entity> pendingShows = vcache.pendingShowsBuffer;
                pendingShows.clear();
                for (int j = 0; j < count; j++) {
                    boolean visServer = vcache.asyncResults[j], visClient = clientVisible[i][j];
                    if (visServer && visClient) continue;
                    if (visServer) {
                        pendingShows.add(snapshots[i][j]);
                        continue;
                    }
                    updateRayTraceChecking(viewer, snapshots[i][j], false, visClient, outbox);
                }
                int vid = ((CraftPlayer) viewer).getHandle().getId();
                for (Entity e : pendingShows) {
                    if (VisibilityUtils.isHidden(vid, ((org.bukkit.craftbukkit.entity.CraftEntity) e).getHandle().getId()))
                        updateRayTraceChecking(viewer, e, true, false, outbox);
                }
                if (Config.isDisplayNameEnabled) NametagCloneRenderer.cleanupStaleClones(outbox, viewer);
                if (!outbox.isEmpty())
                    ((CraftPlayer) viewer).getHandle().connection
                            .send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(outbox));
            }
        }, 0L, Config.checkingPeriodTicks);
    }
}