package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getEntryTeamName;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getTeamVisibility;

public class VisibilityUtils {

    private static final Int2ObjectOpenHashMap<IntSet> hiddenByViewer = new Int2ObjectOpenHashMap<>();

    private static IntSet getOrCreate(int viewerId) {
        IntSet set = hiddenByViewer.get(viewerId);
        if (set == null) {
            set = new IntOpenHashSet();
            hiddenByViewer.put(viewerId, set);
        }
        return set;
    }

    public static void setHidden(Player player, Entity entity) {
        getOrCreate(player.getEntityId()).add(entity.getEntityId());
        PacketManager.addHiddenBypass(player.getUniqueId(), entity.getUniqueId());
        player.hideEntity(plugin, entity);
    }

    public static void setNotHidden(Player player, Entity entity) {
        IntSet set = hiddenByViewer.get(player.getEntityId());
        if (set != null) set.remove(entity.getEntityId());
        PacketManager.removeHiddenBypass(player.getUniqueId(), entity.getUniqueId());
        PacketManager.addShowBypass(player.getUniqueId(), entity.getUniqueId());
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
        IntSet set = hiddenByViewer.get(viewerId);
        return set != null && set.contains(targetId);
    }

    public static void clearViewer(int viewerEntityId) {
        hiddenByViewer.remove(viewerEntityId);
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

    private static net.minecraft.world.scores.PlayerTeam getOrBuildNmsTeam(String teamName) {
        net.minecraft.world.scores.Scoreboard nmsScoreboard =
                net.minecraft.server.MinecraftServer.getServer().getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = nmsScoreboard.getPlayerTeam(teamName);
        if (team != null) return team;
        return new net.minecraft.world.scores.PlayerTeam(nmsScoreboard, teamName);
    }
}