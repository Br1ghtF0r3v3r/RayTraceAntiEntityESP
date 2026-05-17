package RayTraceAntiEntityESP.bukkit.engine;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.packet.SetEntityDataPacketListener;
import RayTraceAntiEntityESP.bukkit.utils.NametagCloneUtils;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class NametagCloneRenderer {

    private static final ConcurrentHashMap<UUID, Map<UUID, NametagCloneUtils>> clones = new ConcurrentHashMap<>();
    private static final double CLONE_MOVE_EPSILON_SQ = 0.001 * 0.001;

    private static boolean shouldShowFast(Player viewer, Entity entity) {
        if (entity.isDead()) return false;
        if (!entity.isValid()) return false;

        int viewerEntityId = ((CraftPlayer) viewer).getHandle().getId();
        int targetEntityId = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle().getId();
        if (!VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) return false;

        Boolean cachedInvis = SetEntityDataPacketListener.invisibleCache.get(entity.getEntityId());
        if (cachedInvis != null ? cachedInvis : entity.isInvisible()) return false;
        if (entity instanceof Player player) {
            if (player.isSneaking()) return false;
            return !((CraftPlayer) player).getHandle().hasDisconnected();
        }
        return true;
    }

    private static boolean shouldShowFull(Player viewer, Entity entity) {
        if (!shouldShowFast(viewer, entity)) return false;
        return VisibilityUtils.isNameVisible(viewer, entity);
    }

    private static double clonePosY(Entity entity) {
        return entity.getY() + entity.getHeight() + Config.displayNameOffSetY;
    }

    public static void applyDisplay(Player viewer, Entity entity, List<Packet<? super ClientGamePacketListener>> outbox) {
        UUID viewerUUID = viewer.getUniqueId();
        UUID entityUUID = entity.getUniqueId();

        if (!shouldShowFull(viewer, entity)) {
            removeDisplay(viewerUUID, entityUUID, outbox);
            return;
        }
        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) {
            inner = new ConcurrentHashMap<>();
            Map<UUID, NametagCloneUtils> existing = clones.putIfAbsent(viewerUUID, inner);
            if (existing != null) inner = existing;
        }
        ConcurrentHashMap<UUID, NametagCloneUtils> innerMap = (ConcurrentHashMap<UUID, NametagCloneUtils>) inner;
        NametagCloneUtils existing = innerMap.get(entityUUID);
        if (existing != null) {
            if (!existing.isSpawned()) {
                innerMap.remove(entityUUID);
                despawnClone(existing, outbox);
            } else {
                existing.setOutbox(outbox);
                try {
                    existing.setName(getName(entity));
                    existing.teleport(entity.getX(), clonePosY(entity), entity.getZ());
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
                clone.setName(getName(entity));
                clone.setPos(entity.getX(), clonePosY(entity), entity.getZ());
                clone.spawn();
            } finally {
                clone.setOutbox(null);
            }
            innerMap.put(entityUUID, clone);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn display for " + viewer.getName() + " -> " + entity.getName() + ": " + t);
        }
    }

    public static void refreshDisplay(Player viewer, Entity entity, List<Packet<? super ClientGamePacketListener>> outbox) {
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
        if (!shouldShowFast(viewer, entity)) {
            inner.remove(entityUUID);
            despawnClone(existing, outbox);
            return;
        }

        double nx = entity.getX(), ny = clonePosY(entity), nz = entity.getZ();
        double dx = nx - existing.getX(), dy = ny - existing.getY(), dz = nz - existing.getZ();
        boolean entityMoved = (dx * dx + dy * dy + dz * dz) > CLONE_MOVE_EPSILON_SQ;

        existing.setOutbox(outbox);
        try {
            existing.setName(getName(entity));
            if (entityMoved) existing.teleport(nx, ny, nz);
        } finally {
            existing.setOutbox(null);
        }
    }

    public static void removeDisplay(UUID viewerUUID, UUID entityUUID, List<Packet<? super ClientGamePacketListener>> outbox) {
        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) return;
        NametagCloneUtils clone = inner.remove(entityUUID);
        if (clone == null) return;
        despawnClone(clone, outbox);
    }

    public static void removeDisplay(UUID viewerUUID) {
        Map<UUID, NametagCloneUtils> inner = clones.remove(viewerUUID);
        if (inner == null) return;
        for (NametagCloneUtils clone : inner.values()) {
            if (clone == null) continue;
            despawnClone(clone, null);
        }
    }

    public static void removeDisplayForEntity(UUID entityUUID) {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner == null) continue;
            NametagCloneUtils clone = inner.remove(entityUUID);
            if (clone == null) continue;
            despawnClone(clone, null);
        }
    }

    public static void cleanupStaleClones(List<Packet<? super ClientGamePacketListener>> outbox, Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) return;
        inner.entrySet().removeIf(entry -> {
            Entity entity = org.bukkit.Bukkit.getEntity(entry.getKey());
            if (entity == null || !shouldShowFast(viewer, entity)) {
                despawnClone(entry.getValue(), outbox);
                return true;
            }
            return false;
        });
    }

    public static void removeAllDisplays() {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner == null) continue;
            for (NametagCloneUtils clone : inner.values()) {
                if (clone == null) continue;
                despawnClone(clone, null);
            }
        }
        clones.clear();
    }

    private static void despawnClone(NametagCloneUtils clone, List<Packet<? super ClientGamePacketListener>> outbox) {
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

    private static Component getName(Entity entity) {
        Component name;
        Component custom = entity.customName();
        name = (!(entity instanceof Player) && custom != null) ? custom : Component.text(entity.getName());
        NamedTextColor teamColor = TeamUtils.getTeamColor(entity);
        if (teamColor != null) name = name.color(teamColor);
        Component teamPrefix = TeamUtils.getTeamPrefix(entity);
        if (teamPrefix != null) name = teamPrefix.append(name);
        return name;
    }
}