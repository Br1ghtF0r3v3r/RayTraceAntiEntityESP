package RayTraceAntiEntityESP.bukkit.manager.engine;

import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class PacketManager {

    public record BypassKey(UUID viewer, UUID entity, boolean show) {
    }

    public static final Set<BypassKey> bypassPacketSet = ConcurrentHashMap.newKeySet();
    public static final Map<UUID, Set<Integer>> glowingEntities = new ConcurrentHashMap<>();

    public static BypassKey bypassShowKey(Player viewer, UUID entityUUID) {
        return new BypassKey(viewer.getUniqueId(), entityUUID, true);
    }

    public static BypassKey bypassHiddenKey(Player viewer, UUID entityUUID) {
        return new BypassKey(viewer.getUniqueId(), entityUUID, false);
    }

    public static void packetManager(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {

        // SPAWN_ENTITY
        if (msg instanceof ClientboundAddEntityPacket packet) {
            int entityId = packet.getId();

            if (entityId >= 2000000 && entityId < 3000000) {
                ctx.write(msg, promise);
                return;
            } // nametag clone
            if (entityId >= 4000000 && entityId < 5000000) {
                ctx.write(msg, promise);
                return;
            } // vertices debug

            UUID entityUUID = packet.getUUID();
            if (viewer.getUniqueId().equals(entityUUID)) {
                ctx.write(msg, promise);
                return;
            }
            if (bypassPacketSet.remove(bypassShowKey(viewer, entityUUID))) {
                ctx.write(msg, promise);
                return;
            }

            // Cancel — hide immediately, RayTraceManager shows if in sight next tick
            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (entity == null) return;
                VisibilityUtils.setHidden(viewer, entity);
            });
            return;
        }

        // PLAYER_INFO_REMOVE
        if (msg instanceof ClientboundPlayerInfoRemovePacket(List<UUID> original)) {
            List<UUID> filtered = new ArrayList<>();

            for (UUID entityUUID : original) {
                if (viewer.getUniqueId().equals(entityUUID)) continue;
                if (bypassPacketSet.contains(bypassHiddenKey(viewer, entityUUID))) continue;
                filtered.add(entityUUID);
            }

            if (filtered.size() == original.size()) {
                ctx.write(msg, promise);
                return;
            }

            if (!filtered.isEmpty()) {
                ServerPlayer nmsPlayer = ((CraftPlayer) viewer).getHandle();
                nmsPlayer.connection.send(new ClientboundPlayerInfoRemovePacket(filtered));
            }
            return;
        }

        // ENTITY_METADATA
        if (msg instanceof ClientboundSetEntityDataPacket(int entityId, List<SynchedEntityData.DataValue<?>> packedItems)) {

            if (entityId < 2000000 || entityId >= 3000000 && entityId < 4000000 || entityId >= 5000000) {
                Set<Integer> playerSet = glowingEntities.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());

                for (SynchedEntityData.DataValue<?> data : packedItems) {
                    if (data.id() == 0 && data.value() instanceof Byte flags) {
                        if ((flags & 0x40) != 0) playerSet.add(entityId);
                        else playerSet.remove(entityId);
                    }
                }
            }

            ctx.write(msg, promise);
            return;
        }

        // TEAMS
        if (msg instanceof ClientboundSetPlayerTeamPacket packet) {
            String teamName = packet.getName();

            ClientboundSetPlayerTeamPacket.Action teamAction = packet.getTeamAction();
            ClientboundSetPlayerTeamPacket.Action playerAction = packet.getPlayerAction();

            // CREATE or UPDATE — both have parameters, teamAction=ADD
            packet.getParameters().ifPresent(params -> {
                NamedTextColor color = chatFormattingToNamedTextColor(params.getColor());
                net.kyori.adventure.text.Component prefix = PaperAdventure.asAdventure(params.getPlayerPrefix());
                if (color != null) TeamUtils.teamColors.put(teamName, color);
                TeamUtils.teamPrefixes.put(teamName, prefix);
            });

            // CREATE (teamAction=ADD) or ADD_PLAYERS (playerAction=ADD)
            if (teamAction == ClientboundSetPlayerTeamPacket.Action.ADD || playerAction == ClientboundSetPlayerTeamPacket.Action.ADD) {
                for (String entry : packet.getPlayers()) TeamUtils.entryToTeam.put(entry, teamName);
            }

            // REMOVE_PLAYERS (playerAction=REMOVE)
            if (playerAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                for (String entry : packet.getPlayers()) TeamUtils.entryToTeam.remove(entry);
            }

            // REMOVE team (teamAction=REMOVE)
            if (teamAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                TeamUtils.teamColors.remove(teamName);
                TeamUtils.teamPrefixes.remove(teamName);
                TeamUtils.entryToTeam.values().removeIf(teamName::equals);
            }

            ctx.write(msg, promise);
            return;
        }

        ctx.write(msg, promise);
    }

    private static NamedTextColor chatFormattingToNamedTextColor(ChatFormatting formatting) {
        if (formatting.getColor() == null) return null;
        return NamedTextColor.namedColor(formatting.getColor());
    }
}
