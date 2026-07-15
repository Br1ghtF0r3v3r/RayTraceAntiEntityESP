package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.DataItem;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedSetEntityData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.*;

public class SetEntityDataPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedSetEntityData parsed = NmsAdapterFactory.get().parseSetEntityData(msg);
        if (parsed == null) return false;

        int entityId = parsed.entityId();
        List<DataItem> items = parsed.items();

        boolean isPluginOwnedEntity = isSyntheticEntity(entityId);

        if (!isPluginOwnedEntity) {
            for (DataItem data : items) {
                if (data.index() == 0 && data.value() instanceof Byte flags) {
                    boolean glowing = (flags & 0x40) != 0;
                    boolean invisible = (flags & 0x20) != 0;

                    Set<Integer> playerSet = glowingEntities.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
                    if (glowing) playerSet.add(entityId);
                    else playerSet.remove(entityId);

                    PacketManager.invisibleCache.put(entityId, invisible);
                }
            }
        }

        ctx.write(msg, promise);
        return true;
    }

    public static void clearEntity(int entityId) {
        PacketManager.invisibleCache.remove(entityId);
        for (Set<Integer> playerSet : glowingEntities.values()) {
            playerSet.remove(entityId);
        }
    }

    public static Boolean getInvisible(int entityId) {
        return PacketManager.invisibleCache.get(entityId);
    }
}
