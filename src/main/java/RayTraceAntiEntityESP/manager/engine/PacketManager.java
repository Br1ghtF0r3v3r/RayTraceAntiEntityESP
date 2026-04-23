package RayTraceAntiEntityESP.manager.engine;

import RayTraceAntiEntityESP.utils.TeamUtils;
import RayTraceAntiEntityESP.utils.VisibilityUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.Main.plugin;

public class PacketManager extends PacketListenerAbstract {

    public static final Set<String> bypassPacketSet = Collections.synchronizedSet(new HashSet<>());
    public static final Map<UUID, Set<Integer>> glowingEntities = new ConcurrentHashMap<>();

    public static String bypassShowKey(Player viewer, UUID entityUUID) {
        return viewer.getUniqueId() + ":show:" + entityUUID;
    }

    public static String bypassHiddenKey(Player viewer, UUID entityUUID) {
        return viewer.getUniqueId() + ":hidden:" + entityUUID;
    }

    public static void packetManager(PacketSendEvent event) {
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
                if (RayTraceManager.isEntityInSight(viewer, entity)) {
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
        if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            Set<Integer> playerSet = glowingEntities.computeIfAbsent(playerUUID, k -> new HashSet<>());
            for (var data : packet.getEntityMetadata()) {
                if (data.getIndex() == 0) {
                    Object value = data.getValue();
                    if (value instanceof Byte flags) {
                        boolean isGlowing = (flags & 0x40) != 0;
                        if (isGlowing) {
                            playerSet.add(packet.getEntityId());
                        } else {
                            playerSet.remove(packet.getEntityId());
                        }
                    }
                }
            }
        }
        if (packetType == PacketType.Play.Server.TEAMS) {
            WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
            String teamName = packet.getTeamName();
            WrapperPlayServerTeams.TeamMode mode = packet.getTeamMode();

            if (mode == WrapperPlayServerTeams.TeamMode.CREATE || mode == WrapperPlayServerTeams.TeamMode.UPDATE) {
                if (packet.getTeamInfo().isPresent()) {
                    WrapperPlayServerTeams.ScoreBoardTeamInfo info = packet.getTeamInfo().get();
                    TeamUtils.teamColors.put(teamName, info.getColor());
                    TeamUtils.teamPrefixes.put(teamName, info.getPrefix());
                }
            }

            if (mode == WrapperPlayServerTeams.TeamMode.CREATE || mode == WrapperPlayServerTeams.TeamMode.ADD_ENTITIES) {
                for (String entry : packet.getPlayers()) {
                    TeamUtils.entryToTeam.put(entry, teamName);
                }
            }

            if (mode == WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES) {
                for (String entry : packet.getPlayers()) {
                    TeamUtils.entryToTeam.remove(entry);
                }
            }

            if (mode == WrapperPlayServerTeams.TeamMode.REMOVE) {
                TeamUtils.teamColors.remove(teamName);
                TeamUtils.teamPrefixes.remove(teamName);
                for (Map.Entry<String, String> entry : TeamUtils.entryToTeam.entrySet()) {
                    if (entry.getValue().equals(teamName)) {
                        TeamUtils.entryToTeam.remove(entry.getKey());
                    }
                }
            }
        }
    }
}