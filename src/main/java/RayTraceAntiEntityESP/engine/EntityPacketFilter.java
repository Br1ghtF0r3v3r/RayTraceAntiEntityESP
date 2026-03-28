package RayTraceAntiEntityESP.engine;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.entity.Player;

public class EntityPacketFilter extends PacketListenerAbstract {
    @Override
    public void onPacketSend(PacketSendEvent event) {
        // --- CASE 1: SERVER IS TRYING TO SPAWN AN ENTITY ---
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(event);
            Player viewer = (Player) event.getPlayer();
            int entityId = spawnPacket.getEntityId();

            if (viewer.getEntityId() == entityId) return; // Don't hide the player from themselves

            // Check if our raycast says it SHOULD be hidden
            if (VisibilityManager.INSTANCE.isHidden(viewer, entityId)) {
                event.setCancelled(true);
                // Sync our map: The client is now missing this entity
                VisibilityManager.INSTANCE.setHidden(viewer, entityId, true);
            }
        }

        // --- CASE 2: SERVER IS NATURALLY UNLOADING ENTITIES ---
        else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(event);
            Player viewer = (Player) event.getPlayer();

            for (int entityId : destroyPacket.getEntityIds()) {
                // If the server destroys it, it's gone from the client anyway.
                // We reset our 'hidden' state so we don't desync.
                VisibilityManager.INSTANCE.setHidden(viewer, entityId, false);
            }
        }
    }
}
