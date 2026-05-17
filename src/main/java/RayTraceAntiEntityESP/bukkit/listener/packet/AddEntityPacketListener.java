package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AddEntityPacketListener extends PacketListener {
    public static final ConcurrentHashMap<UUID, Set<UUID>> pendingHides = new ConcurrentHashMap<>();

    public static void drainPendingHides() {
        if (!RayTraceAntiEntityESP.bukkit.config.Config.isCheckingEnabled) return;
        if (pendingHides.isEmpty()) return;
        pendingHides.forEach((viewerUUID, entityUUIDs) -> {
            Player viewer = Bukkit.getPlayer(viewerUUID);
            if (viewer == null) {
                pendingHides.remove(viewerUUID);
                return;
            }
            entityUUIDs.removeIf(entityUUID -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (entity == null) return false;
                VisibilityUtils.setHidden(viewer, entity);
                if (RayTraceAntiEntityESP.bukkit.config.Config.isCheckingEnabled && RayTraceAntiEntityESP.bukkit.config.Config.isDisplayNameEnabled) {
                    java.util.List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> outbox = new java.util.ArrayList<>();
                    RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer.applyDisplay(viewer, entity, outbox);
                    if (!outbox.isEmpty()) {
                        ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle().connection
                                .send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(outbox));
                    }
                }
                return true;
            });
            if (entityUUIDs.isEmpty()) pendingHides.remove(viewerUUID);
        });
    }

    @Override
    public boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!(msg instanceof ClientboundAddEntityPacket packet)) return false;

        int entityId = packet.getId();

        if (entityId >= 2000000 && entityId < 3000000) {
            ctx.write(msg, promise);
            return true;
        }
        if (entityId >= 4000000 && entityId < 5000000) {
            ctx.write(msg, promise);
            return true;
        }

        UUID entityUUID = packet.getUUID();

        if (viewer.getUniqueId().equals(entityUUID)) {
            ctx.write(msg, promise);
            return true;
        }

        if (PacketManager.consumeShowBypass(viewer.getUniqueId(), entityUUID)) {
            ctx.write(msg, promise);
            return true;
        }

        if (!RayTraceAntiEntityESP.bukkit.config.Config.isCheckingEnabled) {
            ctx.write(msg, promise);
            return true;
        }

        pendingHides
                .computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(entityUUID);

        return true;
    }
}