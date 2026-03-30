package RayTraceAntiEntityESP.manager.engine;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.Main.plugin;

public class VisibilityManager {

    public static void setHidden(Player player, LivingEntity entity) {
        player.hideEntity(plugin, entity);
        FakeNameDisplayManager.applyFakeNameplate(player, entity, true);
    }

    public static void setNotHidden(Player player, LivingEntity entity) {
        player.hideEntity(plugin, entity);
        PacketFilterManager.bypassSet.add(PacketFilterManager.bypassKey(player, entity.getEntityId()));
        player.showEntity(plugin, entity);
        FakeNameDisplayManager.removeNameplate(player, entity);
    }

}
