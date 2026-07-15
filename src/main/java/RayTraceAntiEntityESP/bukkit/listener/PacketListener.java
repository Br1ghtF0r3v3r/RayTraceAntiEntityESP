package RayTraceAntiEntityESP.bukkit.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

public abstract class PacketListener {
    public abstract boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise);
}
