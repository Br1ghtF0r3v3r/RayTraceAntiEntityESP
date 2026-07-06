package RayTraceAntiEntityESP.bukkit.listener.packet;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.listener.PacketListener;
import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
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
                    List<Packet<? super ClientGamePacketListener>> outbox = new ArrayList<>();
                    NametagCloneRenderer.applyDisplay(viewer, entity, outbox);
                    if (!outbox.isEmpty()) {
                        ((CraftPlayer) viewer).getHandle().connection
                                .send(new ClientboundBundlePacket(outbox));
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

        if (PacketManager.isFakeEntity(entityId)) {
            ctx.write(msg, promise);
            return true;
        }

        UUID entityUUID = packet.getUUID();
        if (viewer.getUniqueId().equals(entityUUID)) {
            ctx.write(msg, promise);
            return true;
        }

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