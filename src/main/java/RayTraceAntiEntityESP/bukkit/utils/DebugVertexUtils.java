package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapter;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DebugVertexUtils {

    private static final float SCALE = 0.05f;

    private final Player viewer;
    private final int entityId;
    private final UUID entityUuid;
    private double x, y, z;
    private long lastX, lastY, lastZ;
    private boolean spawned;

    private Object currentBlockState = null;
    private float currentScale = -1;

    public DebugVertexUtils(Player viewer) {
        this.viewer = viewer;
        this.entityId = PacketManager.allocateSyntheticEntityId();
        this.entityUuid = UUID.randomUUID();
    }

    private void send(Object packet) {
        NmsAdapterFactory.get().sendPacket(viewer, packet);
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @SuppressWarnings("unused")
    public boolean isSpawned() { return spawned; }

    public void spawn(boolean vertexVisible) {
        if (spawned) return;
        NmsAdapter adapter = NmsAdapterFactory.get();
        Object blockState = adapter.blockStateForName(vertexVisible ? "LIME_WOOL" : "RED_WOOL");
        currentBlockState = blockState;
        currentScale = SCALE;

        lastX = (long) (x * 4096);
        lastY = (long) (y * 4096);
        lastZ = (long) (z * 4096);

        List<Object> bundle = new ArrayList<>(2);
        bundle.add(adapter.buildBlockDisplaySpawnPacket(entityId, entityUuid, x, y, z));
        bundle.add(adapter.buildSetEntityDataPacket(entityId,
                adapter.buildBlockDisplayMetadata(blockState, SCALE,
                        (int) Config.checkingPeriodTicks + 1)));
        adapter.sendBundled(viewer, bundle);
        spawned = true;
    }

    public void updateMeta(boolean vertexVisible) {
        if (!spawned) return;
        NmsAdapter adapter = NmsAdapterFactory.get();
        Object blockState = adapter.blockStateForName(vertexVisible ? "LIME_WOOL" : "RED_WOOL");
        if (blockState == currentBlockState && SCALE == currentScale) return;
        currentBlockState = blockState;
        currentScale = SCALE;
        send(adapter.buildSetEntityDataPacket(entityId,
                adapter.buildBlockDisplayMetadata(blockState, SCALE,
                        (int) Config.checkingPeriodTicks + 1)));
    }

    public void teleport(double x, double y, double z) {
        if (!spawned) return;
        long newX = (long) (x * 4096);
        long newY = (long) (y * 4096);
        long newZ = (long) (z * 4096);
        long dx = newX - lastX, dy = newY - lastY, dz = newZ - lastZ;
        if (dx == 0 && dy == 0 && dz == 0) return;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastX = newX;
        this.lastY = newY;
        this.lastZ = newZ;

        NmsAdapter adapter = NmsAdapterFactory.get();
        if (dx < -32768 || dx > 32767 || dy < -32768 || dy > 32767 || dz < -32768 || dz > 32767) {
            List<Object> bundle = new ArrayList<>(3);
            bundle.add(adapter.buildRemoveEntitiesPacket(entityId));
            bundle.add(adapter.buildBlockDisplaySpawnPacket(entityId, entityUuid, x, y, z));
            bundle.add(adapter.buildSetEntityDataPacket(entityId,
                    adapter.buildBlockDisplayMetadata(currentBlockState, SCALE,
                            (int) Config.checkingPeriodTicks + 1)));
            adapter.sendBundled(viewer, bundle);
            return;
        }
        send(adapter.buildMoveEntityPacket(entityId, (short) dx, (short) dy, (short) dz, true));
    }

    public void despawn() {
        if (!spawned) return;
        send(NmsAdapterFactory.get().buildRemoveEntitiesPacket(entityId));
        spawned = false;
        PacketManager.unregisterSyntheticEntity(entityId);
    }
}
