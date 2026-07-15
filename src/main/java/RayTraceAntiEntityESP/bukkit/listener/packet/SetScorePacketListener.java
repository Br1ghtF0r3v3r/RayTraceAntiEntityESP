package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedResetScore;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedSetScore;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SetScorePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedSetScore set = NmsAdapterFactory.get().parseSetScore(msg);
        if (set != null) {
            PacketManager.objectiveScores
                    .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(set.objectiveName(), k -> ConcurrentHashMap.newKeySet())
                    .add(set.owner());
            ctx.write(msg, promise);
            return true;
        }
        ParsedResetScore reset = NmsAdapterFactory.get().parseResetScore(msg);
        if (reset != null) {
            Map<String, Set<String>> perObjective = PacketManager.objectiveScores.get(viewer.getUniqueId());
            if (perObjective != null) {
                if (reset.objectiveName() == null) {
                    for (Set<String> entries : perObjective.values()) entries.remove(reset.owner());
                } else {
                    Set<String> entries = perObjective.get(reset.objectiveName());
                    if (entries != null) entries.remove(reset.owner());
                }
            }
            ctx.write(msg, promise);
            return true;
        }
        return false;
    }
}
