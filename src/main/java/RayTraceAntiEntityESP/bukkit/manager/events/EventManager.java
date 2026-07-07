package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.engine.DebugVertexRenderer;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
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
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
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

        for (ServerPlayer sp : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(playerUUID)) continue;

            if (VisibilityUtils.isHidden(sp.getId(), viewerEntityId)) {
                sp.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(playerUUID)));
            }
            PacketManager.removeHiddenBypass(sp.getUUID(), playerUUID);
        }
        PacketManager.clearBypassForViewer(playerUUID);
        TeamUtils.clearViewerOverrides(playerUUID);

        if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplayForEntity(playerUUID);
        if (isDebugEnabled) DebugVertexRenderer.removeDisplayForEntity(playerUUID);
        VisibilityUtils.clearViewer(viewerEntityId);
        RayTraceEngine.clearViewerCache(((CraftPlayer) player).getHandle().getId());
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

        Objective obj = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(DisplaySlot.BELOW_NAME);
        if (obj != null) {
            PacketManager.belowNameObjective.put(playerUUID, obj.getName());
        }

        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        Scoreboard nmsScoreboard = MinecraftServer.getServer().getScoreboard();

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
            for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
                PlayerTeam nmsTeam = nmsScoreboard.getPlayerTeam(team.getName());
                if (nmsTeam == null) continue;

                nmsPlayer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(nmsTeam, true));
                for (String member : nmsTeam.getPlayers()) {
                    nmsPlayer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                            nmsTeam, member, ClientboundSetPlayerTeamPacket.Action.ADD));
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