package RayTraceAntiEntityESP.bukkit.utils;

import net.minecraft.network.protocol.game.*;
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
    private long lastX, lastY, lastZ;
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

        lastX = (long) (x * 4096);
        lastY = (long) (y * 4096);
        lastZ = (long) (z * 4096);

        send(new ClientboundBundlePacket(List.of(
                new ClientboundAddEntityPacket(entityId, entityUuid, x, y, z, 0f, 0f,
                        EntityType.BLOCK_DISPLAY, 0, Vec3.ZERO, 0.0),
                new ClientboundSetEntityDataPacket(entityId, buildMetadata(blockState))
        )));
        spawned = true;
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

        long newX = (long) (x * 4096);
        long newY = (long) (y * 4096);
        long newZ = (long) (z * 4096);

        long dx = newX - lastX;
        long dy = newY - lastY;
        long dz = newZ - lastZ;

        if (dx == 0 && dy == 0 && dz == 0) return;

        this.x = x;
        this.y = y;
        this.z = z;
        this.lastX = newX;
        this.lastY = newY;
        this.lastZ = newZ;

        if (dx < -32768 || dx > 32767 || dy < -32768 || dy > 32767 || dz < -32768 || dz > 32767) {
            send(new ClientboundBundlePacket(List.of(
                    new ClientboundRemoveEntitiesPacket(entityId),
                    new ClientboundAddEntityPacket(entityId, entityUuid, x, y, z, 0f, 0f, EntityType.BLOCK_DISPLAY, 0, Vec3.ZERO, 0.0),
                    new ClientboundSetEntityDataPacket(entityId, buildMetadata(currentBlockState))
            )));
            return;
        }

        send(new ClientboundMoveEntityPacket.Pos(entityId, (short) dx, (short) dy, (short) dz, true));
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