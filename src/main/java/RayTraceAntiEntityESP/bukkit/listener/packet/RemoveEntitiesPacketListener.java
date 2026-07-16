package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedRemoveEntities;
import RayTraceAntiEntityESP.bukkit.utils.EntityIdentityCache;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemoveEntitiesPacketListener extends PacketListener {

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedRemoveEntities parsed = NmsAdapterFactory.get().parseRemoveEntities(msg);
        if (parsed == null) return false;

        int viewerId = viewer.getEntityId();
        UUID viewerUUID = viewer.getUniqueId();
        List<Object> outbox = null;

        for (int entityId : parsed.entityIds()) {
            if (PacketManager.isSyntheticEntity(entityId)) continue;
            if (entityId == viewerId) continue;

            if (PacketManager.consumeDestroyBypass(viewerUUID, entityId)) {
                cleanupEntityState(viewerId, entityId);
                continue;
            }

            UUID entityUUID = EntityIdentityCache.getUuid(entityId);
            boolean wasHidden = VisibilityUtils.isHidden(viewerId, entityId);

            if (EntityIdentityCache.isPlayer(entityId)) {
                Player target = entityUUID != null ? Bukkit.getPlayer(entityUUID) : null;
                if (target == null || !target.isOnline()) {
                    if (wasHidden && entityUUID != null) {
                        NmsAdapterFactory.get().sendPacket(viewer,
                                NmsAdapterFactory.get().buildPlayerInfoRemovePacket(List.of(entityUUID)));
                    }
                }
            }

            if (wasHidden) {
                VisibilityUtils.setNotHiddenSilently(viewerId, entityId);
                if (Config.isDisplayNameEnabled && entityUUID != null) {
                    if (outbox == null) outbox = new ArrayList<>();
                    NametagCloneRenderer.removeDisplay(viewerUUID, entityUUID, outbox);
                }
            }

            VisibilityUtils.markExternallyHidden(viewerId, entityId);
            cleanupEntityState(viewerId, entityId);
        }

        if (outbox != null && !outbox.isEmpty()) {
            NmsAdapterFactory.get().sendBundled(viewer, outbox);
        }

        ctx.write(msg, promise);
        return true;
    }

    private static void cleanupEntityState(int viewerId, int entityId) {
        EntityIdentityCache.remove(entityId);
        SetEntityDataPacketListener.clearEntity(entityId);
        RayTraceEngine.onEntityRemovedFromViewer(viewerId, entityId);
    }
}
