package RayTraceAntiEntityESP.listener;

import RayTraceAntiEntityESP.manager.engine.RayTraceManager;
import RayTraceAntiEntityESP.utils.VisibilityUtils;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.Main.plugin;

public class GlowingPacketListener extends PacketListenerAbstract {
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);

            Player viewer = (Player) event.getPlayer();
            int entityId = packet.getEntityId();
            Entity target = SpigotConversionUtil.getEntityById(viewer.getWorld(), entityId);


            for (var data : packet.getEntityMetadata()) {
                if (data.getIndex() == 0) {
                    Object value = data.getValue();

                    if (value instanceof Byte flags) {
                        boolean isGlowing = (flags & 0x40) != 0; // see flags https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Entity
                        if (isGlowing) {
                            assert target != null;
                            if (!viewer.canSee(target)) {
                                viewer.showEntity(plugin, target); // TODO: pls help need move elsewhere, might conflict
                            }
                        }
                    }
                }
            }
        }
    }
}
