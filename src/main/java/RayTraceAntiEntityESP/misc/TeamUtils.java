package RayTraceAntiEntityESP.misc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

public class TeamUtils {
    public static org.bukkit.scoreboard.Team getTeam(Entity entity) {
        return Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entity.getScoreboardEntryName());
    }

    public static org.bukkit.scoreboard.Team.OptionStatus getTeamVisibility(org.bukkit.scoreboard.Team team) {
        if (team == null) return org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
        return team.getOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY);
    }

}
