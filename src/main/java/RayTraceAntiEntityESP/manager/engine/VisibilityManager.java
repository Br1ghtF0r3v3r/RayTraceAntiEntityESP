package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.utils.FakeNameDisplayUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.utils.FakeNameDisplayUtils.isNametextDisplayVisible;

public class VisibilityManager {

    public static void setHidden(Player viewer, Entity entity) {
        viewer.hideEntity(plugin, entity);

        boolean needsNameplate = isNametextDisplayVisible(viewer, entity);
        if (needsNameplate) {
            FakeNameDisplayUtils.applyFakeNameDisplay(viewer, entity);
        }
    }

    public static void setNotHidden(Player viewer, Entity entity) {
        viewer.hideEntity(plugin, entity);
        PacketFilterManager.bypassSet.add(PacketFilterManager.bypassKey(viewer, entity.getUniqueId()));
        viewer.showEntity(plugin, entity);

        FakeNameDisplayUtils.removeFakeNameDisplay(viewer, entity);
    }

}
