package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getEntryTeamName;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getTeamVisibility;

public class VisibilityUtils {

    private static final ConcurrentHashMap<Integer, IntSet> hiddenByViewer = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, IntSet> externallyHidden = new ConcurrentHashMap<>();

    private static IntSet getOrCreate(int viewerId) {
        return hiddenByViewer.computeIfAbsent(viewerId, k -> IntSets.synchronize(new IntOpenHashSet()));
    }

    public static void setHidden(Player player, Entity entity) {
        int viewerId = player.getEntityId();
        int entityId = entity.getEntityId();
        getOrCreate(viewerId).add(entityId);
        PacketManager.addHiddenBypass(player.getUniqueId(), entity.getUniqueId());
        PacketManager.cancelShowBypass(player.getUniqueId(), entity.getUniqueId());
        PacketManager.addDestroyBypass(player.getUniqueId(), entityId);
        player.hideEntity(plugin, entity);
    }

    public static void setNotHidden(Player player, Entity entity) {
        int viewerId = player.getEntityId();
        int entityId = entity.getEntityId();
        IntSet set = hiddenByViewer.get(viewerId);
        if (set != null) set.remove(entityId);
        PacketManager.removeHiddenBypass(player.getUniqueId(), entity.getUniqueId());
        PacketManager.addShowBypass(player.getUniqueId(), entity.getUniqueId());
        player.showEntity(plugin, entity);

        if (entity instanceof Player target) {
            String teamName = TeamUtils.getEntryTeamName(target);
            if (teamName != null) {
                ClientboundSetPlayerTeamPacket teamsPacket =
                        ClientboundSetPlayerTeamPacket.createPlayerPacket(
                                getOrBuildNmsTeam(teamName),
                                target.getScoreboardEntryName(),
                                ClientboundSetPlayerTeamPacket.Action.ADD
                        );
                ((CraftPlayer) player).getHandle().connection.send(teamsPacket);
            }
        }
    }

    public static void setNotHiddenSilently(int viewerId, int entityId) {
        IntSet set = hiddenByViewer.get(viewerId);
        if (set != null) set.remove(entityId);
    }

    public static boolean isHidden(int viewerId, int targetId) {
        IntSet set = hiddenByViewer.get(viewerId);
        return set != null && set.contains(targetId);
    }

    public static IntSet getHiddenSet(int viewerId) {
        return hiddenByViewer.get(viewerId);
    }

    public static void clearViewer(int viewerEntityId) {
        hiddenByViewer.remove(viewerEntityId);
        externallyHidden.remove(viewerEntityId);
    }

    public static void markExternallyHidden(int viewerId, int entityId) {
        externallyHidden.computeIfAbsent(viewerId, k -> new IntOpenHashSet()).add(entityId);
    }

    public static void clearExternallyHidden(int viewerId, int entityId) {
        IntSet set = externallyHidden.get(viewerId);
        if (set != null) set.remove(entityId);
    }

    public static boolean isExternallyHidden(int viewerId, int entityId) {
        IntSet set = externallyHidden.get(viewerId);
        return set != null && set.contains(entityId);
    }

    public static boolean isNameVisible(Player viewer, Entity entity) {
        if (viewer.equals(entity)) return false;
        if (!(entity instanceof Player)) {
            if (entity.customName() == null || !entity.isCustomNameVisible()) return false;
        }
        Team.OptionStatus visibility = getTeamVisibility(entity);
        if (visibility == Team.OptionStatus.ALWAYS) return true;
        if (visibility == Team.OptionStatus.NEVER) return false;
        String viewerTeam = getEntryTeamName(viewer);
        String entityTeam = getEntryTeamName(entity);
        boolean onSameTeam = viewerTeam != null && viewerTeam.equals(entityTeam);
        return (visibility == Team.OptionStatus.FOR_OWN_TEAM) == onSameTeam;
    }

    public static PlayerTeam getOrBuildNmsTeam(String teamName) {
        Scoreboard nmsScoreboard = MinecraftServer.getServer().getScoreboard();
        PlayerTeam team = nmsScoreboard.getPlayerTeam(teamName);
        if (team != null) return team;
        return new PlayerTeam(nmsScoreboard, teamName);
    }
}