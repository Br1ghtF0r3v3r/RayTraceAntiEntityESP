package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.utils.EntityIdentityCache;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemoveEntitiesPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundRemoveEntitiesPacket packet)) return false;

        int viewerId = viewer.getEntityId();
        List<Packet<? super ClientGamePacketListener>> outbox = null;

        for (int entityId : packet.getEntityIds()) {
            if (PacketManager.isSyntheticEntity(entityId)) continue;
            if (entityId == viewerId) continue;

            if (PacketManager.consumeDestroyBypass(viewer.getUniqueId(), entityId)) {
                EntityIdentityCache.remove(entityId);
                SetEntityDataPacketListener.clearEntity(entityId);
                continue;
            }

            UUID entityUUID = EntityIdentityCache.getUuid(entityId);

            if (EntityIdentityCache.isPlayer(entityId)) {
                Player target = entityUUID != null ? Bukkit.getPlayer(entityUUID) : null;
                if (target == null || !target.isOnline()) {
                    if (VisibilityUtils.isHidden(viewerId, entityId)) {
                        VisibilityUtils.setNotHiddenSilently(viewerId, entityId);
                        if (Config.isDisplayNameEnabled && entityUUID != null) {
                            if (outbox == null) outbox = new ArrayList<>();
                            NametagCloneRenderer.removeDisplay(viewer.getUniqueId(), entityUUID, outbox);
                        }
                    }
                    EntityIdentityCache.remove(entityId);
                    SetEntityDataPacketListener.clearEntity(entityId);
                    continue;
                }
            }

            if (VisibilityUtils.isHidden(viewerId, entityId)) {
                VisibilityUtils.setNotHiddenSilently(viewerId, entityId);
                if (Config.isDisplayNameEnabled && entityUUID != null) {
                    if (outbox == null) outbox = new ArrayList<>();
                    NametagCloneRenderer.removeDisplay(viewer.getUniqueId(), entityUUID, outbox);
                }
            }
            VisibilityUtils.markExternallyHidden(viewerId, entityId);
            EntityIdentityCache.remove(entityId);
            SetEntityDataPacketListener.clearEntity(entityId);
        }

        if (outbox != null && !outbox.isEmpty()) {
            ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundBundlePacket(outbox));
        }

        ctx.write(msg, promise);
        return true;
    }
}