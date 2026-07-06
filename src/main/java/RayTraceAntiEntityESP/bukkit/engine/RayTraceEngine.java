package RayTraceAntiEntityESP.bukkit.engine;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.listener.packet.AddEntityPacketListener;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class RayTraceEngine {

    private static BukkitTask task;

    private static final Long2ByteOpenHashMap blockCache = new Long2ByteOpenHashMap(4096);
    private static final byte CACHE_MISS = 0, CACHE_TRUE = 1, CACHE_FALSE = 2;

    private static int blockCacheTtlTick = 0;
    private static final int BLOCK_CACHE_TTL_TICKS = 200;

    private static int staggerTick = 0;

    private static final double VIEWER_POS_EPSILON_SQ = 0.01 * 0.01;
    private static final double ENTITY_POS_EPSILON_SQ = 0.01 * 0.01;
    private static final float ROT_EPSILON = 0.5f;
    private static final int AABB_REFRESH_TICKS = 4;
    private static final double AABB_REFRESH_MOVE_SQ = 16.0;
    private static final double AABB_QUERY_MARGIN = 4.0;

    private static final Int2ObjectOpenHashMap<ViewerCache> viewerCaches = new Int2ObjectOpenHashMap<>();

    // ---- Per-tick reusable buffers (main thread only; avoids GC pressure) ----
    private static Player[] viewersBuf = new Player[0];
    private static net.minecraft.world.entity.Entity[][] snapshotsBuf = new net.minecraft.world.entity.Entity[0][];
    private static int[][] entityIdSnapshotsBuf = new int[0][];
    private static int[] entityCountsBuf = new int[0];
    private static boolean[] viewerMovedBuf = new boolean[0];
    private static boolean[][] clientVisibleBuf = new boolean[0][];
    private static ViewerCache[] cachesBuf = new ViewerCache[0];
    private static ServerLevel[] levelsBuf = new ServerLevel[0];
    private static int[] worldMinYBuf = new int[0];
    private static int[] worldMaxYBuf = new int[0];
    private static double[] vxBuf = new double[0];
    private static double[] vyBuf = new double[0];
    private static double[] vzBuf = new double[0];

    // ---- Vertex scratch buffer (main thread only) ----
    private static double[] vertexXBuf = new double[128];
    private static double[] vertexYBuf = new double[128];
    private static double[] vertexZBuf = new double[128];

    // ---- Third-person position scratch (main thread only) ----
    private static final double[] thirdPersonScratch = new double[3];

    private static class WorldEntitySnapshot {
        net.minecraft.world.entity.Entity[] entities = new net.minecraft.world.entity.Entity[128];
        int entityCount = 0;
        double centerX, centerY, centerZ;
        int age;
    }

    private static final IdentityHashMap<ServerLevel, WorldEntitySnapshot> worldEntityCache = new IdentityHashMap<>();

    private static class ViewerCache {
        boolean initialized = false;
        double prevX, prevY, prevZ;
        float prevYaw, prevPitch;
        float accumYaw = 0f, accumPitch = 0f;

        // Cached eye position (updated every tick)
        double eyeX, eyeY, eyeZ;

        // Cached third-person positions (valid only when perspectiveValid)
        boolean perspectiveValid = false;
        double thirdBackX, thirdBackY, thirdBackZ;
        double thirdFrontX, thirdFrontY, thirdFrontZ;

        final Int2IntOpenHashMap entityIndexMap = new Int2IntOpenHashMap();
        double[] cachedX = new double[64];
        double[] cachedY = new double[64];
        double[] cachedZ = new double[64];
        boolean[] cachedVisible = new boolean[64];
        int cachedCount = 0;

        net.minecraft.world.entity.Entity[] snapshotBuffer = new net.minecraft.world.entity.Entity[64];
        int[] entityIdBuffer = new int[64];
        boolean[] clientVisBuffer = new boolean[64];
        boolean[] asyncResults = new boolean[64];

        ArrayList<Packet<? super ClientGamePacketListener>> outboxBuffer = new ArrayList<>(32);
        ArrayList<net.minecraft.world.entity.Entity> pendingShowsBuffer = new ArrayList<>(16);
    }

    private static final Object2BooleanOpenHashMap<EntityType> antiEntityTypeCache = new Object2BooleanOpenHashMap<>();

    public static void clearViewerCache(int entityId) {
        viewerCaches.remove(entityId);
    }

    public static void clearAntiEntityCache() {
        antiEntityTypeCache.clear();
    }

    public static void invalidateBlockAt(int x, int y, int z) {
        blockCache.remove(blockKey(x, y, z));
    }

    public static void clearAllCaches() {
        viewerCaches.clear();
        worldEntityCache.clear();
        blockCache.clear();
        antiEntityTypeCache.clear();
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
            LevelChunk chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
            if (chunk == null) {
                result = false;
            } else {
                int si = level.getSectionIndex(y);
                LevelChunkSection[] s = chunk.getSections();
                result = (si >= 0 && si < s.length) && s[si].getBlockState(x & 15, y & 15, z & 15).isSolidRender();
            }
        } catch (Throwable t) {
            result = false;
        }
        blockCache.put(key, result ? CACHE_TRUE : CACHE_FALSE);
        return result;
    }

    public static boolean hitsBlock(ServerLevel level, int minY, int maxY,
                                    double ox, double oy, double oz,
                                    double ex2, double ey2, double ez2) {
        double dirX = ex2 - ox, dirY = ey2 - oy, dirZ = ez2 - oz;
        double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (distance == 0) return false;
        double inv = 1.0 / distance;
        dirX *= inv;
        dirY *= inv;
        dirZ *= inv;
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
            if (posY >= minY && posY <= maxY && isOccluding(level, posX, posY, posZ)) return true;
            if (posX == endX && posY == endY && posZ == endZ) return false;
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

    private static boolean isEntityInSight(
            Player viewer,
            net.minecraft.world.entity.Entity nmsEntity, Entity entity,
            double ex, double ey, double ez,
            double eyeX, double eyeY, double eyeZ,
            double thirdBackX, double thirdBackY, double thirdBackZ,
            double thirdFrontX, double thirdFrontY, double thirdFrontZ,
            boolean perspectiveEnabled,
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

        AABB box = nmsEntity.getBoundingBox();
        double minX = box.minX, bMinY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, bMaxY = box.maxY, maxZ = box.maxZ;
        double midX = (minX + maxX) * 0.5, midZ = (minZ + maxZ) * 0.5;
        double centerY = (bMinY + bMaxY) * 0.5;

        if (Config.isDebugEnabled) {
            int vCount = fillEntityVertices(distance, range, minX, bMinY, minZ, maxX, bMaxY, maxZ);
            List<org.bukkit.util.Vector> vertices = new ArrayList<>(vCount);
            List<Boolean> vis = new ArrayList<>(vCount);
            boolean visible = false;
            for (int i = 0; i < vCount; i++) {
                boolean r = isVisibleNms(level, minY, maxY,
                        eyeX, eyeY, eyeZ,
                        thirdBackX, thirdBackY, thirdBackZ,
                        thirdFrontX, thirdFrontY, thirdFrontZ,
                        perspectiveEnabled,
                        vertexXBuf[i], vertexYBuf[i], vertexZBuf[i]);
                vertices.add(new org.bukkit.util.Vector(vertexXBuf[i], vertexYBuf[i], vertexZBuf[i]));
                vis.add(r);
                if (r) visible = true;
            }
            DebugVertexRenderer.applyDisplay(viewer, entity, vertices, vis);
            return visible;
        }

        if (isVisibleNms(level, minY, maxY,
                eyeX, eyeY, eyeZ,
                thirdBackX, thirdBackY, thirdBackZ,
                thirdFrontX, thirdFrontY, thirdFrontZ,
                perspectiveEnabled,
                midX, centerY, midZ)) return true;

        int vCount = fillEntityVertices(distance, range, minX, bMinY, minZ, maxX, bMaxY, maxZ);
        for (int i = 0; i < vCount; i++) {
            if (isVisibleNms(level, minY, maxY,
                    eyeX, eyeY, eyeZ,
                    thirdBackX, thirdBackY, thirdBackZ,
                    thirdFrontX, thirdFrontY, thirdFrontZ,
                    perspectiveEnabled,
                    vertexXBuf[i], vertexYBuf[i], vertexZBuf[i])) return true;
        }
        return false;
    }

    private static boolean isVisibleNms(ServerLevel level, int minY, int maxY,
                                        double eyeX, double eyeY, double eyeZ,
                                        double thirdBackX, double thirdBackY, double thirdBackZ,
                                        double thirdFrontX, double thirdFrontY, double thirdFrontZ,
                                        boolean perspectiveEnabled,
                                        double endX, double endY, double endZ) {
        if (!hitsBlock(level, minY, maxY, eyeX, eyeY, eyeZ, endX, endY, endZ)) return true;
        if (!perspectiveEnabled) return false;
        if (!hitsBlock(level, minY, maxY, thirdBackX, thirdBackY, thirdBackZ, endX, endY, endZ)) return true;
        return !hitsBlock(level, minY, maxY, thirdFrontX, thirdFrontY, thirdFrontZ, endX, endY, endZ);
    }

    private static void computeThirdPersonPos(ServerLevel level, int minY, int maxY,
                                              double ox, double oy, double oz,
                                              double dirX, double dirY, double dirZ,
                                              double maxDistance) {
        double dlen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dlen == 0) {
            thirdPersonScratch[0] = ox;
            thirdPersonScratch[1] = oy;
            thirdPersonScratch[2] = oz;
            return;
        }
        double inv = 1.0 / dlen;
        dirX *= inv;
        dirY *= inv;
        dirZ *= inv;
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
                thirdPersonScratch[0] = ox + dirX * t;
                thirdPersonScratch[1] = oy + dirY * t;
                thirdPersonScratch[2] = oz + dirZ * t;
                return;
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
        thirdPersonScratch[0] = ox + dirX * maxDistance;
        thirdPersonScratch[1] = oy + dirY * maxDistance;
        thirdPersonScratch[2] = oz + dirZ * maxDistance;
    }

    private static boolean hasBelowNameScore(Player viewer, Entity entity) {
        String objective = PacketManager.belowNameObjective.get(viewer.getUniqueId());
        if (objective == null) return false;
        Map<String, Set<String>> perObjective = PacketManager.objectiveScores.get(viewer.getUniqueId());
        if (perObjective == null) return false;
        Set<String> entries = perObjective.get(objective);
        if (entries == null) return false;
        String entry = entity instanceof Player p ? p.getName() : entity.getUniqueId().toString();
        return entries.contains(entry);
    }

    private static boolean isExcluded(Entity entity) {
        return ExcludeBypassManager.isExcluded(entity.getUniqueId());
    }

    private static boolean isAntiEntityType(Entity entity) {
        EntityType type = entity.getType();
        if (antiEntityTypeCache.containsKey(type)) return antiEntityTypeCache.getBoolean(type);
        boolean listed = Config.antiEntities.contains(type.name().toLowerCase());
        boolean result = Config.isBlacklist != listed;
        antiEntityTypeCache.put(type, result);
        return result;
    }

    public static boolean isAntiEntity(Entity entity) {
        if (isExcluded(entity)) return false;
        EntityType type = entity.getType();
        if (antiEntityTypeCache.containsKey(type)) return antiEntityTypeCache.getBoolean(type);
        boolean listed = Config.antiEntities.contains(type.name().toLowerCase());
        boolean result = Config.isBlacklist != listed;
        antiEntityTypeCache.put(type, result);
        return result;
    }

    /**
     * Fills the static vertex buffers with sampling points along the entity's bounding box.
     * Returns the number of vertices written. Reuses the same arrays across calls (main thread only).
     */
    private static int fillEntityVertices(double distance, double checkingRange,
                                          double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        if (Config.checkingVerticesLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");
        double midX = (minX + maxX) * 0.5, midZ = (minZ + maxZ) * 0.5;
        double ratio = checkingRange > 0 ? Math.min(distance / checkingRange, 1.0) : 0.0;
        int scaledSampleLayers;
        if (ratio > 0.8) scaledSampleLayers = 2;
        else if (ratio > 0.6) scaledSampleLayers = 3;
        else if (ratio > 0.4) scaledSampleLayers = Math.max(2, Config.checkingVerticesLayers - 2);
        else scaledSampleLayers = Math.max(2, (int) Math.round(Config.checkingVerticesLayers * (1.0 - ratio * 0.5)));
        boolean includeCorners = ratio < 0.25;
        boolean hasExtra = Config.checkingBoundingBoxExtraValue > 0;
        double eMinX = minX - Config.checkingBoundingBoxExtraValue, eMaxX = maxX + Config.checkingBoundingBoxExtraValue;
        double eMinZ = minZ - Config.checkingBoundingBoxExtraValue, eMaxZ = maxZ + Config.checkingBoundingBoxExtraValue;

        int maxVerts = scaledSampleLayers * (includeCorners ? 17 : 1);
        if (vertexXBuf.length < maxVerts) {
            vertexXBuf = new double[maxVerts];
            vertexYBuf = new double[maxVerts];
            vertexZBuf = new double[maxVerts];
        }

        int count = 0;
        for (int i = 0; i < scaledSampleLayers; i++) {
            double y = Mth.lerp(((double) i) / (scaledSampleLayers - 1), minY, maxY);
            vertexXBuf[count] = midX;
            vertexYBuf[count] = y;
            vertexZBuf[count] = midZ;
            count++;
            if (includeCorners) {
                if (hasExtra) {
                    vertexXBuf[count] = eMinX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = eMinZ;
                    count++;
                    vertexXBuf[count] = eMinX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = eMaxZ;
                    count++;
                    vertexXBuf[count] = eMaxX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = eMaxZ;
                    count++;
                    vertexXBuf[count] = eMaxX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = eMinZ;
                    count++;
                    vertexXBuf[count] = midX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = eMinZ;
                    count++;
                    vertexXBuf[count] = midX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = eMaxZ;
                    count++;
                    vertexXBuf[count] = eMinX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = midZ;
                    count++;
                    vertexXBuf[count] = eMaxX;
                    vertexYBuf[count] = y;
                    vertexZBuf[count] = midZ;
                    count++;
                }
                vertexXBuf[count] = minX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = minZ;
                count++;
                vertexXBuf[count] = minX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = maxZ;
                count++;
                vertexXBuf[count] = maxX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = maxZ;
                count++;
                vertexXBuf[count] = maxX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = minZ;
                count++;
                vertexXBuf[count] = midX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = minZ;
                count++;
                vertexXBuf[count] = midX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = maxZ;
                count++;
                vertexXBuf[count] = minX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = midZ;
                count++;
                vertexXBuf[count] = maxX;
                vertexYBuf[count] = y;
                vertexZBuf[count] = midZ;
                count++;
            }
        }
        return count;
    }

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer, boolean visibleClient,
                                              List<Packet<? super ClientGamePacketListener>> outbox) {
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

            if (++blockCacheTtlTick > BLOCK_CACHE_TTL_TICKS) {
                blockCache.clear();
                blockCacheTtlTick = 0;
            }

            staggerTick++;

            List<ServerPlayer> serverPlayers = MinecraftServer.getServer().getPlayerList().getPlayers();
            if (serverPlayers.isEmpty()) return;

            int playerCount = serverPlayers.size();
            if (viewersBuf.length < playerCount) {
                int cap = playerCount + 8;
                viewersBuf = new Player[cap];
                snapshotsBuf = new net.minecraft.world.entity.Entity[cap][];
                entityIdSnapshotsBuf = new int[cap][];
                entityCountsBuf = new int[cap];
                viewerMovedBuf = new boolean[cap];
                clientVisibleBuf = new boolean[cap][];
                cachesBuf = new ViewerCache[cap];
                levelsBuf = new ServerLevel[cap];
                worldMinYBuf = new int[cap];
                worldMaxYBuf = new int[cap];
                vxBuf = new double[cap];
                vyBuf = new double[cap];
                vzBuf = new double[cap];
            }

            boolean perspectiveEnabled = Config.isPerspectiveCheckingEnabled;

            int vi = 0;
            for (ServerPlayer sp : serverPlayers) {
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
                    double queryDiameter = (r + AABB_QUERY_MARGIN) * 2;
                    nmsWorld.getEntities().get(AABB.ofSize(new Vec3(vx, vy, vz), queryDiameter, queryDiameter, queryDiameter), e -> {
                        if (e.getId() == vid) return;
                        Entity bukkit = e.getBukkitEntity();
                        if (!isAntiEntityType(bukkit)) return;
                        if (snap.entityCount >= snap.entities.length)
                            snap.entities = Arrays.copyOf(snap.entities, snap.entities.length + (snap.entities.length >> 1));
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
                    cache.snapshotBuffer = new net.minecraft.world.entity.Entity[nl];
                    cache.entityIdBuffer = new int[nl];
                    cache.clientVisBuffer = new boolean[nl];
                    cache.asyncResults = new boolean[nl];
                }
                net.minecraft.world.entity.Entity[] snapshot = cache.snapshotBuffer;
                int[] entityIds = cache.entityIdBuffer;
                int count = 0;
                for (int ei = 0; ei < aabbCount; ei++) {
                    net.minecraft.world.entity.Entity nmsEntity = worldSnap.entities[ei];
                    if (nmsEntity.getId() == vid) continue;
                    if (VisibilityUtils.isExternallyHidden(vid, nmsEntity.getId())) continue;
                    double ex = nmsEntity.getX(), ey = nmsEntity.getY(), ez = nmsEntity.getZ();
                    double dxe = ex - vx, dye = ey - vy, dze = ez - vz;
                    if ((dxe * dxe + dye * dye + dze * dze) > rangeSq) continue;
                    snapshot[count] = nmsEntity;
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

                cache.eyeX = sp.getX();
                cache.eyeY = sp.getEyeY();
                cache.eyeZ = sp.getZ();

                if (perspectiveEnabled) {
                    if (moved || !cache.perspectiveValid) {
                        int worldMinY = viewer.getWorld().getMinHeight();
                        int worldMaxY = viewer.getWorld().getMaxHeight();
                        computeThirdPersonPos(nmsWorld, worldMinY, worldMaxY,
                                cache.eyeX, cache.eyeY, cache.eyeZ,
                                -ldx, -ldy, -ldz,
                                Config.perspectiveCheckingDistance);
                        cache.thirdBackX = thirdPersonScratch[0];
                        cache.thirdBackY = thirdPersonScratch[1];
                        cache.thirdBackZ = thirdPersonScratch[2];
                        computeThirdPersonPos(nmsWorld, worldMinY, worldMaxY,
                                cache.eyeX, cache.eyeY, cache.eyeZ,
                                ldx, ldy, ldz,
                                Config.perspectiveCheckingDistance);
                        cache.thirdFrontX = thirdPersonScratch[0];
                        cache.thirdFrontY = thirdPersonScratch[1];
                        cache.thirdFrontZ = thirdPersonScratch[2];
                        cache.perspectiveValid = true;
                    }
                } else {
                    cache.perspectiveValid = false;
                }

                boolean[] clientVis = cache.clientVisBuffer;
                IntSet hiddenSet = VisibilityUtils.getHiddenSet(vid);
                for (int ci = 0; ci < count; ci++)
                    clientVis[ci] = hiddenSet == null || !hiddenSet.contains(entityIds[ci]);

                viewersBuf[vi] = viewer;
                snapshotsBuf[vi] = snapshot;
                entityIdSnapshotsBuf[vi] = entityIds;
                entityCountsBuf[vi] = count;
                viewerMovedBuf[vi] = moved;
                clientVisibleBuf[vi] = clientVis;
                cachesBuf[vi] = cache;
                levelsBuf[vi] = nmsWorld;
                worldMinYBuf[vi] = viewer.getWorld().getMinHeight();
                worldMaxYBuf[vi] = viewer.getWorld().getMaxHeight();
                vxBuf[vi] = vx;
                vyBuf[vi] = vy;
                vzBuf[vi] = vz;
                vi++;
            }
            if (vi == 0) return;

            int groups = Config.checkingStaggerGroups;
            int currentGroup = staggerTick % groups;

            for (int i = 0; i < vi; i++) {
                int count = entityCountsBuf[i];
                boolean[] results = cachesBuf[i].asyncResults;
                ViewerCache cache = cachesBuf[i];
                boolean vMoved = viewerMovedBuf[i];
                Player viewer = viewersBuf[i];
                double eyeX = cache.eyeX, eyeY = cache.eyeY, eyeZ = cache.eyeZ;
                double thirdBackX = cache.thirdBackX, thirdBackY = cache.thirdBackY, thirdBackZ = cache.thirdBackZ;
                double thirdFrontX = cache.thirdFrontX, thirdFrontY = cache.thirdFrontY, thirdFrontZ = cache.thirdFrontZ;
                boolean perspValid = cache.perspectiveValid && perspectiveEnabled;
                ServerLevel level = levelsBuf[i];
                int minY = worldMinYBuf[i], maxY = worldMaxYBuf[i];
                double vx = vxBuf[i], vy = vyBuf[i], vz = vzBuf[i];
                net.minecraft.world.entity.Entity[] snap = snapshotsBuf[i];
                int[] eids = entityIdSnapshotsBuf[i];

                for (int j = 0; j < count; j++) {
                    net.minecraft.world.entity.Entity nmsEnt = snap[j];
                    int eid = eids[j];
                    double ex = nmsEnt.getX(), ey = nmsEnt.getY(), ez = nmsEnt.getZ();
                    int idx = cache.entityIndexMap.getOrDefault(eid, -1);

                    boolean forceCheck = idx < 0 || vMoved || Config.isDebugEnabled;

                    boolean entityMoved = false;
                    if (idx >= 0 && !forceCheck) {
                        double dxe = ex - cache.cachedX[idx], dye = ey - cache.cachedY[idx], dze = ez - cache.cachedZ[idx];
                        entityMoved = (dxe * dxe + dye * dye + dze * dze) > ENTITY_POS_EPSILON_SQ;
                    }

                    if (forceCheck || entityMoved || (eid % groups) == currentGroup) {
                        Entity entity = nmsEnt.getBukkitEntity();
                        boolean visible = isEntityInSight(
                                viewer, nmsEnt, entity, ex, ey, ez,
                                eyeX, eyeY, eyeZ,
                                thirdBackX, thirdBackY, thirdBackZ,
                                thirdFrontX, thirdFrontY, thirdFrontZ,
                                perspValid,
                                vx, vy, vz,
                                level, minY, maxY);
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
                    } else {
                        results[j] = cache.cachedVisible[idx];
                    }
                }
            }

            for (int i = 0; i < vi; i++) {
                int count = entityCountsBuf[i];
                Player viewer = viewersBuf[i];
                ViewerCache vcache = cachesBuf[i];
                ArrayList<Packet<? super ClientGamePacketListener>> outbox = vcache.outboxBuffer;
                outbox.clear();
                ArrayList<net.minecraft.world.entity.Entity> pendingShows = vcache.pendingShowsBuffer;
                pendingShows.clear();
                net.minecraft.world.entity.Entity[] snap = snapshotsBuf[i];
                for (int j = 0; j < count; j++) {
                    boolean visServer = vcache.asyncResults[j], visClient = clientVisibleBuf[i][j];
                    if (visServer && visClient) continue;
                    if (visServer) {
                        pendingShows.add(snap[j]);
                        continue;
                    }
                    updateRayTraceChecking(viewer, snap[j].getBukkitEntity(), false, visClient, outbox);
                }
                int vid = ((CraftPlayer) viewer).getHandle().getId();
                for (net.minecraft.world.entity.Entity e : pendingShows) {
                    if (VisibilityUtils.isHidden(vid, e.getId()))
                        updateRayTraceChecking(viewer, e.getBukkitEntity(), true, false, outbox);
                }
                if (Config.isDisplayNameEnabled) NametagCloneRenderer.cleanupStaleClones(outbox, viewer);
                if (!outbox.isEmpty())
                    ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundBundlePacket(outbox));
            }
        }, 0L, Config.checkingPeriodTicks);
    }
}