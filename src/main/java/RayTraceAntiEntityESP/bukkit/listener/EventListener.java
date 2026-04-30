package RayTraceAntiEntityESP.bukkit.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import static RayTraceAntiEntityESP.bukkit.manager.events.EventManager.*;

public class EventListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerJoinManager(event);
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

}