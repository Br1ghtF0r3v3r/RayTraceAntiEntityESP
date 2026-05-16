package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.misc.StringFormat;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.mapVisibility;

public class SetPlayerTeamPacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (msg instanceof ClientboundSetPlayerTeamPacket packet) {
            String teamName = packet.getName();

            ClientboundSetPlayerTeamPacket.Action teamAction = packet.getTeamAction();
            ClientboundSetPlayerTeamPacket.Action playerAction = packet.getPlayerAction();

            // CREATE or UPDATE — both have parameters, teamAction=ADD
            packet.getParameters().ifPresent(params -> {
                NamedTextColor color = StringFormat.chatFormattingToNamedTextColor(params.getColor());
                net.kyori.adventure.text.Component prefix = PaperAdventure.asAdventure(params.getPlayerPrefix());
                if (color != null) TeamUtils.teamColors.put(teamName, color);
                TeamUtils.teamPrefixes.put(teamName, prefix);
                TeamUtils.teamVisibilities.put(teamName, mapVisibility(params.getNametagVisibility()));
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
                TeamUtils.teamVisibilities.remove(teamName);
                TeamUtils.entryToTeam.values().removeIf(teamName::equals);
            }
            ctx.write(msg, promise);
            return true;
        }
        return false;
    }
}