package RayTraceAntiEntityESP.bukkit.utils;

import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getTeam;
import static RayTraceAntiEntityESP.bukkit.utils.TeamUtils.getTeamVisibility;

public class VisibilityUtils {

    public static void setHidden(Player player, Entity entity) {
        PacketManager.bypassPacketSet.add(PacketManager.bypassHiddenKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);

    }

    public static void setNotHidden(Player player, Entity entity) {
        PacketManager.bypassPacketSet.remove(PacketManager.bypassHiddenKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);

        PacketManager.bypassPacketSet.add(PacketManager.bypassShowKey(player, entity.getUniqueId()));
        player.showEntity(plugin, entity);

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
}
