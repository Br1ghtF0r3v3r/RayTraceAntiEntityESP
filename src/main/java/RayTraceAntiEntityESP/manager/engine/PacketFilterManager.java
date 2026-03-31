package RayTraceAntiEntityESP.manager.engine;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

import static RayTraceAntiEntityESP.Main.plugin;

public class PacketFilterManager extends PacketListenerAbstract {

    public static final Set<String> bypassSet = Collections.synchronizedSet(new HashSet<>());
    public static String bypassKey(Player viewer, int entityId) { return viewer.getUniqueId() + ":" + entityId; }

    public static void packetFilter(PacketSendEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        Player viewer = event.getPlayer();

        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {

            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(event);
            int entityId = spawnPacket.getEntityId();

            if (viewer.getEntityId() == entityId) return;
            if (bypassSet.remove(bypassKey(viewer, entityId))) return;

            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = null;
                for (Entity e : viewer.getWorld().getEntities()) {
                    if (e.getEntityId() == entityId) {
                        entity = e;
                        break;
                    }
                }
                if (entity == null) return;

                if (RayTraceManager.isEntityVisible(viewer, entity)) {
                    VisibilityManager.setNotHidden(viewer, entity);
                } else {
                    VisibilityManager.setHidden(viewer, entity);
                }
            });
        }
        else if (packetType == PacketType.Play.Server.PLAYER_INFO_REMOVE) {

            WrapperPlayServerPlayerInfoRemove packet = new WrapperPlayServerPlayerInfoRemove(event);
            List<UUID> uuids = new ArrayList<>(packet.getProfileIds());

            uuids.removeIf(uuid -> {
                Player target = Bukkit.getPlayer(uuid);
                return target != null && !viewer.canSee(target);
            });
            if (uuids.size() != packet.getProfileIds().size()) {
                if (uuids.isEmpty()) {
                    event.setCancelled(true);
                }
                else {
                    event.setCancelled(true);
                    WrapperPlayServerPlayerInfoRemove newPacket = new WrapperPlayServerPlayerInfoRemove(uuids);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, newPacket);
                }
            }
        }
    }
}