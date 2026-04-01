package RayTraceAntiEntityESP.utils;

import RayTraceAntiEntityESP.manager.engine.PacketFilterManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.Main.plugin;

public class VisibilityUtils {

    public static void setHidden(Player player, Entity entity) {
        PacketFilterManager.bypassPacketSet.add(PacketFilterManager.bypassHiddenKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);

        FakeNameDisplay.applyDisplay(player, entity);
    }

    public static void setNotHidden(Player player, Entity entity) {
        PacketFilterManager.bypassPacketSet.add(PacketFilterManager.bypassShowKey(player, entity.getUniqueId()));
        player.showEntity(plugin, entity);

        FakeNameDisplay.removeDisplay(player, entity);
    }
}
