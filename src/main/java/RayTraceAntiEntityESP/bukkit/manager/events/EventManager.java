package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.engine.DebugVertexRenderer;
import RayTraceAntiEntityESP.bukkit.utils.VersionChecker;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDebugEnabled;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDisplayNameEnabled;

public class EventManager {

    private static final String HANDLER_NAME = "anti_esp_handler";

    public static void playerQuitHandler(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        int viewerEntityId = event.getPlayer().getEntityId();

        for (ServerPlayer sp : net.minecraft.server.MinecraftServer.getServer().getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(playerUUID)) continue;

            if (VisibilityUtils.isHidden(sp.getId(), viewerEntityId)) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(java.util.List.of(playerUUID)));
            }
            PacketManager.removeHiddenBypass(sp.getUUID(), playerUUID);
        }
        PacketManager.clearBypassForViewer(playerUUID);
        PacketManager.belowNameObjective.remove(playerUUID);
        PacketManager.objectiveScores.remove(playerUUID);

        if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplayForEntity(playerUUID);
        if (isDebugEnabled) DebugVertexRenderer.removeDisplayForEntity(playerUUID);
        VisibilityUtils.clearViewer(viewerEntityId);
        RayTraceEngine.clearViewerCache(((org.bukkit.craftbukkit.entity.CraftPlayer) event.getPlayer()).getHandle().getId());
    }

    public static void connectionCloseHandler(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplay(playerUUID);
            if (isDebugEnabled) DebugVertexRenderer.removeDisplay(playerUUID);
        });
    }

    public static void playerJoinHandler(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        injectPlayer(player);
        org.bukkit.scoreboard.Objective obj =
                Bukkit.getScoreboardManager().getMainScoreboard()
                        .getObjective(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
        if (obj != null) {
            PacketManager.belowNameObjective.put(playerUUID, obj.getName());
        }
        net.minecraft.server.level.ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        net.minecraft.world.scores.Scoreboard nmsScoreboard =
                net.minecraft.server.MinecraftServer.getServer().getScoreboard();
        for (org.bukkit.scoreboard.Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            String teamName = team.getName();
            try {
                net.kyori.adventure.text.format.TextColor textColor = team.color();
                if (textColor instanceof net.kyori.adventure.text.format.NamedTextColor namedColor) {
                    RayTraceAntiEntityESP.bukkit.utils.TeamUtils.teamColors.put(teamName, namedColor);
                }
            } catch (IllegalStateException ignored) {
            }
            RayTraceAntiEntityESP.bukkit.utils.TeamUtils.teamPrefixes.put(teamName, team.prefix());
            RayTraceAntiEntityESP.bukkit.utils.TeamUtils.teamVisibilities.put(teamName,
                    team.getOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY));
            for (String entry : team.getEntries()) {
                RayTraceAntiEntityESP.bukkit.utils.TeamUtils.entryToTeam.put(entry, teamName);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.scoreboard.Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
                net.minecraft.world.scores.PlayerTeam nmsTeam = nmsScoreboard.getPlayerTeam(team.getName());
                if (nmsTeam == null) continue;
                nmsPlayer.connection.send(
                        net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(nmsTeam, true)
                );
                for (String member : nmsTeam.getPlayers()) {
                    nmsPlayer.connection.send(
                            net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createPlayerPacket(
                                    nmsTeam, member, net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.ADD
                            )
                    );
                }
            }
            if (player.isOnline() && player.hasPermission("raytrace_anti_entity_esp.admin")) {
                VersionChecker.notifyIfOutdated(player);
            }
        }, 2L);
    }

    public static void entityDeathHandler(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityUUID = entity.getUniqueId();

        if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplayForEntity(entityUUID);
        if (isDebugEnabled) DebugVertexRenderer.removeDisplayForEntity(entityUUID);
    }

    public static void playerRespawnHandler(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        int entityId = ((CraftPlayer) player).getHandle().getId();

        VisibilityUtils.clearViewer(entityId);
    }

    public static void injectPlayer(Player player) {
        Channel channel = getChannel(player);
        if (channel.pipeline().get(HANDLER_NAME) != null) return;
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (!PacketManager.onPacketSend(player, msg, ctx, promise)) {
                    super.write(ctx, msg, promise);
                }
            }
        });
    }

    private static Channel getChannel(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        return nmsPlayer.connection.connection.channel;
    }

}