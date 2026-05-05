package RayTraceAntiEntityESP.bukkit.listener;

import RayTraceAntiEntityESP.bukkit.manager.events.EventManager;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;


public class EventListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        EventManager.playerJoinHandler(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        EventManager.entityDeathHandler(event);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        EventManager.connectionCloseHandler(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        EventManager.playerRespawnHandler(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        EventManager.playerQuitHandler(event);
    }

}