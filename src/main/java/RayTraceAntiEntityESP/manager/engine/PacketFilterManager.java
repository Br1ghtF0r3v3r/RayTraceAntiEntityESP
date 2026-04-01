package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.utils.FakeNameDisplay;
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
    public static String bypassKey(Player viewer, UUID entityId) {
        return viewer.getUniqueId() + ":" + entityId;
    }

    public static boolean addPacketBypass(Player viewer, UUID entityId) {
        return PacketFilterManager.bypassSet.add(bypassKey(viewer, entityId));
    }

    public static boolean removePacketBypass(Player viewer, UUID entityId) {
        return PacketFilterManager.bypassSet.remove(bypassKey(viewer, entityId));
    }

    public static void clearPacketBypass() {
        PacketFilterManager.bypassSet.clear();
    }

    public static void packetFilter(PacketSendEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        Player viewer = event.getPlayer();

        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);

            if (packet.getUUID().isEmpty()) return;
            UUID entityUUID = packet.getUUID().get();

            if (viewer.getUniqueId().equals(entityUUID)) return;
            if (removePacketBypass(viewer, entityUUID)) return;

            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(entityUUID);
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

            List<UUID> original = packet.getProfileIds();
            List<UUID> filtered = new ArrayList<>();

            for (UUID uuid : original) {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null
                        && !viewer.canSee(target)
                        && FakeNameDisplay.fakeNameDisplay.containsKey(viewer.getUniqueId())
                        && FakeNameDisplay.fakeNameDisplay.get(viewer.getUniqueId()).containsKey(uuid)) {
                    continue;
                }
                filtered.add(uuid);
            }

            if (filtered.size() == original.size()) return;
            event.setCancelled(true);
            if (!filtered.isEmpty()) PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(filtered));
        }
    }
}