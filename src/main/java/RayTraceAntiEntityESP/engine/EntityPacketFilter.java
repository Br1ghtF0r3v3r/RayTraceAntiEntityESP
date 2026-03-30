package RayTraceAntiEntityESP.engine;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static RayTraceAntiEntityESP.Main.plugin;

public class EntityPacketFilter extends PacketListenerAbstract {

    public static final Set<String> bypassSet = Collections.synchronizedSet(new HashSet<>());
    public static String bypassKey(Player viewer, int entityId) { return viewer.getUniqueId() + ":" + entityId; }

    public static void entityPacketFilter(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SPAWN_ENTITY) return;

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(event);
        Player viewer = event.getPlayer();
        int entityId = spawnPacket.getEntityId();

        if (viewer.getEntityId() == entityId) return;
        if (!EntityTypes.isTypeInstanceOf(spawnPacket.getEntityType(), EntityTypes.LIVINGENTITY)) return;
        if (bypassSet.remove(bypassKey(viewer, entityId))) return;

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            LivingEntity entity = viewer.getWorld().getEntities().stream()
                    .filter(e -> e.getEntityId() == entityId && e instanceof LivingEntity)
                    .map(e -> (LivingEntity) e)
                    .findFirst().orElse(null);
            if (entity == null) return;

            if (RayTraceManager.isEntityVisible(viewer, entity)) {
                VisibilityManager.setNotHidden(viewer, entity);
            } else {
                VisibilityManager.setHidden(viewer, entity);
            }
        });
    }
}