package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getTeam;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getTeamVisibility;

public class VisibilityUtils {

    public static final Set<Long> hiddenEntities = ConcurrentHashMap.newKeySet();

    private static long hiddenKey(int viewerId, int targetId) {
        return ((long) viewerId << 32) | (targetId & 0xFFFFFFFFL);
    }

    public static void setHidden(Player player, Entity entity) {
        hiddenEntities.add(hiddenKey(player.getEntityId(), entity.getEntityId()));
        PacketManager.bypassPacketSet.add(PacketManager.bypassHiddenKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);
    }

    public static void setNotHidden(Player player, Entity entity) {
        hiddenEntities.remove(hiddenKey(player.getEntityId(), entity.getEntityId()));
        PacketManager.bypassPacketSet.remove(PacketManager.bypassHiddenKey(player, entity.getUniqueId()));
        PacketManager.bypassPacketSet.add(PacketManager.bypassShowKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);
        player.showEntity(plugin, entity);

        if (entity instanceof Player target) {
            String teamName = TeamUtils.getTeamName(target);
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

    public static boolean isHidden(int viewerId, int targetId) {
        return hiddenEntities.contains(hiddenKey(viewerId, targetId));
    }

    public static void clearViewer(int viewerEntityId) {
        hiddenEntities.removeIf(key -> (key >>> 32) == viewerEntityId);
    }

    public static boolean isNameVisible(Player viewer, Entity entity) {
        if (viewer.equals(entity)) return false;
        if (!(entity instanceof Player)) {
            if (entity.customName() == null || !entity.isCustomNameVisible()) return false;
        }
        Team viewerTeam = getTeam(viewer);
        Team entityTeam = getTeam(entity);
        boolean onSameTeam = viewerTeam != null && viewerTeam.equals(entityTeam);
        return switch (getTeamVisibility(entityTeam)) {
            case ALWAYS -> true;
            case NEVER -> false;
            case FOR_OWN_TEAM -> onSameTeam;
            case FOR_OTHER_TEAMS -> !onSameTeam;
        };
    }

    private static net.minecraft.world.scores.PlayerTeam getOrBuildNmsTeam(String teamName) {
        net.minecraft.world.scores.Scoreboard nmsScoreboard = net.minecraft.server.MinecraftServer.getServer().getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = nmsScoreboard.getPlayerTeam(teamName);
        if (team != null) return team;
        return new net.minecraft.world.scores.PlayerTeam(nmsScoreboard, teamName);
    }

}
