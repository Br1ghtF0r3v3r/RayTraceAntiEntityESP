package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.NametagCloneManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.VerticesDebugManager;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.event.player.*;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDebugEnabled;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDisplayNameEnabled;

public class EventManager {

    private static final String HANDLER_NAME = "anti_esp_handler";

    public static void playerQuitManager(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        int viewerEntityId = event.getPlayer().getEntityId();

        for (ServerPlayer sp : net.minecraft.server.MinecraftServer.getServer().getPlayerList().getPlayers()) {
            PacketManager.bypassPacketSet.remove(PacketManager.bypassHiddenKey(sp.getBukkitEntity(), playerUUID));
        }

        if (isDisplayNameEnabled) NametagCloneManager.removeDisplayForEntity(playerUUID);
        if (isDebugEnabled) VerticesDebugManager.removeDisplayForEntity(playerUUID);
        VisibilityUtils.clearViewer(viewerEntityId);
    }

    public static void connectionCloseManager(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isDisplayNameEnabled) NametagCloneManager.removeDisplay(playerUUID);
            if (isDebugEnabled) VerticesDebugManager.removeDisplay(playerUUID);
        }, 0L);
    }

    public static void playerJoinManager(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        injectPlayer(player);

        org.bukkit.scoreboard.Objective obj =
                Bukkit.getScoreboardManager().getMainScoreboard()
                        .getObjective(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
        if (obj != null) {
            PacketManager.belowNameObjective.put(player.getUniqueId(), obj.getName());
        }
    }

    public static void packetSendManager(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        PacketManager.packetManager(viewer, msg, ctx, promise);
    }

    public static void entityDeathManager(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityUUID = entity.getUniqueId();

        if (isDisplayNameEnabled) NametagCloneManager.removeDisplayForEntity(entityUUID);
        if (isDebugEnabled) VerticesDebugManager.removeDisplayForEntity(entityUUID);
    }

    public static void playerRespawnManager(PlayerRespawnEvent event) {
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
                packetSendManager(player, msg, ctx, promise);
            }
        });
    }

    private static Channel getChannel(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        return nmsPlayer.connection.connection.channel;
    }

}
