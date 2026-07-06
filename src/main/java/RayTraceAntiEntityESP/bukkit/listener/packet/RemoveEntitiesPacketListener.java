package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RemoveEntitiesPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundRemoveEntitiesPacket packet)) return false;

        int viewerId = viewer.getEntityId();
        List<Packet<? super ClientGamePacketListener>> outbox = null;

        for (int entityId : packet.getEntityIds()) {
            if (PacketManager.isFakeEntity(entityId)) continue;
            if (entityId == viewerId) continue;

            if (PacketManager.consumeDestroyBypass(viewer.getUniqueId(), entityId)) {
                continue;
            }

            ServerPlayer sp = ((CraftPlayer) viewer).getHandle();
            net.minecraft.world.entity.Entity nmsTarget = sp.level().getEntity(entityId);
            if (nmsTarget == null) {
                continue;
            }
            Entity target = nmsTarget.getBukkitEntity();
            if (target instanceof Player p && !p.isOnline()) continue;

            if (VisibilityUtils.isHidden(viewerId, entityId)) {
                VisibilityUtils.setNotHiddenSilently(viewerId, entityId);
                if (Config.isDisplayNameEnabled) {
                    if (outbox == null) outbox = new ArrayList<>();
                    NametagCloneRenderer.removeDisplay(viewer.getUniqueId(), target.getUniqueId(), outbox);
                }
            }
            VisibilityUtils.markExternallyHidden(viewerId, entityId);
        }

        if (outbox != null && !outbox.isEmpty()) {
            ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundBundlePacket(outbox));
        }

        ctx.write(msg, promise);
        return true;
    }
}