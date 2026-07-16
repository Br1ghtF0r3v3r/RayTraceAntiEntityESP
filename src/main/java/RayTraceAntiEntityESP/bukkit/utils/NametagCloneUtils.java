package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.nms.NmsAdapter;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

public final class NametagCloneUtils extends AbstractSyntheticEntity {

    private Component customName;
    private List<Object> cachedNamedMetadata;

    public NametagCloneUtils(Player viewer) {
        super(viewer);
    }

    public void setName(Component name) {
        if (name != null && name.equals(this.customName)) return;
        this.customName = name;
        this.cachedNamedMetadata = null;
        if (spawned) {
            send(NmsAdapterFactory.get().buildSetEntityDataPacket(entityId, buildMetadata()));
        }
    }

    public void spawn() {
        if (spawned) return;
        NmsAdapter adapter = NmsAdapterFactory.get();
        sendBundle(
                adapter.buildArmorStandSpawnPacket(entityId, entityUuid, x, y, z),
                adapter.buildSetEntityDataPacket(entityId, buildMetadata())
        );
        markSpawned();
    }

    @Override
    protected void respawnAt(double x, double y, double z) {
        NmsAdapter adapter = NmsAdapterFactory.get();
        sendBundle(
                adapter.buildRemoveEntitiesPacket(entityId),
                adapter.buildArmorStandSpawnPacket(entityId, entityUuid, x, y, z),
                adapter.buildSetEntityDataPacket(entityId, buildMetadata())
        );
    }

    private List<Object> buildMetadata() {
        if (customName == null) {
            return NmsAdapterFactory.get().buildArmorStandMetadata(null);
        }
        if (cachedNamedMetadata == null) {
            cachedNamedMetadata = NmsAdapterFactory.get().buildArmorStandMetadata(customName);
        }
        return cachedNamedMetadata;
    }
}
