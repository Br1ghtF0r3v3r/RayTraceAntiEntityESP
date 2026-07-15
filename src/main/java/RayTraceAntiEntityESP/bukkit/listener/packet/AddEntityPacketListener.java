package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.bukkit.nms.parsed.ParsedAddEntity;
import RayTraceAntiEntityESP.bukkit.utils.EntityIdentityCache;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
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

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

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
