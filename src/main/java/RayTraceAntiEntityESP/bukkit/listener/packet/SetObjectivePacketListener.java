package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.belowNameObjective;

public class SetObjectivePacketListener extends PacketListener {
    @Override
    public void onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        // OBJECTIVE — when objective is removed entirely, clear it from tracking
        if (msg instanceof ClientboundSetObjectivePacket packet) {
            if (packet.getMethod() == 1) {
                String removedName = packet.getObjectiveName();
                belowNameObjective.values().removeIf(removedName::equals);
            }
            ctx.write(msg, promise);
            return;
        }

        ctx.write(msg, promise);
    }
}
