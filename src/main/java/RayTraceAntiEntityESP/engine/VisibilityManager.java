package RayTraceAntiEntityESP.engine;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;

public class VisibilityManager {
    public static void setHidden(Player player, LivingEntity entity) {
        player.hideEntity(plugin, entity);
    }

    public static void setNotHidden(Player player, LivingEntity entity) {
        player.hideEntity(plugin, entity);
        EntityPacketFilter.bypassSet.add(EntityPacketFilter.bypassKey(player, entity.getEntityId()));
        player.showEntity(plugin, entity);
    }
}
