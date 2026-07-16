package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedPlayerInfoUpdate;
import RayTraceAntiEntityESP.bukkit.nms.parsed.PlayerInfoEntry;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerInfoUpdatePacketListener extends PacketListener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedPlayerInfoUpdate parsed = NmsAdapterFactory.get().parsePlayerInfoUpdate(msg);
        if (parsed == null) return false;

        boolean touchesDisplayName = parsed.actions().contains("ADD_PLAYER")
                || parsed.actions().contains("UPDATE_DISPLAY_NAME");

        if (!touchesDisplayName) {
            ctx.write(msg, promise);
            return true;
        }

        Map<UUID, Component> forcedDisplayNames = null;

        for (PlayerInfoEntry entry : parsed.entries()) {
            Component forced = buildForcedDisplayName(entry);
            if (forced != null) {
                if (forcedDisplayNames == null) forcedDisplayNames = new HashMap<>();
                forcedDisplayNames.put(entry.profileId(), forced);
            }
        }

        Object rebuilt = NmsAdapterFactory.get().rebuildPlayerInfoUpdate(parsed, forcedDisplayNames);
        ctx.write(rebuilt, promise);
        return true;
    }

    private static Component buildForcedDisplayName(PlayerInfoEntry entry) {
        if (entry.profile() == null) return null;

        String profileName = entry.profile().name();
        if (profileName == null || profileName.isEmpty()) return null;
        if (!isPlainOrUnset(entry.displayNamePlain(), profileName)) return null;

        String teamName = TeamUtils.getEntryTeamName(profileName);
        if (teamName == null) return null;

        NamedTextColor color = TeamUtils.teamColors.get(teamName);
        Component prefix = TeamUtils.teamPrefixes.get(teamName);
        Component suffix = TeamUtils.teamSuffixes.get(teamName);
        boolean hasPrefix = prefix != null && !PLAIN.serialize(prefix).isEmpty();
        boolean hasSuffix = suffix != null && !PLAIN.serialize(suffix).isEmpty();
        if (color == null && !hasPrefix && !hasSuffix) return null;

        Component name = Component.text(profileName);
        if (color != null) name = name.color(color);
        if (hasPrefix) name = prefix.append(name);
        if (hasSuffix) name = name.append(suffix);

        return name;
    }

    private static boolean isPlainOrUnset(String displayNamePlain, String profileName) {
        if (displayNamePlain == null) return true;
        return displayNamePlain.equals(profileName);
    }
}