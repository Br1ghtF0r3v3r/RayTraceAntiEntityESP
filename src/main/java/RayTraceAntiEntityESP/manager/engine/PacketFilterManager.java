package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.utils.VisibilityUtils;
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

    public static final Set<String> bypassPacketSet = Collections.synchronizedSet(new HashSet<>());

    public static String bypassShowKey(Player viewer, UUID entityUUID) {
        return viewer.getUniqueId() + ":show:" + entityUUID;
    }

    public static String bypassHiddenKey(Player viewer, UUID entityUUID) {
        return viewer.getUniqueId() + ":hidden:" + entityUUID;
    }

    public static void packetFilter(PacketSendEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        Player viewer = event.getPlayer();

        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);

            if (packet.getUUID().isEmpty()) return;
            UUID entityUUID = packet.getUUID().get();

            if (viewer.getUniqueId().equals(entityUUID)) return;
            if (bypassPacketSet.remove(bypassShowKey(viewer, entityUUID))) return;

            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (entity == null) return;
                if (RayTraceManager.isEntityVisible(viewer, entity)) {
                    VisibilityUtils.setNotHidden(viewer, entity);
                } else {
                    VisibilityUtils.setHidden(viewer, entity);
                }
            });
        }
        if (packetType == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            WrapperPlayServerPlayerInfoRemove packet = new WrapperPlayServerPlayerInfoRemove(event);

            List<UUID> original = packet.getProfileIds();
            List<UUID> filtered = new ArrayList<>();

            for (UUID entityUUID : original) {

                if (viewer.getUniqueId().equals(entityUUID)) continue;
                if (bypassPacketSet.contains(bypassHiddenKey(viewer, entityUUID))) continue;

                filtered.add(entityUUID);
            }

            if (filtered.size() == original.size()) return;

            event.setCancelled(true);

            if (!filtered.isEmpty()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(filtered));
            }
        }
    }
}