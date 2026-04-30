package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.NametagCloneManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.VerticesDebugManager;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.event.player.*;
import org.bukkit.Bukkit;
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

    public static void connectionCloseManager(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();
        if (isDisplayNameEnabled) {
            NametagCloneManager.removeDisplayForEntity(playerUUID);
        }
        if (isDebugEnabled) {
            VerticesDebugManager.removeDisplayForEntity(playerUUID);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isDisplayNameEnabled) {
                NametagCloneManager.removeDisplay(playerUUID);
            }
            if (isDebugEnabled) {
                VerticesDebugManager.removeDisplay(playerUUID);
            }
        }, 20L);
    }

    public static void packetSendManager(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        PacketManager.packetManager(viewer, msg, ctx, promise);
    }

    public static void entityDeathManager(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityUUID = entity.getUniqueId();

        if (isDisplayNameEnabled) {
            NametagCloneManager.removeDisplayForEntity(entityUUID);
        }
        if (isDebugEnabled) {
            VerticesDebugManager.removeDisplayForEntity(entityUUID);
        }
    }

}
