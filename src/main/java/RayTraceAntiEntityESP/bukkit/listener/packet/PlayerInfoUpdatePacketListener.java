package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PlayerInfoUpdatePacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundPlayerInfoUpdatePacket packet)) return false;

        if (!packet.actions().contains(Action.UPDATE_DISPLAY_NAME)) {
            ctx.write(msg, promise);
            return true;
        }

        List<Entry> original = packet.entries();
        List<Entry> modified = new ArrayList<>(original.size());
        boolean changed = false;

        int viewerEntityId = ((CraftPlayer) viewer).getHandle().getId();

        for (Entry entry : original) {
            net.minecraft.server.level.ServerPlayer nmsTarget =
                    net.minecraft.server.MinecraftServer.getServer().getPlayerList().getPlayer(entry.profileId());

            if (nmsTarget == null) {
                modified.add(entry);
                continue;
            }

            if (!VisibilityUtils.isHidden(viewerEntityId, nmsTarget.getId())) {
                modified.add(entry);
                continue;
            }

            String playerName = nmsTarget.getScoreboardName();
            String teamName = TeamUtils.entryToTeam.get(playerName);
            if (teamName == null) {
                modified.add(entry);
                continue;
            }

            NamedTextColor teamColor = TeamUtils.teamColors.get(teamName);
            Component teamPrefix = TeamUtils.teamPrefixes.get(teamName);
            Component teamSuffix = TeamUtils.teamSuffixes.get(teamName);
            if (teamColor == null && teamPrefix == null && teamSuffix == null) {
                modified.add(entry);
                continue;
            }

            Component displayName = Component.text(nmsTarget.getScoreboardName());
            if (teamColor != null) displayName = displayName.color(teamColor);
            if (teamPrefix != null) displayName = teamPrefix.append(displayName);
            if (teamSuffix != null) displayName = displayName.append(teamSuffix);

            modified.add(new Entry(
                    entry.profileId(),
                    entry.profile(),
                    entry.listed(),
                    entry.latency(),
                    entry.gameMode(),
                    PaperAdventure.asVanilla(displayName),
                    entry.showHat(),
                    entry.listOrder(),
                    entry.chatSession()
            ));
            changed = true;
        }

        if (!changed) {
            ctx.write(msg, promise);
            return true;
        }

        ctx.write(new ClientboundPlayerInfoUpdatePacket(packet.actions(), modified), promise);
        return true;
    }
}