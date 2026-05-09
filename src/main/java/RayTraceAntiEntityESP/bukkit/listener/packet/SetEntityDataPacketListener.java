package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.*;

public class SetEntityDataPacketListener extends PacketListener {
    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        // ENTITY_METADATA
        if (msg instanceof ClientboundSetEntityDataPacket(int entityId, List<SynchedEntityData.DataValue<?>> packedItems)) {

            boolean isNameTagClone = entityId >= 2000000 && entityId < 3000000;
            boolean isVerticesDebug = entityId >= 4000000 && entityId < 5000000;

            if (isNameTagClone || isVerticesDebug) {
                Set<Integer> playerSet = glowingEntities.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());

                for (SynchedEntityData.DataValue<?> data : packedItems) {
                    if (data.id() == 0 && data.value() instanceof Byte flags) {
                        if ((flags & 0x40) != 0) playerSet.add(entityId);
                        else playerSet.remove(entityId);
                    }
                }
            }
            ctx.write(msg, promise);
        }
        return false;
    }
}
