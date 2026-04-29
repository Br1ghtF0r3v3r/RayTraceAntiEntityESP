package RayTraceAntiEntityESP.bukkit.manager.engine;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.utils.NametagCloneUtils;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class NametagCloneManager {

    private static final Map<UUID, Map<UUID, NametagCloneUtils>> clones = new ConcurrentHashMap<>();

    public static boolean shouldShow(Player viewer, Entity entity) {
        if (viewer.canSee(entity)) return false;
        if (entity.isInvisible()) return false;
        if (entity instanceof Player player && player.isSneaking()) return false;

        return VisibilityUtils.isNameVisible(viewer, entity);
    }

    public static void applyDisplay(Player viewer, Entity entity) {
        if (!shouldShow(viewer, entity)) {
            removeDisplay(viewer.getUniqueId(), entity.getUniqueId());
            return;
        }
        Map<UUID, NametagCloneUtils> inner = clones.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
        NametagCloneUtils existing = inner.get(entity.getUniqueId());
        if (existing != null) {
            if (!existing.isSpawned()) {
                inner.remove(entity.getUniqueId());
                despawnClone(existing);
            } else {
                updatePosition(existing, entity);
                return;
            }
        }
        try {
            NametagCloneUtils clone = new NametagCloneUtils(viewer);
            clone.setName(getName(entity));
            clone.setPos(entity.getX(), entity.getLocation().add(0, entity.getHeight() + Config.displayNameOffSetY, 0).getY() + Config.displayNameOffSetY, entity.getZ());
            clone.spawn();
            inner.put(entity.getUniqueId(), clone);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn display for " + viewer.getName() + " -> " + entity.getName() + ": " + t);
        }
    }

    public static void updatePosition(NametagCloneUtils clone, Entity entity) {
        clone.teleport(entity.getX(), entity.getLocation().add(0, entity.getHeight() + Config.displayNameOffSetY, 0).getY() + Config.displayNameOffSetY, entity.getZ());
    }

    public static void removeDisplay(UUID viewerUUID, UUID entityUUID) {
        Map<UUID, NametagCloneUtils> inner = clones.get(viewerUUID);
        if (inner == null) return;
        NametagCloneUtils clone = inner.remove(entityUUID);
        if (clone == null) return;
        despawnClone(clone);
    }

    public static void removeDisplay(UUID viewerUUID) {
        Map<UUID, NametagCloneUtils> inner = clones.remove(viewerUUID);
        if (inner == null) return;
        for (NametagCloneUtils clone : inner.values()) {
            if (clone == null) continue;
            despawnClone(clone);
        }
    }

    public static void removeDisplayForEntity(UUID entityUUID) {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner == null) continue;
            NametagCloneUtils clone = inner.remove(entityUUID);
            if (clone == null) continue;
            despawnClone(clone);
        }
    }

    public static void removeAllDisplays() {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner == null) continue;
            for (NametagCloneUtils clone : inner.values()) {
                if (clone == null) continue;
                despawnClone(clone);
            }
        }
        clones.clear();
    }

    private static void despawnClone(NametagCloneUtils clone) {
        if (clone == null) return;
        try {
            clone.despawn();
        } catch (Throwable ignored) {}
    }

    public static Component getName(Entity entity) {
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