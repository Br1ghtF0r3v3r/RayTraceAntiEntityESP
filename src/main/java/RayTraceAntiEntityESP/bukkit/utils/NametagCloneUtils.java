package RayTraceAntiEntityESP.bukkit.utils;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class NametagCloneUtils {

    private static final int ID_MIN = 2_000_000;
    private static final int ID_MAX = 3_000_000;

    private final Player viewer;
    private final int entityId;
    private final UUID entityUuid;
    private double x, y, z;
    private long lastX, lastY, lastZ;
    private boolean spawned;

    private Component customName;

    private void send(net.minecraft.network.protocol.Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }

    private List<SynchedEntityData.DataValue<?>> buildMetadata() {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();

        metadata.add(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x20));

        if (customName != null) {
            metadata.add(new SynchedEntityData.DataValue<>(2, EntityDataSerializers.OPTIONAL_COMPONENT,
                    Optional.of(PaperAdventure.asVanilla(customName))));
            metadata.add(new SynchedEntityData.DataValue<>(3, EntityDataSerializers.BOOLEAN, true));
        }

        metadata.add(new SynchedEntityData.DataValue<>(5, EntityDataSerializers.BOOLEAN, true));
        metadata.add(new SynchedEntityData.DataValue<>(15, EntityDataSerializers.BYTE, (byte) 0x19));

        return metadata;
    }

    public NametagCloneUtils(Player viewer) {
        this.viewer = viewer;
        this.entityId = ThreadLocalRandom.current().nextInt(ID_MIN, ID_MAX);
        this.entityUuid = UUID.randomUUID();
    }

    public void setName(Component name) {
        this.customName = name;
        if (spawned) send(new ClientboundSetEntityDataPacket(entityId, buildMetadata()));
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void spawn() {
        if (spawned) return;

        lastX = (long) (x * 4096);
        lastY = (long) (y * 4096);
        lastZ = (long) (z * 4096);

        send(new ClientboundBundlePacket(List.of(
                new ClientboundAddEntityPacket(entityId, entityUuid, x, y, z, 0f, 0f,
                        EntityType.ARMOR_STAND, 0, Vec3.ZERO, 0.0),
                new ClientboundSetEntityDataPacket(entityId, buildMetadata())
        )));
        spawned = true;
    }

    public void teleport(double x, double y, double z) {
        if (!spawned) return;

        long newX = (long) (x * 4096);
        long newY = (long) (y * 4096);
        long newZ = (long) (z * 4096);

        long dx = newX - lastX;
        long dy = newY - lastY;
        long dz = newZ - lastZ;

        this.x = x;
        this.y = y;
        this.z = z;
        this.lastX = newX;
        this.lastY = newY;
        this.lastZ = newZ;

        if (dx < -32768 || dx > 32767 || dy < -32768 || dy > 32767 || dz < -32768 || dz > 32767) {
            send(new ClientboundBundlePacket(List.of(
                    new ClientboundRemoveEntitiesPacket(entityId),
                    new ClientboundAddEntityPacket(entityId, entityUuid, x, y, z, 0f, 0f, EntityType.ARMOR_STAND, 0, Vec3.ZERO, 0.0),
                    new ClientboundSetEntityDataPacket(entityId, buildMetadata())
            )));
            lastX = newX;
            lastY = newY;
            lastZ = newZ;
            return;
        }

        send(new ClientboundMoveEntityPacket.Pos(entityId, (short) dx, (short) dy, (short) dz, true));
    }

    public void despawn() {
        if (!spawned) return;
        send(new ClientboundRemoveEntitiesPacket(entityId));
        spawned = false;
    }
}