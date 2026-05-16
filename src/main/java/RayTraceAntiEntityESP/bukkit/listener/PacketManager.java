package RayTraceAntiEntityESP.bukkit.listener;

import RayTraceAntiEntityESP.bukkit.listener.packet.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.world.scores.Team;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team.OptionStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketManager {

    private static final ConcurrentHashMap<UUID, Set<UUID>> showBypass = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, Set<UUID>> hiddenBypass = new ConcurrentHashMap<>();

    public static void addShowBypass(UUID viewerUUID, UUID entityUUID) {
        showBypass.computeIfAbsent(viewerUUID, k -> ConcurrentHashMap.newKeySet()).add(entityUUID);
    }

    public static boolean consumeShowBypass(UUID viewerUUID, UUID entityUUID) {
        Set<UUID> set = showBypass.get(viewerUUID);
        return set != null && set.remove(entityUUID);
    }

    public static void cancelShowBypass(UUID viewerUUID, UUID entityUUID) {
        Set<UUID> set = showBypass.get(viewerUUID);
        if (set != null) set.remove(entityUUID);
    }

    public static void addHiddenBypass(UUID viewerUUID, UUID entityUUID) {
        hiddenBypass.computeIfAbsent(viewerUUID, k -> ConcurrentHashMap.newKeySet()).add(entityUUID);
    }

    public static void removeHiddenBypass(UUID viewerUUID, UUID entityUUID) {
        Set<UUID> set = hiddenBypass.get(viewerUUID);
        if (set != null) set.remove(entityUUID);
    }

    public static boolean isHiddenBypassed(UUID viewerUUID, UUID entityUUID) {
        Set<UUID> set = hiddenBypass.get(viewerUUID);
        return set != null && set.contains(entityUUID);
    }

    public static void clearBypassForViewer(UUID viewerUUID) {
        showBypass.remove(viewerUUID);
        hiddenBypass.remove(viewerUUID);
    }

    public static void clearAllBypasses() {
        showBypass.clear();
        hiddenBypass.clear();
    }

    private static volatile Set<UUID> bypassPlayers = new HashSet<>();

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
        Set<UUID> players = bypassPlayers;
        return !players.isEmpty() && players.contains(uuid);
    }

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

    private static final List<PacketListener> listeners = List.of(
            new AddEntityPacketListener(),
            new PlayerInfoRemovePacketListener(),
            new PlayerInfoUpdatePacketListener(),
            new SetEntityDataPacketListener(),
            new SetPlayerTeamPacketListener(),
            new SetDisplayObjectivePacketListener(),
            new SetObjectivePacketListener()
    );

    public static boolean onPacketSend(Player viewer, Object msg, ChannelHandlerContext ctx, ChannelPromise promise) {
        for (PacketListener listener : listeners) {
            if (listener.onPacketSend(viewer, msg, ctx, promise)) return true;
        }
        return false;
    }
}