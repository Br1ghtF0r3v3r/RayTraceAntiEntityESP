package RayTraceAntiEntityESP.bukkit.utils;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
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

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                entityId, entityUuid,
                x, y, z,
                0f, 0f,
                EntityType.ARMOR_STAND,
                0,
                Vec3.ZERO,
                0.0
        );

        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(entityId, buildMetadata());

        send(new ClientboundBundlePacket(List.of(spawnPacket, metaPacket)));
        spawned = true;
    }

    public void sendFullMeta() {
        send(new ClientboundSetEntityDataPacket(entityId, buildMetadata()));
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
                        EntityType.ARMOR_STAND,
                        0,
                        Vec3.ZERO,
                        0.0
                ),
                new ClientboundSetEntityDataPacket(entityId, buildMetadata())
        )));
    }

    public void despawn() {
        if (!spawned) return;
        send(new ClientboundRemoveEntitiesPacket(entityId));
        spawned = false;
    }
}