package RayTraceAntiEntityESP.engine;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class EntityPacketHandler {

    public static void sendDestroyEntityPacket(Player player, Entity target) {
        int id = target.getEntityId();
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(id);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyPacket);
    }

    public static void sendSpawnEntityPacket(Player player, Entity target) {
        WrapperPlayServerSpawnEntity spawnEntityPacket = new WrapperPlayServerSpawnEntity(
                target.getEntityId(),
                target.getUniqueId(),
                SpigotConversionUtil.fromBukkitEntityType(target.getType()),
                SpigotConversionUtil.fromBukkitLocation(target.getLocation()),
                target.getLocation().getYaw(),
                0, null
        );

        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(
                target.getEntityId(),
                SpigotConversionUtil.getEntityMetadata(target)
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnEntityPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, meta);
    }
}
