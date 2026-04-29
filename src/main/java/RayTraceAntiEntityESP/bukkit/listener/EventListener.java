package RayTraceAntiEntityESP.bukkit.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.jspecify.annotations.NonNull;

import static RayTraceAntiEntityESP.bukkit.manager.events.EventManager.*;

public class EventListener extends PacketListenerAbstract implements Listener {

    @Override
    public void onPacketSend(@NonNull PacketSendEvent event) {
        packetSendManager(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        entityDeathManager(event);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        connectionCloseManager(event);
    }
}