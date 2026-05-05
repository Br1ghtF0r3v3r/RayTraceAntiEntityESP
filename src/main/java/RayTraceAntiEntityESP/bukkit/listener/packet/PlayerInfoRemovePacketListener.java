package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.*;

public class PlayerInfoRemovePacketListener extends PacketListener {
    @Override
    public void onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        // PLAYER_INFO_REMOVE
        if (msg instanceof ClientboundPlayerInfoRemovePacket(List<UUID> original)) {
            List<UUID> filtered = new ArrayList<>();

            for (UUID entityUUID : original) {
                if (viewer.getUniqueId().equals(entityUUID)) continue;
                if (bypassPacketSet.contains(bypassHiddenKey(viewer, entityUUID))) continue;
                filtered.add(entityUUID);
            }

            if (filtered.size() == original.size()) {
                ctx.write(msg, promise);
                return;
            }

            if (!filtered.isEmpty()) {
                ServerPlayer nmsPlayer = ((CraftPlayer) viewer).getHandle();
                nmsPlayer.connection.send(new ClientboundPlayerInfoRemovePacket(filtered));
            }
        }
    }
}
