package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.misc.StringFormat;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.mapVisibility;

public class SetPlayerTeamPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundSetPlayerTeamPacket packet)) return false;

        String teamName = packet.getName();
        ClientboundSetPlayerTeamPacket.Action teamAction = packet.getTeamAction();
        ClientboundSetPlayerTeamPacket.Action playerAction = packet.getPlayerAction();

        List<Packet<? super ClientGamePacketListener>> outbox = new ArrayList<>();

        packet.getParameters().ifPresent(params -> {
            NamedTextColor color = StringFormat.chatFormattingToNamedTextColor(params.getColor());
            Component prefix = StringFormat.LEGACY_SERIALIZER.deserialize(params.getPlayerPrefix().getString());
            Component suffix = StringFormat.LEGACY_SERIALIZER.deserialize(params.getPlayerSuffix().getString());
            if (color != null) TeamUtils.teamColors.put(teamName, color);
            else TeamUtils.teamColors.remove(teamName);
            TeamUtils.teamPrefixes.put(teamName, prefix);
            TeamUtils.teamSuffixes.put(teamName, suffix);
            TeamUtils.teamVisibilities.put(teamName, mapVisibility(params.getNametagVisibility()));

            if (Config.isDisplayNameEnabled) {
                ServerPlayer nmsViewer = ((CraftPlayer) viewer).getHandle();
                int viewerEntityId = nmsViewer.getId();
                for (String entry : TeamUtils.entryToTeam.entrySet().stream()
                        .filter(e -> e.getValue().equals(teamName))
                        .map(Map.Entry::getKey)
                        .toList()) {
                    Player target = Bukkit.getPlayerExact(entry);
                    if (target == null) continue;
                    int targetEntityId = ((CraftPlayer) target).getHandle().getId();
                    if (!VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) continue;
                    NametagCloneRenderer.refreshDisplay(viewer, target, outbox);
                }
            }
        });

        if (teamAction == ClientboundSetPlayerTeamPacket.Action.ADD || playerAction == ClientboundSetPlayerTeamPacket.Action.ADD) {
            for (String entry : packet.getPlayers()) TeamUtils.entryToTeam.put(entry, teamName);
        }

        if (playerAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            for (String entry : packet.getPlayers()) TeamUtils.entryToTeam.remove(entry);

            ServerPlayer nmsViewer = ((CraftPlayer) viewer).getHandle();
            int viewerEntityId = nmsViewer.getId();

            for (String entry : packet.getPlayers()) {
                Player target = Bukkit.getPlayerExact(entry);
                if (target == null) continue;

                int targetEntityId = ((CraftPlayer) target).getHandle().getId();
                boolean isHidden = VisibilityUtils.isHidden(viewerEntityId, targetEntityId);

                if (isHidden && Config.isDisplayNameEnabled) {
                    NametagCloneRenderer.refreshDisplay(viewer, target, outbox);
                }
            }
        }

        if (teamAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            TeamUtils.teamColors.remove(teamName);
            TeamUtils.teamPrefixes.remove(teamName);
            TeamUtils.teamSuffixes.remove(teamName);
            TeamUtils.teamVisibilities.remove(teamName);
            TeamUtils.entryToTeam.values().removeIf(teamName::equals);
        }

        ctx.write(msg, promise);
        flushOutbox(viewer, outbox);
        return true;
    }

    private static void flushOutbox(
            Player viewer,
            List<Packet<? super ClientGamePacketListener>> outbox
    ) {
        if (outbox.isEmpty()) return;
        ((CraftPlayer) viewer).getHandle().connection
                .send(new ClientboundBundlePacket(outbox));
    }
}