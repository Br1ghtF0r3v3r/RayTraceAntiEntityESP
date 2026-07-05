package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.belowNameObjective;

public class SetObjectivePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundSetObjectivePacket packet)) return false;
        if (packet.getMethod() == 1) {
            belowNameObjective.values().removeIf(packet.getObjectiveName()::equals);
            for (Map<String, Set<String>> perObjective : PacketManager.objectiveScores.values()) {
                perObjective.remove(packet.getObjectiveName());
            }
        }
        ctx.write(msg, promise);
        return true;
    }
}