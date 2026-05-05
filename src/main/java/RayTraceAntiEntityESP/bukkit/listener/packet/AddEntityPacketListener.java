package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.bypassPacketSet;
import static RayTraceAntiEntityESP.bukkit.listener.PacketManager.bypassShowKey;

public class AddEntityPacketListener extends PacketListener {
    @Override
    public void onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (msg instanceof ClientboundAddEntityPacket packet) {
            int entityId = packet.getId();

            if (entityId >= 2000000 && entityId < 3000000) {
                ctx.write(msg, promise);
                return;
            } // nametag clone
            if (entityId >= 4000000 && entityId < 5000000) {
                ctx.write(msg, promise);
                return;
            } // vertices debug

            UUID entityUUID = packet.getUUID();
            if (viewer.getUniqueId().equals(entityUUID)) {
                ctx.write(msg, promise);
                return;
            }
            if (bypassPacketSet.remove(bypassShowKey(viewer, entityUUID))) {
                ctx.write(msg, promise);
                return;
            }

            // Cancel — hide immediately, RayTraceManager shows if in sight next tick
            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (entity == null) return;
                VisibilityUtils.setHidden(viewer, entity);
            });
        }
    }
}
