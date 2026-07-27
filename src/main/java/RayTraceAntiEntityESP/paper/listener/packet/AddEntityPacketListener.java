package RayTraceAntiEntityESP.paper.listener.packet;

import RayTraceAntiEntityESP.paper.config.Config;
import RayTraceAntiEntityESP.paper.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.paper.engine.RayTraceEngine;
import RayTraceAntiEntityESP.paper.listener.PacketListener;
import RayTraceAntiEntityESP.paper.listener.PacketManager;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.nms.parsed.ParsedAddEntity;
import RayTraceAntiEntityESP.paper.utils.EntityIdentityCache;
import RayTraceAntiEntityESP.paper.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.paper.Main.plugin;

public class AddEntityPacketListener extends PacketListener {
    public static final ConcurrentHashMap<UUID, Set<UUID>> pendingHides = new ConcurrentHashMap<>();

    public static void drainPendingHides() {
        if (!Config.isCheckingEnabled) return;
        if (pendingHides.isEmpty()) return;
        pendingHides.forEach((viewerUUID, entityUUIDs) -> {
            if (PacketManager.isBypassed(viewerUUID)) {
                pendingHides.remove(viewerUUID);
                return;
            }
            Player viewer = Bukkit.getPlayer(viewerUUID);
            if (viewer == null) {
                pendingHides.remove(viewerUUID);
                return;
            }
            entityUUIDs.removeIf(entityUUID -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (entity == null) return false;

                if (!RayTraceEngine.isAntiEntity(entity)) {
                    PacketManager.addShowBypass(viewerUUID, entityUUID);
                    viewer.hideEntity(plugin, entity);
                    viewer.showEntity(plugin, entity);
                    return true;
                }

                VisibilityUtils.setHidden(viewer, entity);
                if (Config.isCheckingEnabled && Config.isDisplayNameEnabled) {
                    List<Object> outbox = new ArrayList<>();
                    NametagCloneRenderer.applyDisplay(viewer, entity, outbox);
                    if (!outbox.isEmpty()) {
                        NmsAdapterFactory.get().sendBundled(viewer, outbox);
                    }
                }
                return true;
            });
            if (entityUUIDs.isEmpty()) pendingHides.remove(viewerUUID);
        });
    }

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        ParsedAddEntity parsed = NmsAdapterFactory.get().parseAddEntity(msg);
        if (parsed == null) return false;

        int entityId = parsed.entityId();

        if (PacketManager.isSyntheticEntity(entityId)) {
            ctx.write(msg, promise);
            return true;
        }

        UUID entityUUID = parsed.uuid();
        EntityIdentityCache.register(entityId, entityUUID, parsed.isPlayer());

        if (viewer.getUniqueId().equals(entityUUID)) {
            ctx.write(msg, promise);
            return true;
        }

        VisibilityUtils.clearExternallyHidden(viewer.getEntityId(), entityId);

        if (PacketManager.isBypassed(viewer.getUniqueId())) {
            ctx.write(msg, promise);
            return true;
        }

        if (PacketManager.consumeShowBypass(viewer.getUniqueId(), entityUUID)) {
            ctx.write(msg, promise);
            return true;
        }
        if (!Config.isCheckingEnabled) {
            ctx.write(msg, promise);
            return true;
        }

        pendingHides
                .computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(entityUUID);
        return true;
    }
}
