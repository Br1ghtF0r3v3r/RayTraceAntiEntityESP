package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.engine.DebugVertexRenderer;
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

        PacketManager.removeBypass(playerUUID);

        for (ServerPlayer sp : net.minecraft.server.MinecraftServer.getServer().getPlayerList().getPlayers()) {
            PacketManager.removeHiddenBypass(sp.getUUID(), playerUUID);
        }
        PacketManager.clearBypassForViewer(playerUUID);

        if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplayForEntity(playerUUID);
        if (isDebugEnabled) DebugVertexRenderer.removeDisplayForEntity(playerUUID);
        VisibilityUtils.clearViewer(viewerEntityId);
        RayTraceEngine.clearViewerCache(playerUUID);
    }

    public static void connectionCloseHandler(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isDisplayNameEnabled) NametagCloneRenderer.removeDisplay(playerUUID);
            if (isDebugEnabled) DebugVertexRenderer.removeDisplay(playerUUID);
        }, 0L);
    }

    public static void playerJoinHandler(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        injectPlayer(player);
        if (player.hasPermission("raytrace_anti_entity_esp.bypass")) {
            PacketManager.addBypass(playerUUID);
        }

        org.bukkit.scoreboard.Objective obj =
                Bukkit.getScoreboardManager().getMainScoreboard()
                        .getObjective(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
        if (obj != null) {
            PacketManager.belowNameObjective.put(playerUUID, obj.getName());
        }
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
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                PacketManager.onPacketSend(player, msg, ctx, promise);
            }
        });
    }

    private static Channel getChannel(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        return nmsPlayer.connection.connection.channel;
    }

}
