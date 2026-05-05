package RayTraceAntiEntityESP.bukkit.listener;

import RayTraceAntiEntityESP.bukkit.listener.packet.AddEntityPacketListener;
import RayTraceAntiEntityESP.bukkit.listener.packet.PlayerInfoRemovePacketListener;
import RayTraceAntiEntityESP.bukkit.listener.packet.SetDisplayObjectivePacketListener;
import RayTraceAntiEntityESP.bukkit.listener.packet.SetEntityDataPacketListener;
import RayTraceAntiEntityESP.bukkit.listener.packet.SetObjectivePacketListener;
import RayTraceAntiEntityESP.bukkit.listener.packet.SetPlayerTeamPacketListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.world.scores.Team;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.scoreboard.Team.OptionStatus;


public class PacketManager {

    public record BypassKey(UUID viewer, UUID entity, boolean show) {
    }

    public static final Set<BypassKey> bypassPacketSet = ConcurrentHashMap.newKeySet();
    private static volatile Set<UUID> bypassPlayers = new HashSet<>();
    public static final ConcurrentHashMap<UUID, Set<Integer>> glowingEntities = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<UUID, String> belowNameObjective = new ConcurrentHashMap<>();

    public static OptionStatus mapVisibility(Team.Visibility v) {
        if (v == null) return OptionStatus.ALWAYS;
        return switch (v) {
            case ALWAYS -> OptionStatus.ALWAYS;
            case NEVER -> OptionStatus.NEVER;
            case HIDE_FOR_OTHER_TEAMS -> OptionStatus.FOR_OWN_TEAM;
            case HIDE_FOR_OWN_TEAM -> OptionStatus.FOR_OTHER_TEAMS;
        };
    }

    public static BypassKey bypassShowKey(Player viewer, UUID entityUUID) {
        return new BypassKey(viewer.getUniqueId(), entityUUID, true);
    }

    public static BypassKey bypassHiddenKey(Player viewer, UUID entityUUID) {
        return new BypassKey(viewer.getUniqueId(), entityUUID, false);
    }

    public static void addBypass(UUID uuid) {
        Set<UUID> next = new HashSet<>(bypassPlayers);
        next.add(uuid);
        bypassPlayers = next;
    }

    public static void removeBypass(UUID uuid) {
        Set<UUID> next = new HashSet<>(bypassPlayers);
        next.remove(uuid);
        bypassPlayers = next;
    }

    public static boolean isBypassed(UUID uuid) {
        return bypassPlayers.contains(uuid);
    }


    private static final List<PacketListener> listeners = new ArrayList<>();

    public static void onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        for (PacketListener listener : listeners) {
            listener.onPacketSend(viewer, msg, ctx, promise);
        }
    }

    static {
        listeners.add(new AddEntityPacketListener());
        listeners.add(new PlayerInfoRemovePacketListener());
        listeners.add(new SetEntityDataPacketListener());
        listeners.add(new SetPlayerTeamPacketListener());
        listeners.add(new SetDisplayObjectivePacketListener());
        listeners.add(new SetObjectivePacketListener());
    }
}
