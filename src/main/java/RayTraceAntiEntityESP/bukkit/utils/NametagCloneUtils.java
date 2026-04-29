package RayTraceAntiEntityESP.bukkit.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
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
    private boolean spawned;

    private Component customName;

    public NametagCloneUtils(Player viewer) {
        this.viewer = viewer;
        this.entityId = ThreadLocalRandom.current().nextInt(ID_MIN, ID_MAX);
        this.entityUuid = UUID.randomUUID();
    }

    public void setName(Component name) {
        this.customName = name;
        if (spawned) sendFullMeta();
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

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(entityUuid),
                EntityTypes.ARMOR_STAND,
                new Vector3d(x, y, z),
                0f, 0f, 0f, 0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
        sendFullMeta();
        spawned = true;
    }

    public void sendFullMeta() {
        List<EntityData<?>> metadata = new ArrayList<>();

        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20));

        if (customName != null) {
            metadata.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(customName)));
            metadata.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, true));
        }

        metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x19));

        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metaPacket);
    }

    public void teleport(double x, double y, double z) {
        if (!spawned) return;
        this.x = x;
        this.y = y;
        this.z = z;

        WrapperPlayServerEntityTeleport tpPacket = new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(x, y, z),
                0f, // yaw
                0f, // pitch
                false // onGround
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, tpPacket);
    }

    public void despawn() {
        if (!spawned) return;
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
        spawned = false;
    }
}