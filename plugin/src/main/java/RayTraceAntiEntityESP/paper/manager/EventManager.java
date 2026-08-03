package RayTraceAntiEntityESP.paper.manager;

import RayTraceAntiEntityESP.paper.engine.DebugVertexRenderer;
import RayTraceAntiEntityESP.paper.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.paper.engine.RayTraceEngine;
import RayTraceAntiEntityESP.paper.listener.PacketManager;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapterFactory;
import RayTraceAntiEntityESP.paper.utils.TeamUtils;
import RayTraceAntiEntityESP.paper.utils.VersionChecker;
import RayTraceAntiEntityESP.paper.utils.VisibilityUtils;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

import static RayTraceAntiEntityESP.paper.Main.plugin;
import static RayTraceAntiEntityESP.paper.config.Config.isDebugEnabled;
import static RayTraceAntiEntityESP.paper.config.Config.isDisplayNameEnabled;

public class EventManager {

    private static final String HANDLER_NAME = "anti_esp_handler";

    public static void playerQuitHandler(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        int departingEntityId = player.getEntityId();

        NmsAdapterFactory.get().forEachServerPlayer(other -> {
            if (other.getUniqueId().equals(playerUUID)) return;
            PacketManager.removeHiddenBypass(other.getUniqueId(), playerUUID);
            PacketManager.cancelShowBypass(other.getUniqueId(), playerUUID);

            if (VisibilityUtils.isHidden(other.getEntityId(), departingEntityId)) {
                other.showEntity(plugin, player);
                VisibilityUtils.setNotHiddenSilently(other.getEntityId(), departingEntityId);
            }
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
        VisibilityUtils.clearViewer(departingEntityId);
        RayTraceEngine.clearViewerCache(departingEntityId);
    }

    public static void connectionCloseHandler(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();

        SchedulerAdapterFactory.get().runTask(() -> {
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
            if (!TeamUtils.isEmptyComponent(prefix)) {
                TeamUtils.teamPrefixes.putIfAbsent(teamName, prefix);
            }
            Component suffix = team.suffix();
            if (!TeamUtils.isEmptyComponent(suffix)) {
                TeamUtils.teamSuffixes.putIfAbsent(teamName, suffix);
            }
            TeamUtils.teamVisibilities.putIfAbsent(teamName, team.getOption(Team.Option.NAME_TAG_VISIBILITY));
            for (String entry : team.getEntries()) {
                TeamUtils.entryToTeam.putIfAbsent(entry, teamName);
            }
        }

        SchedulerAdapterFactory.get().runTaskLater(() -> {
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
        Runnable install = () -> {
            if (ch.pipeline().get(HANDLER_NAME) != null) return;
            ch.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    if (!PacketManager.onPacketSend(player, msg, ctx, promise)) {
                        super.write(ctx, msg, promise);
                    }
                }
            });
        };
        if (ch.eventLoop().inEventLoop()) {
            install.run();
        } else {
            ch.eventLoop().execute(install);
        }
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