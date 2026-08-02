package RayTraceAntiEntityESP.paper.engine;

import RayTraceAntiEntityESP.paper.config.Config;
import RayTraceAntiEntityESP.paper.listener.packet.SetEntityDataPacketListener;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.utils.NametagCloneUtils;
import RayTraceAntiEntityESP.paper.utils.TeamUtils;
import RayTraceAntiEntityESP.paper.utils.VisibilityUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.paper.Main.plugin;

public class NametagCloneRenderer {

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, NametagCloneUtils>> clones = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, double[]> velocityTrackers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, NameCacheEntry>> nameCache = new ConcurrentHashMap<>();
    private static final double CLONE_MOVE_EPSILON_SQ = 0.01 * 0.01;

    private static final class NameCacheEntry {
        Component customName;
        NamedTextColor teamColor;
        Component teamPrefix;
        Component teamSuffix;
        Component result;
    }

    private static double[] extrapolatedPos(Entity entity) {
        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        double lookahead = Config.displayNameLookaheadTicks;
        if (lookahead <= 0) return new double[]{ex, ey, ez};

        int tick = Bukkit.getCurrentTick();
        double[] tracker = velocityTrackers.computeIfAbsent(entity.getUniqueId(),
                var -> new double[]{ex, ey, ez, tick, 0, 0, 0});

        int dt = tick - (int) tracker[3];
        if (dt > 0) {
            tracker[4] = (ex - tracker[0]) / dt;
            tracker[5] = (ey - tracker[1]) / dt;
            tracker[6] = (ez - tracker[2]) / dt;
            tracker[0] = ex;
            tracker[1] = ey;
            tracker[2] = ez;
            tracker[3] = tick;
        }
        return new double[]{
                ex + tracker[4] * lookahead,
                ey + tracker[5] * lookahead,
                ez + tracker[6] * lookahead
        };
    }

    private static void maybeClearVelocityTracker(UUID entityUUID) {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner != null && inner.containsKey(entityUUID)) return;
        }
        velocityTrackers.remove(entityUUID);
    }

    private static boolean shouldShowFast(Player viewer, Entity entity) {
        return shouldShowFast(viewer, entity, VisibilityUtils.getHiddenSet(viewer.getEntityId()));
    }

    private static boolean shouldShowFast(Player viewer, Entity entity, it.unimi.dsi.fastutil.ints.IntSet hiddenSet) {
        if (entity.isDead()) return false;
        if (!entity.isValid()) return false;

        int targetEntityId = entity.getEntityId();
        if (hiddenSet == null || !hiddenSet.contains(targetEntityId)) return false;

        Boolean cachedInvisible = SetEntityDataPacketListener.getInvisible(targetEntityId);
        if (cachedInvisible != null ? cachedInvisible : entity.isInvisible()) return false;

        if (entity instanceof Player player) {
            if (player.isSneaking()) return false;
            return !NmsAdapterFactory.get().hasDisconnected(player);
        }
        return true;
    }

    private static boolean shouldShowFull(Player viewer, Entity entity) {
        if (!shouldShowFast(viewer, entity)) return false;
        return VisibilityUtils.isNameVisible(viewer, entity);
    }

    private static double clonePosY(double rawY, Entity entity) {
        return rawY + entity.getHeight() + Config.displayNameOffSetY;
    }

    public static void applyDisplay(Player viewer, Entity entity, List<Object> outbox) {
        UUID viewerUUID = viewer.getUniqueId();
        UUID entityUUID = entity.getUniqueId();

        if (!shouldShowFull(viewer, entity)) {
            removeDisplay(viewerUUID, entityUUID, outbox);
            return;
        }

        ConcurrentHashMap<UUID, NametagCloneUtils> inner = clones.computeIfAbsent(viewerUUID, var -> new ConcurrentHashMap<>());

        NametagCloneUtils existing = inner.get(entityUUID);
        if (existing != null) {
            if (!existing.isSpawned()) {
                inner.remove(entityUUID);
                despawnClone(existing, outbox);
            } else {
                existing.setOutbox(outbox);
                try {
                    existing.setName(getName(viewer, entity));
                    double[] pos = extrapolatedPos(entity);
                    existing.teleport(pos[0], clonePosY(pos[1], entity), pos[2]);
                } finally {
                    existing.setOutbox(null);
                }
                return;
            }
        }

        try {
            NametagCloneUtils clone = new NametagCloneUtils(viewer);
            clone.setOutbox(outbox);
            try {
                clone.setName(getName(viewer, entity));
                double[] pos = extrapolatedPos(entity);
                clone.setPos(pos[0], clonePosY(pos[1], entity), pos[2]);
                clone.spawn();
            } finally {
                clone.setOutbox(null);
            }
            inner.put(entityUUID, clone);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn display for " + viewer.getName() + " -> " + entity.getName() + ": " + t);
        }
    }

    public static void refreshDisplay(Player viewer, Entity entity, List<Object> outbox) {
        refreshDisplay(viewer, entity, outbox, VisibilityUtils.getHiddenSet(viewer.getEntityId()));
    }

    public static void refreshDisplay(Player viewer, Entity entity, List<Object> outbox, it.unimi.dsi.fastutil.ints.IntSet hiddenSet) {
        UUID viewerUUID = viewer.getUniqueId();
        UUID entityUUID = entity.getUniqueId();

        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) {
            applyDisplay(viewer, entity, outbox);
            return;
        }

        NametagCloneUtils existing = inner.get(entityUUID);
        if (existing == null || !existing.isSpawned()) {
            applyDisplay(viewer, entity, outbox);
            return;
        }

        if (!shouldShowFast(viewer, entity, hiddenSet)) {
            inner.remove(entityUUID);
            despawnClone(existing, outbox);
            return;
        }

        double[] pos = extrapolatedPos(entity);
        double nx = pos[0], ny = clonePosY(pos[1], entity), nz = pos[2];
        double dx = nx - existing.getX(), dy = ny - existing.getY(), dz = nz - existing.getZ();
        boolean entityMoved = (dx * dx + dy * dy + dz * dz) > CLONE_MOVE_EPSILON_SQ;

        existing.setOutbox(outbox);
        try {
            existing.setName(getName(viewer, entity));
            if (entityMoved) existing.teleport(nx, ny, nz);
        } finally {
            existing.setOutbox(null);
        }
    }

    public static void removeDisplay(UUID viewerUUID, UUID entityUUID, List<Object> outbox) {
        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) return;
        NametagCloneUtils clone = inner.remove(entityUUID);
        if (clone == null) return;
        despawnClone(clone, outbox);
        maybeClearVelocityTracker(entityUUID);
        ConcurrentHashMap<UUID, NameCacheEntry> nameInner = nameCache.get(viewerUUID);
        if (nameInner != null) nameInner.remove(entityUUID);
    }

    public static void removeDisplay(UUID viewerUUID) {
        Map<UUID, NametagCloneUtils> inner = clones.remove(viewerUUID);
        nameCache.remove(viewerUUID);
        if (inner == null) return;
        for (NametagCloneUtils clone : inner.values()) {
            if (clone == null) continue;
            despawnClone(clone, null);
        }
        inner.keySet().forEach(NametagCloneRenderer::maybeClearVelocityTracker);
    }

    public static void removeDisplayForEntity(UUID entityUUID) {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner == null) continue;
            NametagCloneUtils clone = inner.remove(entityUUID);
            if (clone == null) continue;
            despawnClone(clone, null);
        }
        for (ConcurrentHashMap<UUID, NameCacheEntry> nameInner : nameCache.values()) {
            if (nameInner != null) nameInner.remove(entityUUID);
        }
        velocityTrackers.remove(entityUUID);
    }

    public static void cleanupStaleClones(List<Object> outbox, Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) return;
        ConcurrentHashMap<UUID, NameCacheEntry> nameInner = nameCache.get(viewerUUID);
        it.unimi.dsi.fastutil.ints.IntSet hiddenSet = VisibilityUtils.getHiddenSet(viewer.getEntityId());
        java.util.List<UUID> removed = new ArrayList<>();
        inner.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !shouldShowFast(viewer, entity, hiddenSet)) {
                despawnClone(entry.getValue(), outbox);
                if (nameInner != null) nameInner.remove(entry.getKey());
                removed.add(entry.getKey());
                return true;
            }
            return false;
        });
        for (UUID uuid : removed) maybeClearVelocityTracker(uuid);
    }

    public static void removeAllDisplays() {
        clones.forEach((viewerUUID, inner) -> {
            if (inner == null) return;
            Player viewer = Bukkit.getPlayer(viewerUUID);
            if (viewer == null) {
                inner.values().forEach(c -> despawnClone(c, null));
                return;
            }
            List<Object> outbox = new ArrayList<>(inner.size());
            for (NametagCloneUtils clone : inner.values()) {
                despawnClone(clone, outbox);
            }
            if (!outbox.isEmpty()) {
                NmsAdapterFactory.get().sendBundled(viewer, outbox);
            }
        });
        clones.clear();
        velocityTrackers.clear();
        nameCache.clear();
    }

    private static void despawnClone(NametagCloneUtils clone, List<Object> outbox) {
        if (clone == null) return;
        try {
            clone.setOutbox(outbox);
            try {
                clone.despawn();
            } finally {
                clone.setOutbox(null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Component getName(Player viewer, Entity entity) {
        Component custom = entity.customName();
        TeamUtils.TeamInfo teamInfo = TeamUtils.resolveTeamInfo(viewer, entity);
        NamedTextColor teamColor = teamInfo.color();
        Component teamPrefix = teamInfo.prefix();
        Component teamSuffix = teamInfo.suffix();

        ConcurrentHashMap<UUID, NameCacheEntry> inner =
                nameCache.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
        NameCacheEntry cached = inner.get(entity.getUniqueId());
        if (cached != null
                && cached.customName == custom
                && cached.teamColor == teamColor
                && cached.teamPrefix == teamPrefix
                && cached.teamSuffix == teamSuffix) {
            return cached.result;
        }

        Component name = (!(entity instanceof Player) && custom != null) ? custom : Component.text(entity.getName());
        if (teamColor != null) name = name.color(teamColor);
        if (teamPrefix != null) name = teamPrefix.append(name);
        if (teamSuffix != null) name = name.append(teamSuffix);

        NameCacheEntry fresh = new NameCacheEntry();
        fresh.customName = custom;
        fresh.teamColor = teamColor;
        fresh.teamPrefix = teamPrefix;
        fresh.teamSuffix = teamSuffix;
        fresh.result = name;
        inner.put(entity.getUniqueId(), fresh);

        return name;
    }
}