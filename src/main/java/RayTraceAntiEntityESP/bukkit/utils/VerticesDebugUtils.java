package RayTraceAntiEntityESP.bukkit.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VerticesDebugUtils {

    private static final int ID_MIN = 4_000_000;
    private static final int ID_MAX = 5_000_000;

    public static final int BLOCK_STATE_VISIBLE = StateTypes.LIME_WOOL.createBlockState().getGlobalId();
    public static final int BLOCK_STATE_NOT_VISIBLE = StateTypes.RED_WOOL.createBlockState().getGlobalId();
    public static float SCALE = 0.05f;

    private final Player viewer;
    private final int entityId;
    private final UUID entityUuid;
    private double x, y, z;
    private boolean spawned;

    private int currentBlockState = -1;
    private float currentScale = -1;

    public VerticesDebugUtils(Player viewer) {
        this.viewer = viewer;
        this.entityId = ThreadLocalRandom.current().nextInt(ID_MIN, ID_MAX);
        this.entityUuid = UUID.randomUUID();
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void spawn(boolean vertexVisible) {
        if (spawned) return;

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(entityUuid),
                EntityTypes.BLOCK_DISPLAY,
                new Vector3d(x, y, z),
                0f, 0f, 0f, 0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
        sendFullMeta(vertexVisible);
        spawned = true;
    }

    public void sendFullMeta(boolean vertexVisible) {
        int blockState = vertexVisible ? BLOCK_STATE_VISIBLE : BLOCK_STATE_NOT_VISIBLE;
        currentBlockState = blockState;
        currentScale = SCALE;

        List<EntityData<?>> metadata = new ArrayList<>();

        metadata.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(SCALE, SCALE, SCALE)));
        metadata.add(new EntityData<>(23, EntityDataTypes.BLOCK_STATE, blockState));

        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metaPacket);
    }

    public void updateMeta(boolean vertexVisible) {
        if (!spawned) return;
        int blockState = vertexVisible ? BLOCK_STATE_VISIBLE : BLOCK_STATE_NOT_VISIBLE;
        float scale = SCALE;

        if (blockState == currentBlockState && scale == currentScale) return;
        currentBlockState = blockState;
        currentScale = scale;

        List<EntityData<?>> metadata = new ArrayList<>();

        metadata.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(SCALE, SCALE, SCALE)));
        metadata.add(new EntityData<>(23, EntityDataTypes.BLOCK_STATE, blockState));

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