package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.engine.DebugVertexRenderer;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.utils.TeamUtils;
import RayTraceAntiEntityESP.bukkit.utils.VersionChecker;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDebugEnabled;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDisplayNameEnabled;

public class EventManager {

    private static final String HANDLER_NAME = "anti_esp_handler";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static boolean isEmptyComponent(Component c) {
        if (c == null) return true;
        return PLAIN.serialize(c).isEmpty();
    }

    public static void playerQuitHandler(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        int viewerEntityId = player.getEntityId();

        NmsAdapterFactory.get().forEachServerPlayer(other -> {
            if (other.getUniqueId().equals(playerUUID)) return;
            if (VisibilityUtils.isHidden(other.getEntityId(), viewerEntityId)) {
                NmsAdapterFactory.get().sendPacket(other, NmsAdapterFactory.get().buildPlayerInfoRemovePacket(List.of(playerUUID)));
                VisibilityUtils.setNotHiddenSilently(other.getEntityId(), viewerEntityId);
            }
            PacketManager.removeHiddenBypass(other.getUniqueId(), playerUUID);
        });
        PacketManager.clearBypassForViewer(playerUUID);
        TeamUtils.clearViewerOverrides(playerUUID);

        if (isDisplayNameEnabled) {
            NametagCloneRenderer.removeDisplayForEntity(playerUUID);
            NametagCloneRenderer.removeDisplay(playerUUID);
        }
        if (isDebugEnabled) {
            DebugVertexRenderer.removeDisplayForEntity(playerUUID);
            DebugVertexRenderer.removeDisplay(playerUUID);
        }
        VisibilityUtils.clearViewer(viewerEntityId);
        RayTraceEngine.clearViewerCache(player.getEntityId());
    }

    public static void connectionCloseHandler(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(playerUUID) != null) return;
            if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplay(playerUUID);
            if (isDebugEnabled) DebugVertexRenderer.removeDisplay(playerUUID);
        });
    }

    public static void playerJoinHandler(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        injectPlayer(player);

        Objective obj = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(DisplaySlot.BELOW_NAME);
        if (obj != null) {
            PacketManager.belowNameObjective.put(playerUUID, obj.getName());
        }

        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            String teamName = team.getName();
            try {
                TextColor textColor = team.color();
                if (textColor instanceof NamedTextColor namedColor) {
                    TeamUtils.teamColors.putIfAbsent(teamName, namedColor);
                }
            } catch (IllegalStateException ignored) {
            }
            Component prefix = team.prefix();
            if (!isEmptyComponent(prefix)) {
                TeamUtils.teamPrefixes.putIfAbsent(teamName, prefix);
            }
            Component suffix = team.suffix();
            if (!isEmptyComponent(suffix)) {
                TeamUtils.teamSuffixes.putIfAbsent(teamName, suffix);
            }
            TeamUtils.teamVisibilities.putIfAbsent(teamName, team.getOption(Team.Option.NAME_TAG_VISIBILITY));
            for (String entry : team.getEntries()) {
                TeamUtils.entryToTeam.putIfAbsent(entry, teamName);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            NmsAdapterFactory.get().resendAllTeamsTo(player);
            if (player.isOnline() && player.hasPermission("raytrace_anti_entity_esp.admin")) {
                VersionChecker.notifyIfOutdated(player);
            }
        }, 2L);
    }

    public static void entityDeathHandler(EntityDeathEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        UUID entityUUID = entity.getUniqueId();

        if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplayForEntity(entityUUID);
        if (isDebugEnabled) DebugVertexRenderer.removeDisplayForEntity(entityUUID);
    }

    public static void playerRespawnHandler(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        int entityId = player.getEntityId();

        VisibilityUtils.clearViewer(entityId);
    }

    public static void injectPlayer(Player player) {
        Channel ch = NmsAdapterFactory.get().getChannel(player);
        if (ch.pipeline().get(HANDLER_NAME) != null) return;
        ch.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (!PacketManager.onPacketSend(player, msg, ctx, promise)) {
                    super.write(ctx, msg, promise);
                }
            }
        });
    }

    public static void uninjectPlayer(Player player) {
        Channel ch = NmsAdapterFactory.get().getChannel(player);
        if (ch.pipeline().get(HANDLER_NAME) == null) return;
        ch.eventLoop().execute(() -> {
            if (ch.pipeline().get(HANDLER_NAME) != null) {
                ch.pipeline().remove(HANDLER_NAME);
            }
        });
    }
}