package RayTraceAntiEntityESP.utils;

import RayTraceAntiEntityESP.config.Config;
import RayTraceAntiEntityESP.manager.engine.PacketFilterManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.utils.TeamUtils.getTeam;
import static RayTraceAntiEntityESP.utils.TeamUtils.getTeamVisibility;

public class VisibilityUtils {

    public static void setHidden(Player player, Entity entity) {
        PacketFilterManager.bypassPacketSet.add(PacketFilterManager.bypassHiddenKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);

        if (Config.isFakeDisplayNameEnabled) {
            FakeNameDisplay.applyDisplay(player, entity);
        }
    }

    public static void setNotHidden(Player player, Entity entity) {
        PacketFilterManager.bypassPacketSet.remove(PacketFilterManager.bypassHiddenKey(player, entity.getUniqueId()));
        player.hideEntity(plugin, entity);
        
        PacketFilterManager.bypassPacketSet.add(PacketFilterManager.bypassShowKey(player, entity.getUniqueId()));
        player.showEntity(plugin, entity);

        if (Config.isFakeDisplayNameEnabled) {
            FakeNameDisplay.removeDisplay(player, entity);
        }
    }

    public static boolean isNameVisible(Player viewer, Entity entity) {
        if (entity.isInvisible()) return false;

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
