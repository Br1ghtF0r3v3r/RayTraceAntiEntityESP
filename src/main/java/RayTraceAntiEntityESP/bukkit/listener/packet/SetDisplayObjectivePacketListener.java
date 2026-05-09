package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.belowNameObjective;

public class SetDisplayObjectivePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        // DISPLAY_OBJECTIVE — track which objective is in the below_name slot
        if (msg instanceof ClientboundSetDisplayObjectivePacket packet) {
            if (packet.getSlot() == net.minecraft.world.scores.DisplaySlot.BELOW_NAME) {
                String objName = packet.getObjectiveName();
                if (objName == null || objName.isEmpty()) {
                    belowNameObjective.remove(viewer.getUniqueId());
                } else {
                    belowNameObjective.put(viewer.getUniqueId(), objName);
                }
            }
            ctx.write(msg, promise);
        }
        return false;
    }
}
