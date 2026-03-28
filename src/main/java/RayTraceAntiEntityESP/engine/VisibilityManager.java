package RayTraceAntiEntityESP.engine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;

public class VisibilityManager {
    public static final VisibilityManager INSTANCE = new VisibilityManager();

    private final Map<UUID, Set<Integer>> hiddenEntities = new HashMap<>();

    private static BukkitTask task;

    //  | client state | server state | action         |
    //  |--------------|--------------|----------------|
    //  | visible      | visible      | nothing        |
    //  | visible      | not visible  | destroy packet |
    //  | not visible  | visible      | spawn packet   |
    //  | not visible  | not visible  | nothing        |

    public void setHidden(Player player, int entityId, boolean hidden) {
        UUID uuid = player.getUniqueId();

        if (hidden) {
            hiddenEntities.computeIfAbsent(uuid, k -> new HashSet<>())
                    .add(entityId);
        } else {
            Set<Integer> set = hiddenEntities.get(uuid);
            if (set != null) {
                set.remove(entityId);
                if (set.isEmpty()) {
                    hiddenEntities.remove(uuid);
                }
            }
        }
    }

    public boolean isHidden(Player player, int targetId) {
        Set<Integer> hiddenSet = hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        return hiddenSet.contains(targetId);
    }

    public void update(Player player, Entity target, boolean visibleServer) {
        Set<Integer> hiddenSet = hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        int id = target.getEntityId();

        boolean visibleClient = !hiddenSet.contains(id);

        if (visibleServer && !visibleClient) {
            EntityPacketHandler.sendSpawnEntityPacket(player, target);
            hiddenSet.remove(id);

        } else if (!visibleServer && visibleClient) {
            EntityPacketHandler.sendDestroyEntityPacket(player, target);
            hiddenSet.add(id);
        }
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();

            for (Player player : players) {
                Collection<LivingEntity> entities = player.getWorld().getLivingEntities();
                for (LivingEntity entity : entities) {
                    if (entity == player) continue;
                    VisibilityManager.INSTANCE.update(player, entity, RaycastUtils.isEntityVisible(player, entity));
                }
            }

        }, 0L, 1);
    }
}
