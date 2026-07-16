package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapter;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import org.bukkit.entity.Player;

import java.util.List;

public final class DebugVertexUtils extends AbstractSyntheticEntity {

    private static final float SCALE = 0.05f;
    private static final String STATE_VISIBLE = "LIME_WOOL";
    private static final String STATE_OCCLUDED = "RED_WOOL";

    private Object currentBlockState;
    private float currentScale = -1f;

    public DebugVertexUtils(Player viewer) {
        super(viewer);
    }

    public void spawn(boolean vertexVisible) {
        if (spawned) return;
        NmsAdapter adapter = NmsAdapterFactory.get();
        currentBlockState = adapter.blockStateForName(vertexVisible ? STATE_VISIBLE : STATE_OCCLUDED);
        currentScale = SCALE;
        sendBundle(
                adapter.buildBlockDisplaySpawnPacket(entityId, entityUuid, x, y, z),
                adapter.buildSetEntityDataPacket(entityId, buildMetadata())
        );
        markSpawned();
    }

    public void updateMeta(boolean vertexVisible) {
        if (!spawned) return;
        NmsAdapter adapter = NmsAdapterFactory.get();
        Object blockState = adapter.blockStateForName(vertexVisible ? STATE_VISIBLE : STATE_OCCLUDED);
        if (blockState == currentBlockState && SCALE == currentScale) return;
        currentBlockState = blockState;
        currentScale = SCALE;
        send(adapter.buildSetEntityDataPacket(entityId, buildMetadata()));
    }

    @Override
    protected void respawnAt(double x, double y, double z) {
        NmsAdapter adapter = NmsAdapterFactory.get();
        sendBundle(
                adapter.buildRemoveEntitiesPacket(entityId),
                adapter.buildBlockDisplaySpawnPacket(entityId, entityUuid, x, y, z),
                adapter.buildSetEntityDataPacket(entityId, buildMetadata())
        );
    }

    private List<Object> buildMetadata() {
        return NmsAdapterFactory.get().buildBlockDisplayMetadata(
                currentBlockState, SCALE, (int) Config.checkingPeriodTicks + 1);
    }
}
