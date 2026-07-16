package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedSetPlayerTeam;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SetPlayerTeamPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedSetPlayerTeam parsed = NmsAdapterFactory.get().parseSetPlayerTeam(msg);
        if (parsed == null) return false;

        String teamName = parsed.teamName();
        String teamAction = parsed.teamAction();
        String playerAction = parsed.playerAction();

        List<Object> outbox = new ArrayList<>();

        if (parsed.color() != null || parsed.prefix() != null || parsed.suffix() != null || parsed.nametagVisibility() != null) {
            NamedTextColor color = parsed.color();
            Component prefix = parsed.prefix();
            Component suffix = parsed.suffix();
            TeamUtils.putOrRemove(TeamUtils.teamColors, teamName, color);
            TeamUtils.putOrRemove(TeamUtils.teamPrefixes, teamName, prefix);
            TeamUtils.putOrRemove(TeamUtils.teamSuffixes, teamName, suffix);
            if (parsed.nametagVisibility() != null) {
                TeamUtils.teamVisibilities.put(teamName, parsed.nametagVisibility());
            }

            if (Config.isDisplayNameEnabled) {
                int viewerEntityId = viewer.getEntityId();
                for (String entry : TeamUtils.entryToTeam.entrySet().stream()
                        .filter(e -> e.getValue().equals(teamName))
                        .map(Map.Entry::getKey)
                        .toList()) {
                    Player target = Bukkit.getPlayerExact(entry);
                    if (target == null) continue;
                    int targetEntityId = target.getEntityId();
                    if (!VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) continue;
                    NametagCloneRenderer.refreshDisplay(viewer, target, outbox);
                }
            }
        }

        if ("ADD".equals(teamAction) || "ADD".equals(playerAction)) {
            for (String entry : parsed.players()) TeamUtils.entryToTeam.put(entry, teamName);
        }

        if ("REMOVE".equals(playerAction)) {
            for (String entry : parsed.players()) TeamUtils.entryToTeam.remove(entry);

            int viewerEntityId = viewer.getEntityId();
            for (String entry : parsed.players()) {
                Player target = Bukkit.getPlayerExact(entry);
                if (target == null) continue;

                int targetEntityId = target.getEntityId();
                boolean isHidden = VisibilityUtils.isHidden(viewerEntityId, targetEntityId);

                if (isHidden && Config.isDisplayNameEnabled) {
                    NametagCloneRenderer.refreshDisplay(viewer, target, outbox);
                }
            }
        }

        if ("REMOVE".equals(teamAction)) {
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

    private static void flushOutbox(Player viewer, List<Object> outbox) {
        if (outbox.isEmpty()) return;
        NmsAdapterFactory.get().sendBundled(viewer, outbox);
    }
}