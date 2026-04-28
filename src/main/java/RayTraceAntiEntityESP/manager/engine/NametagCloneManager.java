package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.config.Config;
import RayTraceAntiEntityESP.utils.NametagCloneUtils;
import RayTraceAntiEntityESP.utils.TeamUtils;
import RayTraceAntiEntityESP.utils.VisibilityUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.Main.plugin;

public class NametagCloneManager {

    private static final Map<UUID, Map<UUID, NametagCloneUtils>> clones = new ConcurrentHashMap<>();

    public static boolean shouldHide(Player viewer, Entity entity) {
        boolean isSelf = viewer.equals(entity);
        boolean nameNotVisible = !VisibilityUtils.isNameVisible(viewer, entity);
        boolean isInvisible = entity.isInvisible();
        boolean isSneaking = (entity instanceof Player player && player.isSneaking());
        return isSelf || nameNotVisible || isInvisible || isSneaking;
    }

    public static void applyDisplay(Player viewer, Entity entity) {
        if (shouldHide(viewer, entity)) {
            removeDisplay(viewer, entity);
            return;
        }

        Map<UUID, NametagCloneUtils> inner = clones.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
        NametagCloneUtils existing = inner.get(entity.getUniqueId());
        if (existing != null) {
            updatePosition(existing, entity);
        } else {
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
    }

    public static void updatePosition(NametagCloneUtils clone, Entity entity) {
        clone.teleport(entity.getX(), entity.getLocation().add(0, entity.getHeight() + Config.displayNameOffSetY, 0).getY() + Config.displayNameOffSetY, entity.getZ());
    }

    public static void removeDisplay(Player viewer, Entity entity) {
        Map<UUID, NametagCloneUtils> inner = clones.get(viewer.getUniqueId());
        if (inner == null) return;
        NametagCloneUtils clone = inner.remove(entity.getUniqueId());
        if (clone == null) return;
        despawnClone(clone);
    }

    public static void removeDisplay(Player viewer) {
        Map<UUID, NametagCloneUtils> inner = clones.remove(viewer.getUniqueId());
        if (inner == null) return;
        for (NametagCloneUtils clone : inner.values()) {
            if (clone == null) continue;
            despawnClone(clone);
        }
    }

    public static void removeDisplayForEntity(Entity entity) {
        for (Map<UUID, NametagCloneUtils> inner : clones.values()) {
            if (inner == null) continue;
            NametagCloneUtils clone = inner.remove(entity.getUniqueId());
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
        if (entity instanceof Player) {
            name = Component.text(entity.getName());
        } else if (entity.customName() != null) {
            name = entity.customName();
        } else {
            name = Component.text(entity.getName());
        }
        NamedTextColor teamColor = TeamUtils.getTeamColor(entity);
        if (teamColor != null && name != null) name = name.color(teamColor);
        Component teamPrefix = TeamUtils.getTeamPrefix(entity);
        if (teamPrefix != null && name != null) name = teamPrefix.append(name);
        return name;
    }
}