package RayTraceAntiEntityESP.misc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Team;

public class TeamUtils {
    public static Team getTeam(Entity entity) {
        return Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entity.getScoreboardEntryName());
    }

    public static Team.OptionStatus getTeamVisibility(Team team) {
        if (team == null) return Team.OptionStatus.ALWAYS;
        return team.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

}
