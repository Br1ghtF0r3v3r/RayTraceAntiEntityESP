package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedSetObjective;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.belowNameObjective;

public class SetObjectivePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedSetObjective parsed = NmsAdapterFactory.get().parseSetObjective(msg);
        if (parsed == null) return false;

        if (parsed.method() == 1) {
            belowNameObjective.values().removeIf(parsed.objectiveName()::equals);
            for (Map<String, Set<String>> perObjective : PacketManager.objectiveScores.values()) {
                perObjective.remove(parsed.objectiveName());
            }
        }
        ctx.write(msg, promise);
        return true;
    }
}
