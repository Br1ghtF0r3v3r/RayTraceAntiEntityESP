package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SetScorePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (msg instanceof ClientboundSetScorePacket packet) {
            PacketManager.objectiveScores
                    .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(packet.objectiveName(), k -> ConcurrentHashMap.newKeySet())
                    .add(packet.owner());
            ctx.write(msg, promise);
            return true;
        }
        if (msg instanceof ClientboundResetScorePacket(String owner, String objectiveName)) {
            Map<String, Set<String>> perObjective = PacketManager.objectiveScores.get(viewer.getUniqueId());
            if (perObjective != null) {
                if (objectiveName == null) {
                    for (Set<String> entries : perObjective.values()) entries.remove(owner);
                } else {
                    Set<String> entries = perObjective.get(objectiveName);
                    if (entries != null) entries.remove(owner);
                }
            }
            ctx.write(msg, promise);
            return true;
        }
        return false;
    }
}