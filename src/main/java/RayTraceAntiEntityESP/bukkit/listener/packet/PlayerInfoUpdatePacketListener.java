package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class PlayerInfoUpdatePacketListener extends PacketListener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static volatile boolean fakePlayerHookResolved = false;
    private static Method fakePlayerPluginGetInstance;
    private static Method getFakePlayerManager;
    private static Method getByUuid;

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundPlayerInfoUpdatePacket packet)) return false;

        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = packet.actions();
        boolean touchesDisplayName = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);

        if (!touchesDisplayName) {
            ctx.write(msg, promise);
            return true;
        }

        boolean rewritePacket = false;
        List<ClientboundPlayerInfoUpdatePacket.Entry> updated = new ArrayList<>(packet.entries().size());

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            net.minecraft.network.chat.Component forced = buildForcedDisplayName(entry);
            if (forced != null) {
                updated.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId(), entry.profile(), entry.listed(), entry.latency(),
                        entry.gameMode(), forced, entry.showHat(), entry.listOrder(), entry.chatSession()));
                rewritePacket = true;
            } else {
                updated.add(entry);
            }
        }

        ctx.write(rewritePacket ? new ClientboundPlayerInfoUpdatePacket(actions, updated) : msg, promise);
        return true;
    }

    private static net.minecraft.network.chat.Component buildForcedDisplayName(ClientboundPlayerInfoUpdatePacket.Entry entry) {
        if (entry.profile() == null) return null;
        if (!isFakePlayerBot(entry.profileId())) return null;

        String profileName = entry.profile().name();
        if (profileName == null || profileName.isEmpty()) return null;
        if (!isPlainOrUnset(entry.displayName(), profileName)) return null;

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

        return PaperAdventure.asVanilla(name);
    }

    private static boolean isFakePlayerBot(UUID profileId) {
        ensureFakePlayerHook();
        if (getByUuid == null) return false;
        try {
            Object pluginInstance = fakePlayerPluginGetInstance.invoke(null);
            if (pluginInstance == null) return false;
            Object manager = getFakePlayerManager.invoke(pluginInstance);
            if (manager == null) return false;
            return getByUuid.invoke(manager, profileId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized void ensureFakePlayerHook() {
        if (fakePlayerHookResolved) return;
        fakePlayerHookResolved = true;
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("FakePlayerPlugin");
            if (plugin == null || !plugin.isEnabled()) return;

            Class<?> pluginClass = Class.forName("me.bill.fakePlayerPlugin.FakePlayerPlugin");
            Class<?> managerClass = Class.forName("me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager");

            fakePlayerPluginGetInstance = pluginClass.getMethod("getInstance");
            getFakePlayerManager = pluginClass.getMethod("getFakePlayerManager");
            getByUuid = managerClass.getMethod("getByUuid", UUID.class);
        } catch (Throwable t) {
            fakePlayerPluginGetInstance = null;
            getFakePlayerManager = null;
            getByUuid = null;
        }
    }

    private static boolean isPlainOrUnset(net.minecraft.network.chat.Component displayName, String profileName) {
        if (displayName == null) return true;
        return displayName.getString().equals(profileName);
    }
}