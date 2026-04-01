package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.utils.FakeNameDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.manager.engine.PacketFilterManager.addPacketBypass;

public class VisibilityManager {

    public static void setHidden(Player viewer, Entity entity) {
        viewer.hideEntity(plugin, entity);

        FakeNameDisplay.applyDisplay(viewer, entity);
    }

    public static void setNotHidden(Player viewer, Entity entity) {
        viewer.hideEntity(plugin, entity);
        addPacketBypass(viewer, entity.getUniqueId());
        viewer.showEntity(plugin, entity);

        FakeNameDisplay.removeDisplay(viewer, entity);
    }

}
