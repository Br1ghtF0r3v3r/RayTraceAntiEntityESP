package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.utils.FakeNameDisplayUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.Main.plugin;

public class VisibilityManager {

    public static void setHidden(Player player, Entity entity) {
        player.hideEntity(plugin, entity);
        FakeNameDisplayUtils.applyFakeNameplate(player, entity, true);
    }

    public static void setNotHidden(Player player, Entity entity) {
        player.hideEntity(plugin, entity);
        PacketFilterManager.bypassSet.add(PacketFilterManager.bypassKey(player, entity.getEntityId()));
        player.showEntity(plugin, entity);
        FakeNameDisplayUtils.removeNameplate(player, entity);
    }

}
