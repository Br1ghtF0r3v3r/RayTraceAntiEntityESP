package RayTraceAntiEntityESP.engine;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.Main.plugin;

public class VisibilityManager {

    public static ConcurrentHashMap<UUID, Set<Integer>> hiddenEntities = new ConcurrentHashMap<>();

    public static void markHidden(Player player, int entityId) {
        hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(entityId);
    }

    public static void markNotHidden(Player player, int entityId) {
        Set<Integer> set = hiddenEntities.get(player.getUniqueId());
        if (set != null) {
            set.remove(entityId);
            if (set.isEmpty()) hiddenEntities.remove(player.getUniqueId());
        }
    }

    public static void setHidden(Player player, Entity entity) {
        hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(entity.getEntityId());
        player.hideEntity(plugin, entity);
    }

    public static void setNotHidden(Player player, Entity entity) {
        Set<Integer> set = hiddenEntities.get(player.getUniqueId());
        if (set != null) {
            set.remove(entity.getEntityId());
            if (set.isEmpty()) hiddenEntities.remove(player.getUniqueId());
        }
        EntityPacketFilter.bypassSet.add(player.getUniqueId() + ":" + entity.getEntityId());
        player.showEntity(plugin, entity);
    }

    public static boolean isHidden(Player player, int entityId) {
        Set<Integer> hiddenSet = hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        return hiddenSet.contains(entityId);
    }

}
