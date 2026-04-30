package RayTraceAntiEntityESP.bukkit.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.netty.channel.*;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import static RayTraceAntiEntityESP.bukkit.manager.events.EventManager.*;

public class EventListener implements Listener {

    private static final String HANDLER_NAME = "anti_esp_handler";

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        entityDeathManager(event);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        connectionCloseManager(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        playerRespawnManager(event);
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