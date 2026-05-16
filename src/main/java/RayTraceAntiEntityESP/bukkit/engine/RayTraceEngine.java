package RayTraceAntiEntityESP.bukkit.engine;

import RayTraceAntiEntityESP.bukkit.Main;
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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class RayTraceEngine {

    private static BukkitTask task;

    private static final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap blockCacheA =
            new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap(512);
    private static final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap blockCacheB =
            new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap(512);
    private static volatile it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap blockCache = blockCacheA;
    private static boolean blockCacheUseA = true;

    private static final double VIEWER_POS_EPSILON_SQ = 0.01 * 0.01;
    private static final double ENTITY_POS_EPSILON_SQ = 0.01 * 0.01;
    private static final float ROT_EPSILON = 0.5f;

    private static final int AABB_REFRESH_TICKS = 20;
    private static final double AABB_REFRESH_MOVE_SQ = 64.0;

    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ViewerCache> viewerCaches =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

    private static class WorldEntitySnapshot {

        net.minecraft.world.entity.Entity[] entities = new net.minecraft.world.entity.Entity[128];
        int entityCount = 0;
        double centerX, centerY, centerZ;
        int age;
    }

    private static final java.util.IdentityHashMap<net.minecraft.server.level.ServerLevel, WorldEntitySnapshot> worldEntityCache =
            new java.util.IdentityHashMap<>();

    private static class ViewerCache {
        double prevX = Double.NaN, prevY = Double.NaN, prevZ = Double.NaN;
        float prevYaw = Float.NaN, prevPitch = Float.NaN;

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

    public static void clearViewerCache(int entityId) {
        viewerCaches.remove(entityId);
    }

    public static void clearAllCaches() {
        viewerCaches.clear();
        worldEntityCache.clear();
        blockCacheA.clear();
        blockCacheB.clear();
        blockCache = blockCacheA;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private static boolean isOccluding(ServerLevel level, int x, int y, int z) {
        long key = blockKey(x, y, z);
        if (blockCache.containsKey(key)) return blockCache.get(key);
        boolean result;
        try {
            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
            if (chunk == null) {
                result = false;
            } else {
                int sectionIndex = level.getSectionIndex(y);
                net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                if (sectionIndex < 0 || sectionIndex >= sections.length) {
                    result = false;
                } else {
                    net.minecraft.world.level.block.state.BlockState state =
                            sections[sectionIndex].getBlockState(x & 15, y & 15, z & 15);
                    result = state.canOcclude();
                }
            }
        } catch (Throwable t) {
            result = false;
        }
        blockCache.put(key, result);
        return result;
    }

    public static boolean hitsBlock(ServerLevel level, int minY, int maxY, Vector origin, Vector endpoint) {
        double dirX = endpoint.getX() - origin.getX();
        double dirY = endpoint.getY() - origin.getY();
        double dirZ = endpoint.getZ() - origin.getZ();
        double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (distance == 0) return false;
        double inv = 1.0 / distance;
        dirX *= inv;
        dirY *= inv;
        dirZ *= inv;

        double ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int posX = (int) Math.floor(ox);
        int posY = (int) Math.floor(oy);
        int posZ = (int) Math.floor(oz);
        int stepX = dirX > 0 ? 1 : -1;
        int stepY = dirY > 0 ? 1 : -1;
        int stepZ = dirZ > 0 ? 1 : -1;

        double tDeltaX = dirX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirX);
        double tDeltaY = dirY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirY);
        double tDeltaZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirZ);
        double tMaxX = dirX == 0 ? Double.MAX_VALUE : Math.abs((stepX > 0 ? (posX + 1 - ox) : (ox - posX)) / dirX);
        double tMaxY = dirY == 0 ? Double.MAX_VALUE : Math.abs((stepY > 0 ? (posY + 1 - oy) : (oy - posY)) / dirY);
        double tMaxZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs((stepZ > 0 ? (posZ + 1 - oz) : (oz - posZ)) / dirZ);

        int endX = (int) Math.floor(endpoint.getX());
        int endY = (int) Math.floor(endpoint.getY());
        int endZ = (int) Math.floor(endpoint.getZ());
        int maxSteps = (int) (distance + 2) * 3;

        for (int step = 0; step < maxSteps; step++) {
            if (posX == endX && posY == endY && posZ == endZ) return false;
            if (posY >= minY && posY <= maxY && isOccluding(level, posX, posY, posZ)) return true;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                posX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                posY += stepY;
                tMaxY += tDeltaY;
            } else {
                posZ += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return false;
    }

    public static boolean isEntityGlowing(Player player, Entity entity) {
        Set<Integer> playerSet = PacketManager.glowingEntities.get(player.getUniqueId());
        if (playerSet == null) return entity.isGlowing();
        return entity.isGlowing() || playerSet.contains(entity.getEntityId());
    }

    public static boolean isEntityInSight(Player viewer, Entity entity,
                                          Vector eyePos, Vector lookDir, Vector negLookDir,
                                          double viewerX, double viewerY, double viewerZ,
                                          ServerLevel level, int minY, int maxY) {
        double range = Config.getSpigotTrackingRange(entity);

        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();

        double dx = viewerX - ex;
        double dy = viewerY - ey;
        double dz = viewerZ - ez;
        double horizDistSq = dx * dx + dz * dz;
        double distSq = horizDistSq + dy * dy;
        double distance = Math.sqrt(distSq);

        if (!isAntiEntity(entity)
                || isEntityGlowing(viewer, entity)
                || horizDistSq > range * range
                || (Config.checkingDistanceOverride > 0 && distSq < Config.checkingDistanceOverride * Config.checkingDistanceOverride)
                || (hasBelowNameScore(viewer, entity) && distSq <= 10 * 10)) {
            if (Config.isDebugEnabled) DebugVertexRenderer.removeDisplay(viewer.getUniqueId(), entity.getUniqueId());
            return true;
        }

        List<Vector> vertices = getEntityVertices(distance, entity, range);

        if (Config.isDebugEnabled) {
            List<Vector> verticesCopy = new ArrayList<>(vertices);
            List<Boolean> visibilities = new ArrayList<>(verticesCopy.size());
            boolean visible = false;
            for (Vector vertex : verticesCopy) {
                boolean v = isVisibleNms(level, minY, maxY, eyePos, lookDir, negLookDir, vertex);
                visibilities.add(v);
                if (v) visible = true;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!Config.isDebugEnabled) return;
                DebugVertexRenderer.applyDisplay(viewer, entity, verticesCopy, visibilities);
            });
            return visible;
        }

        for (Vector vertex : vertices) {
            if (isVisibleNms(level, minY, maxY, eyePos, lookDir, negLookDir, vertex)) return true;
        }
        return false;
    }

    private static boolean isVisibleNms(ServerLevel level, int minY, int maxY,
                                        Vector eyePos, Vector lookDir, Vector negLookDir, Vector endpoint) {
        if (!Config.isPerspectiveCheckingEnabled) return !hitsBlock(level, minY, maxY, eyePos, endpoint);
        return !hitsBlock(level, minY, maxY, eyePos, endpoint)
                || !hitsBlock(level, minY, maxY, getThirdPersonPosNms(level, minY, maxY, eyePos, negLookDir, Config.perspectiveCheckingDistance), endpoint)
                || !hitsBlock(level, minY, maxY, getThirdPersonPosNms(level, minY, maxY, eyePos, lookDir, Config.perspectiveCheckingDistance), endpoint);
    }

    private static Vector getThirdPersonPosNms(ServerLevel level, int minY, int maxY,
                                               Vector eyePos, Vector direction, double maxDistance) {
        double dlen = direction.length();
        if (dlen == 0) return eyePos.clone();
        double inv = 1.0 / dlen;
        double dirX = direction.getX() * inv;
        double dirY = direction.getY() * inv;
        double dirZ = direction.getZ() * inv;

        double ox = eyePos.getX(), oy = eyePos.getY(), oz = eyePos.getZ();
        int posX = (int) Math.floor(ox);
        int posY = (int) Math.floor(oy);
        int posZ = (int) Math.floor(oz);
        int stepX = dirX > 0 ? 1 : -1;
        int stepY = dirY > 0 ? 1 : -1;
        int stepZ = dirZ > 0 ? 1 : -1;

        double tDeltaX = dirX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirX);
        double tDeltaY = dirY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirY);
        double tDeltaZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirZ);
        double tMaxX = dirX == 0 ? Double.MAX_VALUE : Math.abs((stepX > 0 ? (posX + 1 - ox) : (ox - posX)) / dirX);
        double tMaxY = dirY == 0 ? Double.MAX_VALUE : Math.abs((stepY > 0 ? (posY + 1 - oy) : (oy - posY)) / dirY);
        double tMaxZ = dirZ == 0 ? Double.MAX_VALUE : Math.abs((stepZ > 0 ? (posZ + 1 - oz) : (oz - posZ)) / dirZ);

        int maxSteps = (int) (maxDistance + 2) * 3;
        double curT = 0;

        for (int step = 0; step < maxSteps; step++) {
            if (curT >= maxDistance) break;
            if (posY >= minY && posY <= maxY && isOccluding(level, posX, posY, posZ)) {
                double t = Math.max(0, curT - 0.1);
                return new Vector(ox + dirX * t, oy + dirY * t, oz + dirZ * t);
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                curT = tMaxX;
                posX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                curT = tMaxY;
                posY += stepY;
                tMaxY += tDeltaY;
            } else {
                curT = tMaxZ;
                posZ += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return new Vector(ox + dirX * maxDistance, oy + dirY * maxDistance, oz + dirZ * maxDistance);
    }

    private static boolean hasBelowNameScore(Player viewer, Entity entity) {
        String objective = PacketManager.belowNameObjective.get(viewer.getUniqueId());
        if (objective == null) return false;

        net.minecraft.world.scores.Scoreboard nmsScoreboard =
                net.minecraft.server.MinecraftServer.getServer().getScoreboard();
        net.minecraft.world.scores.Objective nmsObjective = nmsScoreboard.getObjective(objective);
        if (nmsObjective == null) return false;

        String entry = entity instanceof Player p ? p.getName() : entity.getUniqueId().toString();
        net.minecraft.world.scores.ScoreHolder holder =
                net.minecraft.world.scores.ScoreHolder.forNameOnly(entry);
        return nmsScoreboard.getPlayerScoreInfo(holder, nmsObjective) != null;
    }

    public static boolean isAntiEntity(Entity entity) {
        if (!Config.excludeEntityTag.isEmpty() && entity.getScoreboardTags().contains(Config.excludeEntityTag))
            return false;
        boolean listed = Config.antiEntities.contains(entity.getType().name().toLowerCase());
        return Config.isBlacklist != listed;
    }

    public static List<Vector> getEntityVertices(double distance, Entity entity, double checkingRange) {
        if (Config.checkingVerticesLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");

        ArrayList<Vector> vertices = vertexBuffer.get();
        vertices.clear();

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

        NametagCloneRenderer.removeAllDisplays();
        DebugVertexRenderer.removeAllDisplays();
        PacketManager.clearAllBypasses();
        viewerCaches.clear();
        worldEntityCache.clear();
    }

    public static void startTask() {
        plugin.getLogger().info("RayTraceEngine: startTask() called (debug=" + Config.isDebugEnabled + ")");
        killTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            AddEntityPacketListener.drainPendingHides();

            blockCacheUseA = !blockCacheUseA;
            it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap nextCache = blockCacheUseA ? blockCacheA : blockCacheB;
            nextCache.clear();
            blockCache = nextCache;

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
            double[] viewerX = new double[playerCount];
            double[] viewerY = new double[playerCount];
            double[] viewerZ = new double[playerCount];
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
                int viewerEntityId = sp.getId();

                double vx = sp.getX();
                double vy = sp.getY();
                double vz = sp.getZ();

                ViewerCache cache = viewerCaches.get(viewerEntityId);
                if (cache == null) {
                    cache = new ViewerCache();
                    viewerCaches.put(viewerEntityId, cache);
                }

                double r = Config.getMaxTrackingRange();
                double rangeSq = r * r;

                WorldEntitySnapshot worldSnap = worldEntityCache.get(nmsWorld);
                net.minecraft.world.entity.Entity[] aabbEntities;
                boolean refreshWorld;
                if (worldSnap == null) {
                    refreshWorld = true;
                } else {
                    double ddx = vx - worldSnap.centerX, ddy = vy - worldSnap.centerY, ddz = vz - worldSnap.centerZ;
                    refreshWorld = worldSnap.age >= AABB_REFRESH_TICKS
                            || (ddx * ddx + ddy * ddy + ddz * ddz) > AABB_REFRESH_MOVE_SQ;
                }
                if (refreshWorld) {
                    if (worldSnap == null) {
                        worldSnap = new WorldEntitySnapshot();
                        worldEntityCache.put(nmsWorld, worldSnap);
                    }

                    final WorldEntitySnapshot snap = worldSnap;
                    snap.entityCount = 0;
                    AABB aabb = AABB.ofSize(new Vec3(vx, vy, vz), r * 2, r * 2, r * 2);
                    nmsWorld.getEntities().get(aabb, e -> {
                        if (snap.entityCount >= snap.entities.length) {
                            snap.entities = java.util.Arrays.copyOf(snap.entities, snap.entities.length + (snap.entities.length >> 1));
                        }
                        snap.entities[snap.entityCount++] = e;
                    });
                    snap.centerX = vx;
                    snap.centerY = vy;
                    snap.centerZ = vz;
                    snap.age = 0;
                    aabbEntities = snap.entities;
                } else {
                    aabbEntities = worldSnap.entities;
                    worldSnap.age++;
                }

                final int aabbCount = worldSnap.entityCount;
                if (aabbCount == 0) continue;

                int count = 0;
                if (cache.snapshotBuffer.length < aabbCount) {
                    int newLen = aabbCount + 16;
                    cache.snapshotBuffer = new org.bukkit.entity.Entity[newLen];
                    cache.entityIdBuffer = new int[newLen];
                    cache.clientVisBuffer = new boolean[newLen];
                    cache.asyncResults = new boolean[newLen];
                }
                Entity[] snapshot = cache.snapshotBuffer;
                int[] entityIds = cache.entityIdBuffer;
                for (int ei = 0; ei < aabbCount; ei++) {
                    net.minecraft.world.entity.Entity nmsEntity = aabbEntities[ei];
                    double ex = nmsEntity.getX(), ey = nmsEntity.getY(), ez = nmsEntity.getZ();
                    double dxe = ex - vx, dye = ey - vy, dze = ez - vz;
                    if ((dxe * dxe + dye * dye + dze * dze) > rangeSq) continue;
                    snapshot[count] = nmsEntity.getBukkitEntity();
                    entityIds[count] = nmsEntity.getId();
                    count++;
                }
                if (count == 0) continue;

                float yaw = sp.getYRot();
                float pitch = sp.getXRot();
                double cosPitch = Math.cos(Math.toRadians(pitch));
                double ldx = -Math.sin(Math.toRadians(yaw)) * cosPitch;
                double ldy = -Math.sin(Math.toRadians(pitch));
                double ldz = Math.cos(Math.toRadians(yaw)) * cosPitch;
                Vector lookDir = new Vector(ldx, ldy, ldz);
                Vector negLookDir = new Vector(-ldx, -ldy, -ldz);

                boolean moved;
                if (Double.isNaN(cache.prevX)) {
                    moved = true;
                    cache.accumYaw = 0f;
                    cache.accumPitch = 0f;
                } else {
                    double ddx = vx - cache.prevX, ddy = vy - cache.prevY, ddz = vz - cache.prevZ;
                    boolean posMoved = (ddx * ddx + ddy * ddy + ddz * ddz) > VIEWER_POS_EPSILON_SQ;

                    cache.accumYaw += Math.abs(yaw - cache.prevYaw);
                    cache.accumPitch += Math.abs(pitch - cache.prevPitch);

                    moved = posMoved
                            || cache.accumYaw > ROT_EPSILON
                            || cache.accumPitch > ROT_EPSILON;
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
                it.unimi.dsi.fastutil.ints.IntSet hiddenSet = VisibilityUtils.getHiddenSet(viewerEntityId);
                for (int ci = 0; ci < count; ci++) {
                    clientVis[ci] = hiddenSet == null || !hiddenSet.contains(entityIds[ci]);
                }
                clientVisible[vi] = clientVis;
                viewers[vi] = viewer;
                snapshots[vi] = snapshot;
                entityIdSnapshots[vi] = entityIds;
                entityCounts[vi] = count;
                eyePositions[vi] = new Vector(sp.getX(), sp.getEyeY(), sp.getZ());
                lookDirs[vi] = lookDir;
                negLookDirs[vi] = negLookDir;
                viewerX[vi] = vx;
                viewerY[vi] = vy;
                viewerZ[vi] = vz;
                viewerMoved[vi] = moved;
                caches[vi] = cache;
                levels[vi] = nmsWorld;
                worldMinY[vi] = viewer.getWorld().getMinHeight();
                worldMaxY[vi] = viewer.getWorld().getMaxHeight();
                vi++;
            }

            if (vi == 0) return;
            final int activeViewers = vi;

            Main.executor.execute(() -> {

                for (int i = 0; i < activeViewers; i++) {
                    int count = entityCounts[i];
                    boolean[] results = caches[i].asyncResults;
                    ViewerCache cache = caches[i];
                    boolean vMoved = viewerMoved[i];

                    for (int j = 0; j < count; j++) {
                        Entity entity = snapshots[i][j];
                        int eid = entityIdSnapshots[i][j];
                        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();

                        if (!vMoved) {
                            int idx = cache.entityIndexMap.getOrDefault(eid, -1);
                            if (idx >= 0) {
                                double dxe = ex - cache.cachedX[idx];
                                double dye = ey - cache.cachedY[idx];
                                double dze = ez - cache.cachedZ[idx];
                                if ((dxe * dxe + dye * dye + dze * dze) <= ENTITY_POS_EPSILON_SQ) {
                                    results[j] = cache.cachedVisible[idx];
                                    continue;
                                }
                            }
                        }

                        int idx = cache.entityIndexMap.getOrDefault(eid, -1);
                        boolean entityStationary = !Config.isDebugEnabled && idx >= 0 && (
                                (ex - cache.cachedX[idx]) * (ex - cache.cachedX[idx]) +
                                        (ey - cache.cachedY[idx]) * (ey - cache.cachedY[idx]) +
                                        (ez - cache.cachedZ[idx]) * (ez - cache.cachedZ[idx])
                        ) <= ENTITY_POS_EPSILON_SQ;
                        boolean visible;
                        if (!vMoved && entityStationary) {
                            visible = cache.cachedVisible[idx];
                        } else {
                            visible = isEntityInSight(
                                    viewers[i], entity,
                                    eyePositions[i], lookDirs[i], negLookDirs[i],
                                    viewerX[i], viewerY[i], viewerZ[i],
                                    levels[i], worldMinY[i], worldMaxY[i]
                            );
                        }
                        results[j] = visible;
                        if (idx < 0) {
                            idx = cache.cachedCount++;
                            if (idx >= cache.cachedX.length) {
                                int newLen = idx * 2;
                                cache.cachedX = Arrays.copyOf(cache.cachedX, newLen);
                                cache.cachedY = Arrays.copyOf(cache.cachedY, newLen);
                                cache.cachedZ = Arrays.copyOf(cache.cachedZ, newLen);
                                cache.cachedVisible = Arrays.copyOf(cache.cachedVisible, newLen);
                            }
                            cache.entityIndexMap.put(eid, idx);
                        }
                        cache.cachedX[idx] = ex;
                        cache.cachedY[idx] = ey;
                        cache.cachedZ[idx] = ez;
                        cache.cachedVisible[idx] = visible;
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < activeViewers; i++) {
                        int count = entityCounts[i];
                        Player viewer = viewers[i];
                        ViewerCache vcache = caches[i];
                        java.util.ArrayList<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> outbox = vcache.outboxBuffer;
                        outbox.clear();
                        java.util.ArrayList<Entity> pendingShows = vcache.pendingShowsBuffer;
                        pendingShows.clear();
                        for (int j = 0; j < count; j++) {
                            boolean visServer = caches[i].asyncResults[j];
                            boolean visClient = clientVisible[i][j];
                            if (visServer && visClient) continue;
                            if (visServer) {
                                pendingShows.add(snapshots[i][j]);
                                continue;
                            }
                            updateRayTraceChecking(viewer, snapshots[i][j], false, visClient, outbox);
                        }
                        for (Entity e : pendingShows) {
                            boolean stillHidden = VisibilityUtils.isHidden(
                                    ((CraftPlayer) viewer).getHandle().getId(),
                                    ((org.bukkit.craftbukkit.entity.CraftEntity) e).getHandle().getId()
                            );
                            if (stillHidden) {
                                updateRayTraceChecking(viewer, e, true, false, outbox);
                            }
                        }
                        if (!outbox.isEmpty()) {
                            ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle().connection
                                    .send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(outbox));
                        }
                    }
                });
            });

        }, 0L, Config.checkingPeriodTicks);
    }
}

