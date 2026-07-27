package RayTraceAntiEntityESP.paper.listener.packet;

import RayTraceAntiEntityESP.paper.listener.PacketListener;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.nms.parsed.ParsedSetDisplayObjective;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import static RayTraceAntiEntityESP.paper.listener.PacketManager.belowNameObjective;

public class SetDisplayObjectivePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedSetDisplayObjective parsed = NmsAdapterFactory.get().parseSetDisplayObjective(msg);
        if (parsed == null) return false;

        if (parsed.slotName().equals("BELOW_NAME")) {
            String objName = parsed.objectiveName();
            if (objName == null || objName.isEmpty()) {
                belowNameObjective.remove(viewer.getUniqueId());
            } else {
                belowNameObjective.put(viewer.getUniqueId(), objName);
            }
        }
        ctx.write(msg, promise);
        return true;
    }
}
