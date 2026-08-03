package RayTraceAntiEntityESP.paper.engine;

import RayTraceAntiEntityESP.paper.config.Config;
import RayTraceAntiEntityESP.paper.config.ExcludeBypassManager;
import RayTraceAntiEntityESP.paper.listener.PacketManager;
import RayTraceAntiEntityESP.paper.listener.packet.AddEntityPacketListener;
import RayTraceAntiEntityESP.paper.nms.NmsAdapter;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.scheduler.RegionOwnershipChecker;
import RayTraceAntiEntityESP.paper.scheduler.ScheduledTaskHandle;
import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapter;
import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapterFactory;
import RayTraceAntiEntityESP.paper.utils.VisibilityUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static RayTraceAntiEntityESP.paper.Main.plugin;

public class RayTraceEngine {

    private static ScheduledTaskHandle task;

    private static final AtomicInteger blockCacheTtlTick = new AtomicInteger(0);
    private static final int BLOCK_CACHE_TTL_TICKS = 200;

    private static final AtomicInteger globalTick = new AtomicInteger(0);
    private static final AtomicInteger bucketEvictSweepTick = new AtomicInteger(0);
    private static final int BUCKET_EVICT_SWEEP_INTERVAL_TICKS = 200;
    private static final int BUCKET_IDLE_EVICT_TICKS = 6000;

    private static final AtomicInteger staggerTick = new AtomicInteger(0);

    private static final Object sharedStateLock = new Object();

    private static final double POS_EPSILON_SQ = 0.01 * 0.01;
    private static final float ROT_EPSILON = 1;
    private static final int AABB_REFRESH_TICKS = 4;
    private static final int STALE_CLONE_CLEANUP_INTERVAL_TICKS = 20;
    private static final double AABB_QUERY_MARGIN = 4;
    private static final double VERTEX_INSET = 0.02;

    private static final double BUCKET_SIZE_XZ = 64;

    private static final double BELOW_NAME_RANGE_BLOCKS = 10;

    private static final Int2ObjectOpenHashMap<ViewerCache> viewerCaches = new Int2ObjectOpenHashMap<>();

    private static final Int2ObjectOpenHashMap<IntSet> distanceOverrideActive = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<IntSet> belowNameRangeActive = new Int2ObjectOpenHashMap<>();

    private static final Object2BooleanOpenHashMap<String> antiEntityTypeCache = new Object2BooleanOpenHashMap<>();

    private static final EnumMap<EntityType, String> ENTITY_TYPE_KEYS = new EnumMap<>(EntityType.class);
    static {
        for (EntityType t : EntityType.values()) {
            ENTITY_TYPE_KEYS.put(t, t.name().toLowerCase());
        }
    }

    private static final class BlockSection {
        final long[] known = new long[64];
        final long[] solid = new long[64];
    }

    private static final java.util.IdentityHashMap<org.bukkit.World, Long2ObjectOpenHashMap<BlockSection>> blockSectionCache =
            new java.util.IdentityHashMap<>();

    private static long sectionKey(int sx, int sy, int sz) {
        return (((long) sx & 0x3FFFFFF) << 38) | (((long) sy & 0xFFF) << 26) | (sz & 0x3FFFFFF);
    }

    private static Long2ObjectOpenHashMap<BlockSection> getOrCreateWorldSections(org.bukkit.World world) {
        return blockSectionCache.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());
    }

    private static BlockSection getOrCreateSection(Long2ObjectOpenHashMap<BlockSection> sections, int sx, int sy, int sz) {
        long key = sectionKey(sx, sy, sz);
        BlockSection section = sections.get(key);
        if (section == null) {
            section = new BlockSection();
            sections.put(key, section);
        }
        return section;
    }

    private static final class SectionCursor {
        int sx = Integer.MIN_VALUE, sy, sz;
        BlockSection section;
    }

    private static BlockSection getSectionCached(Long2ObjectOpenHashMap<BlockSection> sections,
                                                 SectionCursor cursor, int sx, int sy, int sz) {
        if (cursor.section != null && cursor.sx == sx && cursor.sy == sy && cursor.sz == sz) {
            return cursor.section;
        }
        BlockSection section = getOrCreateSection(sections, sx, sy, sz);
        cursor.sx = sx;
        cursor.sy = sy;
        cursor.sz = sz;
        cursor.section = section;
        return section;
    }

    private static class WorldEntitySnapshot {
        Entity[] entities = new Entity[128];
        int entityCount = 0;
        int age;
        int lastAccessTick;
    }

    private static final java.util.IdentityHashMap<org.bukkit.World, Long2ObjectOpenHashMap<WorldEntitySnapshot>> worldEntityCache =
            new java.util.IdentityHashMap<>();

    private static int bucketCoord(double v) {
        return (int) Math.floor(v / BUCKET_SIZE_XZ);
    }

    private static long bucketKey(int bx, int bz) {
        return (((long) bx) << 32) | (bz & 0xFFFFFFFFL);
    }

    private static void evictIdleBuckets() {

        int currentGlobalTick = globalTick.get();
        var worldIt = worldEntityCache.entrySet().iterator();
        while (worldIt.hasNext()) {
            var entry = worldIt.next();
            Long2ObjectOpenHashMap<WorldEntitySnapshot> buckets = entry.getValue();
            if (buckets.isEmpty()) {
                worldIt.remove();
                continue;
            }
            buckets.long2ObjectEntrySet().removeIf(bucket -> (currentGlobalTick - bucket.getValue().lastAccessTick) > BUCKET_IDLE_EVICT_TICKS);
            if (buckets.isEmpty()) {
                worldIt.remove();
            }
        }
    }

    private static class ViewerCache {
        boolean initialized = false;
        double prevX, prevY, prevZ;
        float prevYaw, prevPitch;
        float accumYaw = 0f, accumPitch = 0f;

        double eyeX, eyeY, eyeZ;

        boolean perspectiveValid = false;
        double thirdBackX, thirdBackY, thirdBackZ;
        double thirdFrontX, thirdFrontY, thirdFrontZ;

        final Int2IntOpenHashMap entityIndexMap = new Int2IntOpenHashMap();
        double[] cachedX = new double[64];
        double[] cachedY = new double[64];
        double[] cachedZ = new double[64];
        boolean[] cachedVisible = new boolean[64];
        int cachedCount = 0;

        Entity[] snapshotBuffer = new Entity[64];
        int[] entityIdBuffer = new int[64];
        boolean[] clientVisBuffer = new boolean[64];
        boolean[] asyncResults = new boolean[64];

        ArrayList<Object> outboxBuffer = new ArrayList<>(32);
        ArrayList<Entity> pendingShowsBuffer = new ArrayList<>(16);

        final double[] thirdPersonScratch = new double[3];
        final double[] vertexXBuf = new double[128];
        final double[] vertexYBuf = new double[128];
        final double[] vertexZBuf = new double[128];

        final SectionCursor sectionCursor = new SectionCursor();
    }

    public static void clearViewerCache(int entityId) {
        synchronized (sharedStateLock) {
            viewerCaches.remove(entityId);
            distanceOverrideActive.remove(entityId);
            belowNameRangeActive.remove(entityId);
        }
    }

    public static void onEntityRemovedFromViewer(int viewerId, int entityId) {
        synchronized (sharedStateLock) {
            IntSet distSet = distanceOverrideActive.get(viewerId);
            if (distSet != null) distSet.remove(entityId);
            IntSet belowSet = belowNameRangeActive.get(viewerId);
            if (belowSet != null) belowSet.remove(entityId);

            ViewerCache cache = viewerCaches.get(viewerId);
            if (cache != null) {
                int idx = cache.entityIndexMap.remove(entityId);
                if (idx >= 0 && idx < cache.cachedCount) {
                    cache.cachedVisible[idx] = false;
                }
            }
        }
    }

    public static void clearAntiEntityCache() {
        synchronized (sharedStateLock) {
            antiEntityTypeCache.clear();
        }
    }

    public static void invalidateBlockAt(org.bukkit.World world, int x, int y, int z) {
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        int lx = x & 15, ly = y & 15, lz = z & 15;
        int bitIndex = (ly << 8) | (lz << 4) | lx;
        int word = bitIndex >>> 6;
        long mask = 1L << (bitIndex & 63);
        synchronized (sharedStateLock) {
            Long2ObjectOpenHashMap<BlockSection> sections = blockSectionCache.get(world);
            if (sections == null) return;
            BlockSection section = sections.get(sectionKey(sx, sy, sz));
            if (section == null) return;
            section.known[word] &= ~mask;
            section.solid[word] &= ~mask;
        }
    }

    public static void clearAllCaches() {
        synchronized (sharedStateLock) {
            viewerCaches.clear();
            worldEntityCache.clear();
            blockSectionCache.clear();
            antiEntityTypeCache.clear();
            distanceOverrideActive.clear();
            belowNameRangeActive.clear();
        }
    }

    public static void clearWorldCache(org.bukkit.World world) {
        synchronized (sharedStateLock) {
            worldEntityCache.remove(world);
            blockSectionCache.remove(world);
        }
    }

    private static boolean isOccluding(NmsAdapter adapter, org.bukkit.World world,
                                       Long2ObjectOpenHashMap<BlockSection> sections,
                                       SectionCursor cursor,
                                       int x, int y, int z, boolean folia) {
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        int lx = x & 15, ly = y & 15, lz = z & 15;
        int bitIndex = (ly << 8) | (lz << 4) | lx;
        int word = bitIndex >>> 6;
        long mask = 1L << (bitIndex & 63);

        if (folia) {
            synchronized (sharedStateLock) {
                return isOccludingLocked(adapter, world, sections, cursor, x, y, z, sx, sy, sz, word, mask);
            }
        }
        return isOccludingLocked(adapter, world, sections, cursor, x, y, z, sx, sy, sz, word, mask);
    }

    private static boolean isOccludingLocked(NmsAdapter adapter, org.bukkit.World world,
                                             Long2ObjectOpenHashMap<BlockSection> sections,
                                             SectionCursor cursor,
                                             int x, int y, int z,
                                             int sx, int sy, int sz, int word, long mask) {
        BlockSection section = getSectionCached(sections, cursor, sx, sy, sz);
        if ((section.known[word] & mask) != 0) return (section.solid[word] & mask) != 0;

        boolean result;
        try {
            result = adapter.isBlockSolidAt(world, x, y, z);
        } catch (Throwable t) {
            result = false;
        }

        section.known[word] |= mask;
        if (result) section.solid[word] |= mask;
        return result;
    }

    private static Long2ObjectOpenHashMap<BlockSection> resolveSections(org.bukkit.World world, boolean folia) {
        if (folia) {
            synchronized (sharedStateLock) {
                return getOrCreateWorldSections(world);
            }
        }
        return getOrCreateWorldSections(world);
    }

    public static boolean hitsBlock(org.bukkit.World world, int minY, int maxY,
                                    double ox, double oy, double oz,
                                    double ex2, double ey2, double ez2) {
        NmsAdapter adapter = NmsAdapterFactory.get();
        boolean folia = SchedulerAdapterFactory.isFolia();
        Long2ObjectOpenHashMap<BlockSection> sections = resolveSections(world, folia);
        SectionCursor sectionCursor = new SectionCursor();
        return hitsBlockFast(adapter, folia, sections, sectionCursor, world, minY, maxY, ox, oy, oz, ex2, ey2, ez2);
    }

    private static boolean hitsBlockFast(NmsAdapter adapter, boolean folia,
                                         Long2ObjectOpenHashMap<BlockSection> sections,
                                         SectionCursor sectionCursor,
                                         org.bukkit.World world, int minY, int maxY,
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
            if (posY >= minY && posY <= maxY && isOccluding(adapter, world, sections, sectionCursor, posX, posY, posZ, folia)) return true;
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

    private static boolean applyProximityDebounce(Int2ObjectOpenHashMap<IntSet> stateMap, int viewerId, int entityId, double distSq, double thresholdDist) {
        synchronized (sharedStateLock) {
            IntSet set = stateMap.get(viewerId);

            boolean nowActive = distSq < thresholdDist * thresholdDist;

            if (nowActive) {
                stateMap.computeIfAbsent(viewerId, k -> new IntOpenHashSet()).add(entityId);
            } else if (set != null) {
                set.remove(entityId);
            }
            return nowActive;
        }
    }

    private static boolean isWithinDistanceOverride(int viewerId, int entityId, double distSq) {
        if (Config.checkingDistanceOverride <= 0) return false;
        return applyProximityDebounce(distanceOverrideActive, viewerId, entityId, distSq, Config.checkingDistanceOverride);
    }

    private static boolean isWithinBelowNameRange(Player viewer, Entity entity, int viewerId, int entityId, double distSq) {
        if (!hasBelowNameScore(viewer, entity)) return false;
        return applyProximityDebounce(belowNameRangeActive, viewerId, entityId, distSq, BELOW_NAME_RANGE_BLOCKS);
    }

    private static boolean isEntityInSight(
            Player viewer,
            Entity entity,
            double ex, double ey, double ez,
            double eyeX, double eyeY, double eyeZ,
            double thirdBackX, double thirdBackY, double thirdBackZ,
            double thirdFrontX, double thirdFrontY, double thirdFrontZ,
            boolean perspectiveEnabled,
            double vx, double vy, double vz,
            org.bukkit.World level, int minY, int maxY,
            double[] vertexXBufLocal, double[] vertexYBufLocal, double[] vertexZBufLocal,
            NmsAdapter adapter, boolean folia, Long2ObjectOpenHashMap<BlockSection> sections,
            SectionCursor sectionCursor) {
        double range = Config.getSpigotTrackingRange(entity);
        double dx = vx - ex, dy = vy - ey, dz = vz - ez;
        double horizDistSq = dx * dx + dz * dz, distSq = horizDistSq + dy * dy;
        double distance = Math.sqrt(distSq);

        int viewerId = viewer.getEntityId();
        int entityId = entity.getEntityId();
        boolean withinDistanceOverride = isWithinDistanceOverride(viewerId, entityId, distSq);
        boolean withinBelowNameRange = isWithinBelowNameRange(viewer, entity, viewerId, entityId, distSq);
        if (ExcludeBypassManager.isExcluded(entity.getUniqueId()) || isEntityGlowing(viewer, entity)
                || horizDistSq > range * range
                || withinDistanceOverride
                || withinBelowNameRange) {
            if (Config.isDebugEnabled) DebugVertexRenderer.removeDisplay(viewer.getUniqueId(), entity.getUniqueId());
            return true;
        }

        double[] box = NmsAdapterFactory.get().getEntityBoundingBox(entity);
        double minX = box[0], bMinY = box[1], minZ = box[2];
        double maxX = box[3], bMaxY = box[4], maxZ = box[5];
        double midX = (minX + maxX) * 0.5, midZ = (minZ + maxZ) * 0.5;
        double centerY = (bMinY + bMaxY) * 0.5;

        if (Config.isDebugEnabled) {
            int vCount = fillEntityVertices(distance, range, minX, bMinY, minZ, maxX, bMaxY, maxZ, vertexXBufLocal, vertexYBufLocal, vertexZBufLocal);
            List<Vector> vertices = new ArrayList<>(vCount);
            List<Boolean> vis = new ArrayList<>(vCount);
            boolean visible = false;
            for (int i = 0; i < vCount; i++) {
                boolean r = isVisibleNms(adapter, folia, sections, sectionCursor, level, minY, maxY,
                        eyeX, eyeY, eyeZ,
                        thirdBackX, thirdBackY, thirdBackZ,
                        thirdFrontX, thirdFrontY, thirdFrontZ,
                        perspectiveEnabled,
                        vertexXBufLocal[i], vertexYBufLocal[i], vertexZBufLocal[i]);
                vertices.add(new Vector(vertexXBufLocal[i], vertexYBufLocal[i], vertexZBufLocal[i]));
                vis.add(r);
                if (r) visible = true;
            }
            DebugVertexRenderer.applyDisplay(viewer, entity, vertices, vis);
            return visible;
        }

        if (isVisibleNms(adapter, folia, sections, sectionCursor, level, minY, maxY,
                eyeX, eyeY, eyeZ,
                thirdBackX, thirdBackY, thirdBackZ,
                thirdFrontX, thirdFrontY, thirdFrontZ,
                perspectiveEnabled,
                midX, centerY, midZ)) return true;

        int sparseCount = fillSparseCorners(minX, bMinY, minZ, maxX, bMaxY, maxZ,
                vertexXBufLocal, vertexYBufLocal, vertexZBufLocal);
        for (int i = 0; i < sparseCount; i++) {
            if (isVisibleNms(adapter, folia, sections, sectionCursor, level, minY, maxY,
                    eyeX, eyeY, eyeZ,
                    thirdBackX, thirdBackY, thirdBackZ,
                    thirdFrontX, thirdFrontY, thirdFrontZ,
                    perspectiveEnabled,
                    vertexXBufLocal[i], vertexYBufLocal[i], vertexZBufLocal[i])) return true;
        }
        return false;
    }

    private static int fillSparseCorners(double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         double[] vertexXBufLocal, double[] vertexYBufLocal, double[] vertexZBufLocal) {
        double midX = (minX + maxX) * 0.5, midZ = (minZ + maxZ) * 0.5, midY = (minY + maxY) * 0.5;
        double insetMinX = Math.min(minX + VERTEX_INSET, midX);
        double insetMaxX = Math.max(maxX - VERTEX_INSET, midX);
        double insetMinZ = Math.min(minZ + VERTEX_INSET, midZ);
        double insetMaxZ = Math.max(maxZ - VERTEX_INSET, midZ);
        double insetMinY = Math.min(minY + VERTEX_INSET, midY);
        double insetMaxY = Math.max(maxY - VERTEX_INSET, midY);

        int count = 0;
        for (int layer = 0; layer < 2; layer++) {
            double y = layer == 0 ? insetMinY : insetMaxY;
            vertexXBufLocal[count] = insetMinX; vertexYBufLocal[count] = y; vertexZBufLocal[count] = insetMinZ; count++;
            vertexXBufLocal[count] = insetMinX; vertexYBufLocal[count] = y; vertexZBufLocal[count] = insetMaxZ; count++;
            vertexXBufLocal[count] = insetMaxX; vertexYBufLocal[count] = y; vertexZBufLocal[count] = insetMaxZ; count++;
            vertexXBufLocal[count] = insetMaxX; vertexYBufLocal[count] = y; vertexZBufLocal[count] = insetMinZ; count++;
        }
        return count;
    }

    private static boolean isVisibleNms(NmsAdapter adapter, boolean folia,
                                        Long2ObjectOpenHashMap<BlockSection> sections,
                                        SectionCursor sectionCursor,
                                        org.bukkit.World level, int minY, int maxY,
                                        double eyeX, double eyeY, double eyeZ,
                                        double thirdBackX, double thirdBackY, double thirdBackZ,
                                        double thirdFrontX, double thirdFrontY, double thirdFrontZ,
                                        boolean perspectiveEnabled,
                                        double endX, double endY, double endZ) {
        if (!hitsBlockFast(adapter, folia, sections, sectionCursor, level, minY, maxY, eyeX, eyeY, eyeZ, endX, endY, endZ)) return true;
        if (!perspectiveEnabled) return false;
        if (!hitsBlockFast(adapter, folia, sections, sectionCursor, level, minY, maxY, thirdBackX, thirdBackY, thirdBackZ, endX, endY, endZ)) return true;
        return !hitsBlockFast(adapter, folia, sections, sectionCursor, level, minY, maxY, thirdFrontX, thirdFrontY, thirdFrontZ, endX, endY, endZ);
    }

    private static void computeThirdPersonPos(NmsAdapter adapter, boolean folia,
                                              Long2ObjectOpenHashMap<BlockSection> sections,
                                              SectionCursor sectionCursor,
                                              org.bukkit.World level, int minY, int maxY,
                                              double ox, double oy, double oz,
                                              double dirX, double dirY, double dirZ,
                                              double maxDistance,
                                              double[] scratch) {
        double dlen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dlen == 0) {
            scratch[0] = ox;
            scratch[1] = oy;
            scratch[2] = oz;
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
            if (posY >= minY && posY <= maxY && isOccluding(adapter, level, sections, sectionCursor, posX, posY, posZ, folia)) {
                double t = Math.max(0, curT - 0.1);
                scratch[0] = ox + dirX * t;
                scratch[1] = oy + dirY * t;
                scratch[2] = oz + dirZ * t;
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
        scratch[0] = ox + dirX * maxDistance;
        scratch[1] = oy + dirY * maxDistance;
        scratch[2] = oz + dirZ * maxDistance;
    }

    private static boolean hasBelowNameScore(Player viewer, Entity entity) {
        String objective = PacketManager.belowNameObjective.get(viewer.getUniqueId());
        if (objective == null) return false;
        java.util.Map<String, Set<String>> perObjective = PacketManager.objectiveScores.get(viewer.getUniqueId());
        if (perObjective == null) return false;
        Set<String> entries = perObjective.get(objective);
        if (entries == null) return false;
        String entry = entity instanceof Player p ? p.getName() : entity.getUniqueId().toString();
        return entries.contains(entry);
    }

    public static boolean isAntiEntity(String typeKey, UUID entityUUID) {
        if (typeKey == null) return false;
        if (ExcludeBypassManager.isExcluded(entityUUID)) return false;
        return isAntiEntityType(typeKey.toLowerCase());
    }

    public static boolean isAntiEntity(Entity entity) {
        String typeKey = ENTITY_TYPE_KEYS.get(entity.getType());
        if (typeKey == null) return false;
        if (ExcludeBypassManager.isExcluded(entity.getUniqueId())) return false;
        return isAntiEntityType(typeKey.toLowerCase());
    }

    public static boolean isAntiEntityType(String typeKey) {
        synchronized (sharedStateLock) {
            if (antiEntityTypeCache.containsKey(typeKey)) return antiEntityTypeCache.getBoolean(typeKey);
            boolean listed = Config.antiEntities.contains(typeKey);
            boolean result = Config.isBlacklist != listed;
            antiEntityTypeCache.put(typeKey, result);
            return result;
        }
    }

    private static int fillEntityVertices(double distance, double checkingRange,
                                          double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ,
                                          double[] vertexXBufLocal,
                                          double[] vertexYBufLocal,
                                          double[] vertexZBufLocal) {
        if (Config.checkingVerticesLayers < 2) throw new ExceptionInInitializerError("sampleLayers must be at least 2");
        double midX = (minX + maxX) * 0.5, midZ = (minZ + maxZ) * 0.5;
        double insetMinX = Math.min(minX + VERTEX_INSET, midX);
        double insetMaxX = Math.max(maxX - VERTEX_INSET, midX);
        double insetMinZ = Math.min(minZ + VERTEX_INSET, midZ);
        double insetMaxZ = Math.max(maxZ - VERTEX_INSET, midZ);
        double midY = (minY + maxY) * 0.5;
        double insetMinY = Math.min(minY + VERTEX_INSET, midY);
        double insetMaxY = Math.max(maxY - VERTEX_INSET, midY);
        double ratio = checkingRange > 0 ? Math.min(distance / checkingRange, 1.0) : 0.0;
        int scaledSampleLayers = Math.max(2, (int) Math.round(Config.checkingVerticesLayers * (1.0 - ratio * 0.75)));
        boolean includeCorners = ratio < 0.25;
        boolean hasExtra = Config.checkingBoundingBoxExtraValue > 0;
        double eMinX = minX - Config.checkingBoundingBoxExtraValue, eMaxX = maxX + Config.checkingBoundingBoxExtraValue;
        double eMinZ = minZ - Config.checkingBoundingBoxExtraValue, eMaxZ = maxZ + Config.checkingBoundingBoxExtraValue;

        int maxVerts = scaledSampleLayers * (includeCorners ? 17 : 1);

        if (maxVerts > vertexXBufLocal.length) {
            throw new IllegalStateException("vertex buffer overflow: " + maxVerts + " > " + vertexXBufLocal.length);
        }

        int count = 0;
        for (int i = 0; i < scaledSampleLayers; i++) {
            double y = lerp(((double) i) / (scaledSampleLayers - 1), insetMinY, insetMaxY);
            vertexXBufLocal[count] = midX;
            vertexYBufLocal[count] = y;
            vertexZBufLocal[count] = midZ;
            count++;
            if (includeCorners) {
                if (hasExtra) {
                    vertexXBufLocal[count] = eMinX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = eMinZ;
                    count++;
                    vertexXBufLocal[count] = eMinX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = eMaxZ;
                    count++;
                    vertexXBufLocal[count] = eMaxX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = eMaxZ;
                    count++;
                    vertexXBufLocal[count] = eMaxX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = eMinZ;
                    count++;
                    vertexXBufLocal[count] = midX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = eMinZ;
                    count++;
                    vertexXBufLocal[count] = midX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = eMaxZ;
                    count++;
                    vertexXBufLocal[count] = eMinX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = midZ;
                    count++;
                    vertexXBufLocal[count] = eMaxX;
                    vertexYBufLocal[count] = y;
                    vertexZBufLocal[count] = midZ;
                    count++;
                }
                vertexXBufLocal[count] = insetMinX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = insetMinZ;
                count++;
                vertexXBufLocal[count] = insetMinX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = insetMaxZ;
                count++;
                vertexXBufLocal[count] = insetMaxX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = insetMaxZ;
                count++;
                vertexXBufLocal[count] = insetMaxX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = insetMinZ;
                count++;
                vertexXBufLocal[count] = midX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = insetMinZ;
                count++;
                vertexXBufLocal[count] = midX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = insetMaxZ;
                count++;
                vertexXBufLocal[count] = insetMinX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = midZ;
                count++;
                vertexXBufLocal[count] = insetMaxX;
                vertexYBufLocal[count] = y;
                vertexZBufLocal[count] = midZ;
                count++;
            }
        }
        return count;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer, boolean visibleClient,
                                              List<Object> outbox) {
        updateRayTraceChecking(viewer, entity, visibleServer, visibleClient, outbox, true, null);
    }

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer, boolean visibleClient,
                                              List<Object> outbox, boolean forceRefresh) {
        updateRayTraceChecking(viewer, entity, visibleServer, visibleClient, outbox, forceRefresh, null);
    }

    public static void updateRayTraceChecking(Player viewer, Entity entity, boolean visibleServer, boolean visibleClient,
                                              List<Object> outbox, boolean forceRefresh, IntSet hiddenSet) {
        if (visibleServer && !visibleClient) {
            VisibilityUtils.setNotHidden(viewer, entity);
            if (Config.isDisplayNameEnabled)
                NametagCloneRenderer.removeDisplay(viewer.getUniqueId(), entity.getUniqueId(), outbox);
        } else if (!visibleServer && visibleClient) {
            VisibilityUtils.setHidden(viewer, entity);
            if (Config.isDisplayNameEnabled) NametagCloneRenderer.applyDisplay(viewer, entity, outbox);
        } else if (!visibleServer) {
            if (Config.isDisplayNameEnabled && forceRefresh) {
                if (hiddenSet != null) NametagCloneRenderer.refreshDisplay(viewer, entity, outbox, hiddenSet);
                else NametagCloneRenderer.refreshDisplay(viewer, entity, outbox);
            }
        }
    }

    public static void killTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        SchedulerAdapter scheduler = SchedulerAdapterFactory.get();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            final Player v = viewer;
            scheduler.runEntityTask(v, () -> clearViewerVisibility(v));
        }
        killTaskCommon();
    }

    public static void shutdownCleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            clearViewerVisibility(viewer);
        }
        killTaskCommon();
    }

    private static void clearViewerVisibility(Player v) {
        int vid = v.getEntityId();
        for (Entity nmsEntity : v.getWorld().getEntities()) {
            if (!RegionOwnershipChecker.isOwnedByCurrentRegion(nmsEntity)) continue;
            int tid = nmsEntity.getEntityId();
            if (tid == vid) continue;
            if (VisibilityUtils.isHidden(vid, tid))
                VisibilityUtils.setNotHidden(v, nmsEntity);
        }
        VisibilityUtils.clearViewer(vid);
    }

    private static void killTaskCommon() {
        NametagCloneRenderer.removeAllDisplays();
        DebugVertexRenderer.removeAllDisplays();
        PacketManager.clearAllBypasses();
        AddEntityPacketListener.pendingHides.clear();

        clearAllCaches();

        blockCacheTtlTick.set(0);
        globalTick.set(0);
        bucketEvictSweepTick.set(0);
        staggerTick.set(0);
    }

    public static void startTask() {
        killTask();
        final SchedulerAdapter scheduler = SchedulerAdapterFactory.get();
        task = scheduler.runTaskTimer(() -> {

            AddEntityPacketListener.drainPendingHides();

            if (blockCacheTtlTick.incrementAndGet() > BLOCK_CACHE_TTL_TICKS) {
                synchronized (sharedStateLock) {
                    blockSectionCache.clear();
                }
                blockCacheTtlTick.set(0);
            }

            globalTick.incrementAndGet();
            if (bucketEvictSweepTick.incrementAndGet() > BUCKET_EVICT_SWEEP_INTERVAL_TICKS) {
                synchronized (sharedStateLock) {
                    evictIdleBuckets();
                }
                bucketEvictSweepTick.set(0);
            }

            staggerTick.incrementAndGet();

            java.util.List<Player> onlinePlayers = new ArrayList<>();
            NmsAdapterFactory.get().forEachServerPlayer(p -> {
                if (!PacketManager.isBypassed(p.getUniqueId()) && Config.isWorldAllowed(p.getWorld().getName()))
                    onlinePlayers.add(p);
            });
            if (onlinePlayers.isEmpty()) return;

            int groups = Math.max(1, Config.checkingStaggerGroups);
            int currentGroup = staggerTick.get() % groups;
            boolean perspectiveEnabled = Config.isPerspectiveCheckingEnabled;

            for (Player viewer : onlinePlayers) {
                final Player v = viewer;
                scheduler.runEntityTask(v, () -> processPlayer(v, currentGroup, groups, perspectiveEnabled));
            }
        }, 0L, Config.checkingPeriodTicks);
    }

    private static void processPlayer(Player viewer, int currentGroup, int groups, boolean perspectiveEnabled) {
        try {
            processPlayerInternal(viewer, currentGroup, groups, perspectiveEnabled);
        } catch (Throwable t) {

            plugin.getLogger().warning("[RayTraceAntiEntityESP] Raytrace error for "
                    + viewer.getName() + ": " + t);
        }
    }

    private static void processPlayerInternal(Player viewer, int currentGroup, int groups, boolean perspectiveEnabled) {
        int vid = viewer.getEntityId();
        Location loc = viewer.getLocation();
        double vx = loc.getX(), vy = loc.getY(), vz = loc.getZ();
        org.bukkit.World world = viewer.getWorld();

        ViewerCache cache;
        synchronized (sharedStateLock) {
            cache = viewerCaches.get(vid);
            if (cache == null) {
                cache = new ViewerCache();
                viewerCaches.put(vid, cache);
            }
        }

        double r = Config.getMaxTrackingRange(), rangeSq = r * r;

        int bx = bucketCoord(vx), bz = bucketCoord(vz);
        long bucket = bucketKey(bx, bz);

        if (SchedulerAdapterFactory.isFolia()) {
            double pad = r + AABB_QUERY_MARGIN;
            int bucketChunks = (int) (BUCKET_SIZE_XZ / 16.0);
            int centerChunkX = bx * bucketChunks + bucketChunks / 2;
            int centerChunkZ = bz * bucketChunks + bucketChunks / 2;
            int radiusChunks = (int) Math.ceil((BUCKET_SIZE_XZ / 2.0 + pad) / 16.0);
            if (!RegionOwnershipChecker.isOwnedByCurrentRegion(world, centerChunkX, centerChunkZ, radiusChunks)) {
                return;
            }
        }

        WorldEntitySnapshot worldSnap;
        synchronized (sharedStateLock) {
            Long2ObjectOpenHashMap<WorldEntitySnapshot> worldBuckets =
                    worldEntityCache.computeIfAbsent(world, k -> new Long2ObjectOpenHashMap<>());
            worldSnap = worldBuckets.get(bucket);
            boolean refreshWorld = worldSnap == null || worldSnap.age >= AABB_REFRESH_TICKS;

            if (refreshWorld) {
                if (worldSnap == null) {
                    worldSnap = new WorldEntitySnapshot();
                    worldBuckets.put(bucket, worldSnap);
                }
                WorldEntitySnapshot snap = worldSnap;
                snap.entityCount = 0;

                double pad = r + AABB_QUERY_MARGIN;
                double cellMinX = bx * BUCKET_SIZE_XZ - pad, cellMaxX = (bx + 1) * BUCKET_SIZE_XZ + pad;
                double cellMinZ = bz * BUCKET_SIZE_XZ - pad, cellMaxZ = (bz + 1) * BUCKET_SIZE_XZ + pad;
                int worldMinY = world.getMinHeight(), worldMaxY = world.getMaxHeight();

                NmsAdapterFactory.get().getAllEntitiesInBox(world,
                        cellMinX, worldMinY, cellMinZ, cellMaxX, worldMaxY, cellMaxZ, e -> {
                            if (!isAntiEntity(e)) return;
                            if (!RegionOwnershipChecker.isOwnedByCurrentRegion(e)) return;
                            if (snap.entityCount >= snap.entities.length)
                                snap.entities = Arrays.copyOf(snap.entities, snap.entities.length + (snap.entities.length >> 1));
                            snap.entities[snap.entityCount++] = e;
                        });
                snap.age = 0;
            } else {
                worldSnap.age++;
            }
            worldSnap.lastAccessTick = globalTick.get();
        }

        int aabbCount = worldSnap.entityCount;
        if (aabbCount == 0) return;

        NmsAdapter adapter = NmsAdapterFactory.get();
        boolean folia = SchedulerAdapterFactory.isFolia();
        Long2ObjectOpenHashMap<BlockSection> sections = resolveSections(world, folia);
        SectionCursor sectionCursor = cache.sectionCursor;

        if (cache.snapshotBuffer.length < aabbCount) {
            int nl = aabbCount + 16;
            cache.snapshotBuffer = new Entity[nl];
            cache.entityIdBuffer = new int[nl];
            cache.clientVisBuffer = new boolean[nl];
            cache.asyncResults = new boolean[nl];
        }
        Entity[] snapshot = cache.snapshotBuffer;
        int[] entityIds = cache.entityIdBuffer;
        int count = 0;
        IntSet externallyHiddenSet = VisibilityUtils.getExternallyHiddenSet(vid);
        for (int ei = 0; ei < aabbCount; ei++) {
            Entity nmsEntity = worldSnap.entities[ei];
            if (!nmsEntity.isValid()) continue;
            int eid = nmsEntity.getEntityId();
            if (eid == vid) continue;
            if (externallyHiddenSet != null && externallyHiddenSet.contains(eid)) continue;
            double ex = nmsEntity.getX(), ey = nmsEntity.getY(), ez = nmsEntity.getZ();
            double dxe = ex - vx, dye = ey - vy, dze = ez - vz;
            if ((dxe * dxe + dye * dye + dze * dze) > rangeSq) continue;
            snapshot[count] = nmsEntity;
            entityIds[count] = eid;
            count++;
        }
        if (count == 0) return;

        Location eyeLoc = viewer.getEyeLocation();
        float yaw = loc.getYaw(), pitch = loc.getPitch();
        double cosPitch = Math.cos(Math.toRadians(pitch));
        double ldx = -Math.sin(Math.toRadians(yaw)) * cosPitch;
        double ldy = -Math.sin(Math.toRadians(pitch));
        double ldz = Math.cos(Math.toRadians(yaw)) * cosPitch;

        boolean posMoved;
        boolean rotMoved;
        if (!cache.initialized) {
            posMoved = true;
            rotMoved = true;
            cache.initialized = true;
            cache.accumYaw = 0f;
            cache.accumPitch = 0f;
        } else {
            double ddx = vx - cache.prevX, ddy = vy - cache.prevY, ddz = vz - cache.prevZ;
            cache.accumYaw += Math.abs(yaw - cache.prevYaw);
            cache.accumPitch += Math.abs(pitch - cache.prevPitch);
            posMoved = (ddx * ddx + ddy * ddy + ddz * ddz) > POS_EPSILON_SQ;
            rotMoved = cache.accumYaw > ROT_EPSILON || cache.accumPitch > ROT_EPSILON;
        }
        boolean moved = posMoved || rotMoved;
        if (moved) {
            cache.accumYaw = 0f;
            cache.accumPitch = 0f;
        }
        cache.prevX = vx;
        cache.prevY = vy;
        cache.prevZ = vz;
        cache.prevYaw = yaw;
        cache.prevPitch = pitch;

        cache.eyeX = eyeLoc.getX();
        cache.eyeY = eyeLoc.getY();
        cache.eyeZ = eyeLoc.getZ();

        double[] thirdPersonScratchLocal = cache.thirdPersonScratch;

        if (perspectiveEnabled) {
            if (moved || !cache.perspectiveValid) {
                int worldMinY = world.getMinHeight();
                int worldMaxY = world.getMaxHeight();
                computeThirdPersonPos(adapter, folia, sections, sectionCursor, world, worldMinY, worldMaxY,
                        cache.eyeX, cache.eyeY, cache.eyeZ,
                        -ldx, -ldy, -ldz,
                        Config.perspectiveCheckingDistance, thirdPersonScratchLocal);
                cache.thirdBackX = thirdPersonScratchLocal[0];
                cache.thirdBackY = thirdPersonScratchLocal[1];
                cache.thirdBackZ = thirdPersonScratchLocal[2];
                computeThirdPersonPos(adapter, folia, sections, sectionCursor, world, worldMinY, worldMaxY,
                        cache.eyeX, cache.eyeY, cache.eyeZ,
                        ldx, ldy, ldz,
                        Config.perspectiveCheckingDistance, thirdPersonScratchLocal);
                cache.thirdFrontX = thirdPersonScratchLocal[0];
                cache.thirdFrontY = thirdPersonScratchLocal[1];
                cache.thirdFrontZ = thirdPersonScratchLocal[2];
                cache.perspectiveValid = true;
            }
        } else {
            cache.perspectiveValid = false;
        }

        boolean[] clientVis = cache.clientVisBuffer;
        IntSet hiddenSet = VisibilityUtils.getHiddenSet(vid);
        for (int ci = 0; ci < count; ci++)
            clientVis[ci] = hiddenSet == null || !hiddenSet.contains(entityIds[ci]);

        double[] vertexXBufLocal = cache.vertexXBuf;
        double[] vertexYBufLocal = cache.vertexYBuf;
        double[] vertexZBufLocal = cache.vertexZBuf;

        boolean[] results = cache.asyncResults;
        boolean vMoved = posMoved;
        double eyeX = cache.eyeX, eyeY = cache.eyeY, eyeZ = cache.eyeZ;
        double thirdBackX = cache.thirdBackX, thirdBackY = cache.thirdBackY, thirdBackZ = cache.thirdBackZ;
        double thirdFrontX = cache.thirdFrontX, thirdFrontY = cache.thirdFrontY, thirdFrontZ = cache.thirdFrontZ;
        boolean perspValid = cache.perspectiveValid && perspectiveEnabled;
        int minY = world.getMinHeight(), maxY = world.getMaxHeight();

        for (int j = 0; j < count; j++) {
            Entity nmsEnt = snapshot[j];
            int eid = entityIds[j];
            double ex = nmsEnt.getX(), ey = nmsEnt.getY(), ez = nmsEnt.getZ();
            int idx = cache.entityIndexMap.getOrDefault(eid, -1);

            boolean forceCheck = idx < 0 || vMoved || Config.isDebugEnabled;

            boolean entityMoved = false;
            if (idx >= 0 && !forceCheck) {
                double dxe = ex - cache.cachedX[idx], dye = ey - cache.cachedY[idx], dze = ez - cache.cachedZ[idx];
                entityMoved = (dxe * dxe + dye * dye + dze * dze) > POS_EPSILON_SQ;
            }

            if (forceCheck || entityMoved || (eid % groups) == currentGroup) {
                boolean visible = isEntityInSight(
                        viewer, nmsEnt, ex, ey, ez,
                        eyeX, eyeY, eyeZ,
                        thirdBackX, thirdBackY, thirdBackZ,
                        thirdFrontX, thirdFrontY, thirdFrontZ,
                        perspValid,
                        vx, vy, vz,
                        world, minY, maxY,
                        vertexXBufLocal, vertexYBufLocal, vertexZBufLocal,
                        adapter, folia, sections, sectionCursor);
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

        ArrayList<Object> outbox = cache.outboxBuffer;
        outbox.clear();
        ArrayList<Entity> pendingShows = cache.pendingShowsBuffer;
        pendingShows.clear();
        boolean refreshNametag = globalTick.get() % Math.max(1, Config.displayNamePeriodTicks) == 0;
        for (int j = 0; j < count; j++) {
            boolean visServer = results[j], visClient = clientVis[j];
            if (visServer && visClient) continue;
            if (visServer) {
                pendingShows.add(snapshot[j]);
                continue;
            }
            updateRayTraceChecking(viewer, snapshot[j], false, visClient, outbox, refreshNametag, hiddenSet);
        }
        for (Entity e : pendingShows) {
            if (VisibilityUtils.isHidden(vid, e.getEntityId()))
                updateRayTraceChecking(viewer, e, true, false, outbox);
        }
        if (Config.isDisplayNameEnabled && globalTick.get() % STALE_CLONE_CLEANUP_INTERVAL_TICKS == vid % STALE_CLONE_CLEANUP_INTERVAL_TICKS)
            NametagCloneRenderer.cleanupStaleClones(outbox, viewer);
        if (!outbox.isEmpty())
            NmsAdapterFactory.get().sendBundled(viewer, outbox);
    }
}