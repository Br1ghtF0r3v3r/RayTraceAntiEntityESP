package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.*;

public class SetEntityDataPacketListener extends PacketListener {
    public static final ConcurrentHashMap<Integer, Boolean> invisibleCache = new ConcurrentHashMap<>();

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundSetEntityDataPacket(
                int entityId, List<SynchedEntityData.DataValue<?>> packedItems
        ))) return false;

        boolean isPluginOwnedEntity = isFakeEntity(entityId);

        if (!isPluginOwnedEntity) {
            for (SynchedEntityData.DataValue<?> data : packedItems) {
                if (data.id() == 0 && data.value() instanceof Byte flags) {
                    boolean glowing = (flags & 0x40) != 0;
                    boolean invisible = (flags & 0x20) != 0;

                    Set<Integer> playerSet = glowingEntities.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
                    if (glowing) playerSet.add(entityId);
                    else playerSet.remove(entityId);

                    invisibleCache.put(entityId, invisible);
                }
            }
        }

        ctx.write(msg, promise);
        return true;
    }

    public static void clearEntity(int entityId) {
        invisibleCache.remove(entityId);
        for (Set<Integer> playerSet : glowingEntities.values()) {
            playerSet.remove(entityId);
        }
    }
}