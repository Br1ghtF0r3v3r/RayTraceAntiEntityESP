package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.NametagCloneManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.VerticesDebugManager;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import io.papermc.paper.event.player.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isDisplayNameEnabled) {
                NametagCloneManager.removeDisplay(playerUUID);
                NametagCloneManager.removeDisplayForEntity(playerUUID);
            }
            if (isDebugEnabled) {
                VerticesDebugManager.removeDisplay(playerUUID);
                VerticesDebugManager.removeDisplayForEntity(playerUUID);
            }
        }, 20L);
    }

    public static void packetSendManager(PacketSendEvent event) {
        PacketManager.packetManager(event);
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
