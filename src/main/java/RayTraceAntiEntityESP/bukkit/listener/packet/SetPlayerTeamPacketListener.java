package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.misc.StringFormat;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.mapVisibility;

public class SetPlayerTeamPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundSetPlayerTeamPacket packet)) return false;

        String teamName = packet.getName();
        ClientboundSetPlayerTeamPacket.Action teamAction = packet.getTeamAction();
        ClientboundSetPlayerTeamPacket.Action playerAction = packet.getPlayerAction();

        packet.getParameters().ifPresent(params -> {
            NamedTextColor color = StringFormat.chatFormattingToNamedTextColor(params.getColor());
            Component prefix = PaperAdventure.asAdventure(params.getPlayerPrefix());
            if (color != null) TeamUtils.teamColors.put(teamName, color);
            TeamUtils.teamPrefixes.put(teamName, prefix);
            TeamUtils.teamVisibilities.put(teamName, mapVisibility(params.getNametagVisibility()));
        });

        if (teamAction == ClientboundSetPlayerTeamPacket.Action.ADD || playerAction == ClientboundSetPlayerTeamPacket.Action.ADD) {
            for (String entry : packet.getPlayers()) TeamUtils.entryToTeam.put(entry, teamName);
        }

        if (playerAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            for (String entry : packet.getPlayers()) TeamUtils.entryToTeam.remove(entry);

            net.minecraft.server.level.ServerPlayer nmsViewer = ((CraftPlayer) viewer).getHandle();
            int viewerEntityId = nmsViewer.getId();
            for (String entry : packet.getPlayers()) {
                Player target = Bukkit.getPlayerExact(entry);
                if (target == null) continue;
                int targetEntityId = ((CraftPlayer) target).getHandle().getId();
                if (!VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) continue;
                net.minecraft.server.level.ServerPlayer nmsTarget = ((CraftPlayer) target).getHandle();
                nmsViewer.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                        java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                        java.util.List.of(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(
                                nmsTarget.getUUID(),
                                nmsTarget.getGameProfile(),
                                true,
                                nmsTarget.connection.latency(),
                                nmsTarget.gameMode.getGameModeForPlayer(),
                                net.minecraft.network.chat.Component.literal(nmsTarget.getScoreboardName()),
                                true,
                                0,
                                null
                        ))
                ));
            }
        }

        if (teamAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            TeamUtils.teamColors.remove(teamName);
            TeamUtils.teamPrefixes.remove(teamName);
            TeamUtils.teamVisibilities.remove(teamName);
            TeamUtils.entryToTeam.values().removeIf(teamName::equals);
        }

        ctx.write(msg, promise);

        if (playerAction == ClientboundSetPlayerTeamPacket.Action.ADD || teamAction == ClientboundSetPlayerTeamPacket.Action.ADD) {
            NamedTextColor teamColor = TeamUtils.teamColors.get(teamName);
            Component teamPrefix = TeamUtils.teamPrefixes.get(teamName);
            if (teamColor == null && teamPrefix == null) return true;

            Collection<String> entries = packet.getPlayers().isEmpty()
                    ? TeamUtils.entryToTeam.entrySet().stream()
                      .filter(e -> e.getValue().equals(teamName))
                      .map(java.util.Map.Entry::getKey)
                      .toList()
                    : packet.getPlayers();

            net.minecraft.server.level.ServerPlayer nmsViewer = ((CraftPlayer) viewer).getHandle();
            int viewerEntityId = nmsViewer.getId();

            for (String entry : entries) {
                Player target = Bukkit.getPlayerExact(entry);
                if (target == null) continue;
                int targetEntityId = ((CraftPlayer) target).getHandle().getId();
                if (!VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) continue;

                Component displayName = Component.text(target.getName());
                if (teamColor != null) displayName = displayName.color(teamColor);
                if (teamPrefix != null) displayName = teamPrefix.append(displayName);

                net.minecraft.network.chat.Component nmsName =
                        PaperAdventure.asVanilla(displayName);
                net.minecraft.server.level.ServerPlayer nmsTarget =
                        ((CraftPlayer) target).getHandle();
                nmsViewer.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                        java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                        java.util.List.of(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(
                                nmsTarget.getUUID(),
                                nmsTarget.getGameProfile(),
                                true,
                                nmsTarget.connection.latency(),
                                nmsTarget.gameMode.getGameModeForPlayer(),
                                nmsName,
                                true,
                                0,
                                null
                        ))
                ));
            }
        }

        return true;
    }
}