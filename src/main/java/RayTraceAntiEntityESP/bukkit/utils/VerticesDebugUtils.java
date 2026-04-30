package RayTraceAntiEntityESP.bukkit.utils;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VerticesDebugUtils {

    private static final int ID_MIN = 4_000_000;
    private static final int ID_MAX = 5_000_000;

    private static final BlockState BLOCK_STATE_VISIBLE = Blocks.LIME_WOOL.defaultBlockState();
    private static final BlockState BLOCK_STATE_NOT_VISIBLE = Blocks.RED_WOOL.defaultBlockState();
    private static final float SCALE = 0.05f;

    private final Player viewer;
    private final int entityId;
    private final UUID entityUuid;
    private double x, y, z;
    private boolean spawned;

    private BlockState currentBlockState = null;
    private float currentScale = -1;

    public VerticesDebugUtils(Player viewer) {
        this.viewer = viewer;
        this.entityId = ThreadLocalRandom.current().nextInt(ID_MIN, ID_MAX);
        this.entityUuid = UUID.randomUUID();
    }

    private void send(net.minecraft.network.protocol.Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void spawn(boolean vertexVisible) {
        if (spawned) return;

        BlockState blockState = vertexVisible ? BLOCK_STATE_VISIBLE : BLOCK_STATE_NOT_VISIBLE;
        currentBlockState = blockState;
        currentScale = SCALE;

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                entityId, entityUuid,
                x, y, z,
                0f, 0f,
                EntityType.BLOCK_DISPLAY,
                0,
                Vec3.ZERO,
                0.0
        );

        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(entityId, buildMetadata(blockState));

        send(new ClientboundBundlePacket(List.of(spawnPacket, metaPacket)));
        spawned = true;
    }

    public void sendFullMeta(boolean vertexVisible) {
        BlockState blockState = vertexVisible ? BLOCK_STATE_VISIBLE : BLOCK_STATE_NOT_VISIBLE;
        currentBlockState = blockState;
        currentScale = SCALE;

        send(new ClientboundSetEntityDataPacket(entityId, buildMetadata(blockState)));
    }

    public void updateMeta(boolean vertexVisible) {
        if (!spawned) return;

        BlockState blockState = vertexVisible ? BLOCK_STATE_VISIBLE : BLOCK_STATE_NOT_VISIBLE;

        if (blockState == currentBlockState && SCALE == currentScale) return;

        currentBlockState = blockState;
        currentScale = SCALE;

        send(new ClientboundSetEntityDataPacket(entityId, buildMetadata(blockState)));
    }

    public void teleport(double x, double y, double z) {
        if (!spawned) return;
        this.x = x;
        this.y = y;
        this.z = z;

        send(new ClientboundBundlePacket(List.of(
                new ClientboundRemoveEntitiesPacket(entityId),
                new ClientboundAddEntityPacket(
                        entityId, entityUuid,
                        x, y, z,
                        0f, 0f,
                        EntityType.BLOCK_DISPLAY,
                        0,
                        Vec3.ZERO,
                        0.0
                ),
                new ClientboundSetEntityDataPacket(entityId, buildMetadata(currentBlockState))
        )));
    }

    public void despawn() {
        if (!spawned) return;
        send(new ClientboundRemoveEntitiesPacket(entityId));
        spawned = false;
    }

    private List<SynchedEntityData.DataValue<?>> buildMetadata(BlockState blockState) {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        // index 12 — scale (Display entity)
        metadata.add(new SynchedEntityData.DataValue<>(12, EntityDataSerializers.VECTOR3, new Vector3f(SCALE, SCALE, SCALE)));
        // index 23 — block state (BlockDisplay entity)
        metadata.add(new SynchedEntityData.DataValue<>(23, EntityDataSerializers.BLOCK_STATE, blockState));
        return metadata;
    }
}