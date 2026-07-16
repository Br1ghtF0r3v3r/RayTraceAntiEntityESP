package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapter;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class AbstractSyntheticEntity {

    protected static final double FIXED_POINT = 4096.0;
    protected static final short SHORT_MIN = -32768;
    protected static final short SHORT_MAX = 32767;

    protected final Player viewer;
    protected final int entityId;
    protected final UUID entityUuid;

    protected double x, y, z;
    protected long lastX, lastY, lastZ;
    protected boolean spawned;

    protected List<Object> outbox;

    protected AbstractSyntheticEntity(Player viewer) {
        this.viewer = viewer;
        this.entityId = PacketManager.allocateSyntheticEntityId();
        this.entityUuid = UUID.randomUUID();
    }

    public final Player getViewer() { return viewer; }
    public final int getEntityId() { return entityId; }
    public final UUID getEntityUuid() { return entityUuid; }

    public final double getX() { return x; }
    public final double getY() { return y; }
    public final double getZ() { return z; }
    public final boolean isSpawned() { return spawned; }

    public final void setOutbox(List<Object> outbox) { this.outbox = outbox; }

    public final void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    protected final void send(Object packet) {
        if (outbox != null) {
            outbox.add(packet);
            return;
        }
        NmsAdapterFactory.get().sendPacket(viewer, packet);
    }

    protected final void sendBundle(Object... packets) {
        if (outbox != null) {
            Collections.addAll(outbox, packets);
            return;
        }
        NmsAdapterFactory.get().sendBundled(viewer, Arrays.asList(packets));
    }

    protected final void markSpawned() {
        lastX = (long) (x * FIXED_POINT);
        lastY = (long) (y * FIXED_POINT);
        lastZ = (long) (z * FIXED_POINT);
        spawned = true;
    }

    public final void teleport(double x, double y, double z) {
        if (!spawned) return;
        long nx = (long) (x * FIXED_POINT);
        long ny = (long) (y * FIXED_POINT);
        long nz = (long) (z * FIXED_POINT);
        long dx = nx - lastX, dy = ny - lastY, dz = nz - lastZ;
        if (dx == 0 && dy == 0 && dz == 0) return;
        this.x = x;
        this.y = y;
        this.z = z;
        lastX = nx;
        lastY = ny;
        lastZ = nz;
        if (dx < SHORT_MIN || dx > SHORT_MAX || dy < SHORT_MIN || dy > SHORT_MAX
                || dz < SHORT_MIN || dz > SHORT_MAX) {
            respawnAt(x, y, z);
            return;
        }
        NmsAdapter adapter = NmsAdapterFactory.get();
        send(adapter.buildMoveEntityPacket(entityId, (short) dx, (short) dy, (short) dz, true));
    }

    public final void despawn() {
        if (!spawned) return;
        send(NmsAdapterFactory.get().buildRemoveEntitiesPacket(entityId));
        spawned = false;
        PacketManager.unregisterSyntheticEntity(entityId);
    }

    protected abstract void respawnAt(double x, double y, double z);
}
