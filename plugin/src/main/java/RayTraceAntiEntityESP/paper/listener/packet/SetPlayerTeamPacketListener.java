package RayTraceAntiEntityESP.paper.listener.packet;

import RayTraceAntiEntityESP.paper.config.Config;
import RayTraceAntiEntityESP.paper.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.paper.listener.PacketListener;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.nms.parsed.ParsedSetPlayerTeam;
import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapterFactory;
import RayTraceAntiEntityESP.paper.utils.TeamUtils;
import RayTraceAntiEntityESP.paper.utils.VisibilityUtils;
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

        boolean hasColorInfo = parsed.color() != null || parsed.prefix() != null || parsed.suffix() != null || parsed.nametagVisibility() != null;

        if (hasColorInfo) {
            NamedTextColor color = parsed.color();
            Component prefix = parsed.prefix();
            Component suffix = parsed.suffix();
            TeamUtils.putOrRemove(TeamUtils.teamColors, teamName, color);
            TeamUtils.putOrRemove(TeamUtils.teamPrefixes, teamName, prefix);
            TeamUtils.putOrRemove(TeamUtils.teamSuffixes, teamName, suffix);
            if (parsed.nametagVisibility() != null) {
                TeamUtils.teamVisibilities.put(teamName, parsed.nametagVisibility());
            }
        }

        if ("ADD".equals(teamAction) || "ADD".equals(playerAction)) {
            for (String entry : parsed.players()) TeamUtils.entryToTeam.put(entry, teamName);
        }

        List<String> playersRemoved = null;
        if ("REMOVE".equals(playerAction)) {
            playersRemoved = List.copyOf(parsed.players());
            for (String entry : parsed.players()) TeamUtils.entryToTeam.remove(entry);
        }

        if ("REMOVE".equals(teamAction)) {
            TeamUtils.teamColors.remove(teamName);
            TeamUtils.teamPrefixes.remove(teamName);
            TeamUtils.teamSuffixes.remove(teamName);
            TeamUtils.teamVisibilities.remove(teamName);
            TeamUtils.entryToTeam.values().removeIf(teamName::equals);
        }

        ctx.write(msg, promise);
        if (Config.isDisplayNameEnabled && (hasColorInfo || playersRemoved != null)) {
            List<String> finalPlayersRemoved = playersRemoved;
            SchedulerAdapterFactory.get().runEntityTask(viewer, () ->
                    refreshNametagsForTeamChange(viewer, teamName, hasColorInfo, finalPlayersRemoved));
        }

        return true;
    }

    private static void refreshTabListEntry(Player viewer, Player target, String teamName) {
        Component decorated = TeamUtils.decorateName(viewer, target, target.getName());
        if (decorated == null) return;
        NmsAdapterFactory.get().sendPacket(viewer, NmsAdapterFactory.get().buildDisplayNameUpdatePacket(target, decorated));
    }

    private static void refreshNametagsForTeamChange(Player viewer, String teamName,
                                                     boolean hasColorInfo, List<String> playersRemoved) {
        List<Object> outbox = new ArrayList<>();
        int viewerEntityId = viewer.getEntityId();

        if (hasColorInfo) {
            for (String entry : TeamUtils.entryToTeam.entrySet().stream()
                    .filter(e -> e.getValue().equals(teamName))
                    .map(Map.Entry::getKey)
                    .toList()) {
                Player target = Bukkit.getPlayerExact(entry);
                if (target == null) continue;
                int targetEntityId = target.getEntityId();
                if (!VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) continue;
                NametagCloneRenderer.refreshDisplay(viewer, target, outbox);
                refreshTabListEntry(viewer, target, teamName);
            }
        }

        if (playersRemoved != null) {
            for (String entry : playersRemoved) {
                Player target = Bukkit.getPlayerExact(entry);
                if (target == null) continue;
                int targetEntityId = target.getEntityId();
                if (VisibilityUtils.isHidden(viewerEntityId, targetEntityId)) {
                    NametagCloneRenderer.refreshDisplay(viewer, target, outbox);
                }
            }
        }

        if (!outbox.isEmpty()) {
            NmsAdapterFactory.get().sendBundled(viewer, outbox);
        }
    }
}