package RayTraceAntiEntityESP.bukkit.manager.engine;

import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class PacketManager extends PacketListenerAbstract {

    public record BypassKey(UUID viewer, UUID entity, boolean show) {}

    public static final Set<BypassKey> bypassPacketSet = ConcurrentHashMap.newKeySet();
    public static final Map<UUID, Set<Integer>> glowingEntities = new ConcurrentHashMap<>();

    public static BypassKey bypassShowKey(Player viewer, UUID entityUUID) {
        return new BypassKey(viewer.getUniqueId(), entityUUID, true);
    }

    public static BypassKey bypassHiddenKey(Player viewer, UUID entityUUID) {
        return new BypassKey(viewer.getUniqueId(), entityUUID, false);
    }

    public static void packetManager(PacketSendEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        Player viewer = event.getPlayer();

        switch (packetType) {
            case PacketType.Play.Server.SPAWN_ENTITY -> {
                WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);

                boolean isNametagClone = packet.getEntityId() >= 2000000 && packet.getEntityId() < 3000000;
                boolean isVerticesDebug = packet.getEntityId() >= 4000000 && packet.getEntityId() < 5000000;

                if (isNametagClone) return;
                if (isVerticesDebug) return;
                if (packet.getUUID().isEmpty()) return;
                UUID entityUUID = packet.getUUID().get();
                if (viewer.getUniqueId().equals(entityUUID)) return;
                if (bypassPacketSet.remove(bypassShowKey(viewer, entityUUID))) return;

                event.setCancelled(true);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Entity entity = Bukkit.getEntity(entityUUID);
                    if (entity == null) return;
                    Vector eyePos = viewer.getEyeLocation().toVector();
                    Vector lookDir = viewer.getLocation().getDirection();
                    Location viewerLoc = viewer.getLocation().clone();
                    World world = viewer.getWorld();
                    if (RayTraceManager.isEntityInSight(viewer, entity, eyePos, lookDir, viewerLoc, world)) {
                        VisibilityUtils.setNotHidden(viewer, entity);
                    } else {
                        VisibilityUtils.setHidden(viewer, entity);
                    }
                });
            }
            case PacketType.Play.Server.PLAYER_INFO_REMOVE -> {
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
            case PacketType.Play.Server.ENTITY_METADATA -> {
                WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);

                boolean isNametagClone = packet.getEntityId() >= 2000000 && packet.getEntityId() < 3000000;
                boolean isVerticesDebug = packet.getEntityId() >= 4000000 && packet.getEntityId() < 5000000;

                if (isNametagClone) return;
                if (isVerticesDebug) return;
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
            case PacketType.Play.Server.TEAMS -> {
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
                    TeamUtils.entryToTeam.values().removeIf(teamName::equals);
                }
            }
            default -> {}
        }
    }
}
