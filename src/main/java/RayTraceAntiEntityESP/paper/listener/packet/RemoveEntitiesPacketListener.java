package RayTraceAntiEntityESP.paper.listener.packet;

import RayTraceAntiEntityESP.paper.config.Config;
import RayTraceAntiEntityESP.paper.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.paper.engine.RayTraceEngine;
import RayTraceAntiEntityESP.paper.listener.PacketListener;
import RayTraceAntiEntityESP.paper.listener.PacketManager;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.nms.parsed.ParsedRemoveEntities;
import RayTraceAntiEntityESP.paper.utils.EntityIdentityCache;
import RayTraceAntiEntityESP.paper.utils.VisibilityUtils;
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
