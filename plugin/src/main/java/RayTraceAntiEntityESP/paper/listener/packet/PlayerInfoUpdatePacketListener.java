package RayTraceAntiEntityESP.paper.listener.packet;

import RayTraceAntiEntityESP.paper.listener.PacketListener;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.nms.parsed.ParsedPlayerInfoUpdate;
import RayTraceAntiEntityESP.paper.nms.parsed.PlayerInfoEntry;
import RayTraceAntiEntityESP.paper.utils.TeamUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerInfoUpdatePacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedPlayerInfoUpdate parsed = NmsAdapterFactory.get().parsePlayerInfoUpdate(msg);
        if (parsed == null) return false;

        boolean touchesDisplayName = parsed.actions().contains("ADD_PLAYER") || parsed.actions().contains("UPDATE_DISPLAY_NAME");

        if (!touchesDisplayName) {
            ctx.write(msg, promise);
            return true;
        }

        Map<UUID, Component> forcedDisplayNames = null;

        for (PlayerInfoEntry entry : parsed.entries()) {
            Component forced = buildForcedDisplayName(viewer, entry);
            if (forced != null) {
                if (forcedDisplayNames == null) forcedDisplayNames = new HashMap<>();
                forcedDisplayNames.put(entry.profileId(), forced);
            }
        }

        Object rebuilt = NmsAdapterFactory.get().rebuildPlayerInfoUpdate(parsed, forcedDisplayNames);
        ctx.write(rebuilt, promise);
        return true;
    }

    private static Component buildForcedDisplayName(Player viewer, PlayerInfoEntry entry) {
        if (entry.profile() == null) return null;

        String profileName = entry.profile().name();
        if (profileName == null || profileName.isEmpty()) return null;
        if (!isPlainOrUnset(entry.displayNamePlain(), profileName)) return null;
        Player target = org.bukkit.Bukkit.getPlayer(entry.profileId());
        if (target != null) {
            return TeamUtils.decorateName(viewer, target, profileName);
        }
        String teamName = TeamUtils.getEntryTeamName(profileName);
        if (teamName == null) return null;

        return TeamUtils.decorateName(teamName, profileName);
    }

    private static boolean isPlainOrUnset(String displayNamePlain, String profileName) {
        if (displayNamePlain == null) return true;
        return displayNamePlain.equals(profileName);
    }
}