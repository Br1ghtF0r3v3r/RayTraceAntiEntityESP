package RayTraceAntiEntityESP.bukkit.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

public class PacketListener {
    public void onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        // This method will be overridden by specific packet listeners
    }
}
