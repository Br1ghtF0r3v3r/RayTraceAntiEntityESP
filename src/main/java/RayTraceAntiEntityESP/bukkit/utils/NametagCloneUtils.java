package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapter;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.*;

public class NametagCloneUtils {

    private final Player viewer;
    private final int entityId;
    private final UUID entityUuid;
    private double x, y, z;
    private long lastX, lastY, lastZ;
    private boolean spawned;
    private Component customName;

    private List<Object> cachedNamedMetadata = null;
    private List<Object> outbox;

    public NametagCloneUtils(Player viewer) {
        this.viewer = viewer;
        this.entityId = PacketManager.allocateSyntheticEntityId();
        this.entityUuid = UUID.randomUUID();
    }

    private void send(Object packet) {
        if (outbox != null) {
            outbox.add(packet);
            return;
        }
        NmsAdapterFactory.get().sendPacket(viewer, packet);
    }

    private void sendAtomic(Object... packets) {
        if (outbox != null) {
            Collections.addAll(outbox, packets);
            return;
        }
        List<Object> list = new ArrayList<>(packets.length);
        list.addAll(Arrays.asList(packets));
        NmsAdapterFactory.get().sendBundled(viewer, list);
    }

    public double getX() {
        return x;
    }
    public double getY() {
        return y;
    }
    public double getZ() {
        return z;
    }
    public boolean isSpawned() {
        return spawned;
    }

    public void setOutbox(List<Object> outbox) { this.outbox = outbox; }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setName(Component name) {
        if (name != null && name.equals(this.customName)) return;
        this.customName = name;
        this.cachedNamedMetadata = null;
        if (spawned) {
            NmsAdapter adapter = NmsAdapterFactory.get();
            send(adapter.buildSetEntityDataPacket(entityId, buildMetadata()));
        }
    }

    public void spawn() {
        if (spawned) return;
        lastX = (long) (x * 4096);
        lastY = (long) (y * 4096);
        lastZ = (long) (z * 4096);
        NmsAdapter adapter = NmsAdapterFactory.get();
        sendAtomic(
                adapter.buildArmorStandSpawnPacket(entityId, entityUuid, x, y, z),
                adapter.buildSetEntityDataPacket(entityId, buildMetadata())
        );
        spawned = true;
    }

    public void teleport(double x, double y, double z) {
        if (!spawned) return;
        long nx = (long) (x * 4096), ny = (long) (y * 4096), nz = (long) (z * 4096);
        long dx = nx - lastX, dy = ny - lastY, dz = nz - lastZ;
        if (dx == 0 && dy == 0 && dz == 0) return;
        this.x = x;
        this.y = y;
        this.z = z;
        lastX = nx;
        lastY = ny;
        lastZ = nz;
        NmsAdapter adapter = NmsAdapterFactory.get();
        if (dx < -32768 || dx > 32767 || dy < -32768 || dy > 32767 || dz < -32768 || dz > 32767) {
            sendAtomic(
                    adapter.buildRemoveEntitiesPacket(entityId),
                    adapter.buildArmorStandSpawnPacket(entityId, entityUuid, x, y, z),
                    adapter.buildSetEntityDataPacket(entityId, buildMetadata()));
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
