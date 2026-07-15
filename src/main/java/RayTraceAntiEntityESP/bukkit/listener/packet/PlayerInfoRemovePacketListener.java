package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedPlayerInfoRemove;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerInfoRemovePacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedPlayerInfoRemove parsed = NmsAdapterFactory.get().parsePlayerInfoRemove(msg);
        if (parsed == null) return false;

        List<UUID> original = parsed.profileIds();
        List<UUID> filtered = new ArrayList<>();
        for (UUID entityUUID : original) {
            if (viewer.getUniqueId().equals(entityUUID)) continue;
            if (PacketManager.removeHiddenBypass(viewer.getUniqueId(), entityUUID)) continue;
            filtered.add(entityUUID);
        }

        if (filtered.size() == original.size()) {
            ctx.write(msg, promise);
            return true;
        }

        if (!filtered.isEmpty()) {
            NmsAdapterFactory.get().sendPacket(viewer,
                    NmsAdapterFactory.get().buildPlayerInfoRemovePacket(filtered));
        }
        return true;
    }
}
