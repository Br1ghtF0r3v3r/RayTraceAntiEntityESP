package RayTraceAntiEntityESP.bukkit.compatibility;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.listener.packet.SetEntityDataPacketListener;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

final class PacketEventsHook {

    private static final int GLOW_BIT = 0x40;
    private static final int INVISIBLE_BIT = 0x20;
    private static boolean installed = false;

    private PacketEventsHook() {
    }

    static void install() {
        if (installed) return;
        if (!PacketEvents.getAPI().isInitialized()) return;

        PacketEvents.getAPI().getEventManager().registerListener(new BridgeListener());
        installed = true;
        plugin.getLogger().info("PacketEvents detected - enabled team-color/glow/invisibility compatibility bridge.");
    }

    private static final class BridgeListener extends PacketListenerAbstract {

        BridgeListener() {
            super(PacketListenerPriority.MONITOR);
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            var type = event.getPacketType();
            if (type == PacketType.Play.Server.TEAMS) {
                handleTeams(event);
            } else if (type == PacketType.Play.Server.ENTITY_METADATA) {
                handleEntityMetadata(event);
            }
        }

        private void handleTeams(PacketSendEvent event) {
            UUID viewer;
            WrapperPlayServerTeams packet;
            try {
                viewer = event.getUser().getUUID();
                packet = new WrapperPlayServerTeams(event);
            } catch (Throwable t) {
                return;
            }

            String teamName = packet.getTeamName();

            try {
                switch (packet.getTeamMode()) {
                    case REMOVE -> TeamUtils.removeViewerTeam(viewer, teamName);

                    case CREATE, UPDATE -> {
                        packet.getTeamInfo().ifPresent(info -> TeamUtils.putViewerTeamInfo(
                                viewer, teamName, info.getColor(), info.getPrefix(), info.getSuffix()));
                        for (String entry : packet.getPlayers()) {
                            TeamUtils.putViewerTeamAssignment(viewer, entry, teamName);
                        }
                    }

                    case ADD_ENTITIES -> {
                        for (String entry : packet.getPlayers()) {
                            TeamUtils.putViewerTeamAssignment(viewer, entry, teamName);
                        }
                    }

                    case REMOVE_ENTITIES -> {
                        for (String entry : packet.getPlayers()) {
                            TeamUtils.removeViewerTeamAssignment(viewer, entry);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        private void handleEntityMetadata(PacketSendEvent event) {
            UUID viewer;
            WrapperPlayServerEntityMetadata wrapper;
            try {
                viewer = event.getUser().getUUID();
                wrapper = new WrapperPlayServerEntityMetadata(event);
            } catch (Throwable t) {
                return;
            }

            int entityId = wrapper.getEntityId();
            if (PacketManager.isFakeEntity(entityId)) return;

            try {
                List<EntityData<?>> data = wrapper.getEntityMetadata();
                if (data.isEmpty()) return;

                for (EntityData<?> entityData : data) {
                    if (entityData == null || entityData.getIndex() != 0) continue;
                    if (!(entityData.getValue() instanceof Byte flags)) continue;

                    boolean glowing = (flags & GLOW_BIT) != 0;
                    boolean invisible = (flags & INVISIBLE_BIT) != 0;

                    Set<Integer> glowingForViewer = PacketManager.glowingEntities
                            .computeIfAbsent(viewer, k -> ConcurrentHashMap.newKeySet());

                    if (glowing) {
                        glowingForViewer.add(entityId);
                    } else {
                        glowingForViewer.remove(entityId);
                    }

                    SetEntityDataPacketListener.invisibleCache.put(entityId, invisible);
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
    }
}