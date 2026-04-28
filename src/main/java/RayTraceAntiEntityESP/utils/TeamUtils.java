package RayTraceAntiEntityESP.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamUtils {

    public static final Map<String, NamedTextColor> teamColors = new ConcurrentHashMap<>();
    public static final Map<String, Component> teamPrefixes = new ConcurrentHashMap<>();
    public static final Map<String, String> entryToTeam = new ConcurrentHashMap<>();

    public static Team getTeam(Entity entity) {
        return Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entity.getScoreboardEntryName());
    }

    public static Team.OptionStatus getTeamVisibility(Team team) {
        if (team == null) return Team.OptionStatus.ALWAYS;
        return team.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

    public static NamedTextColor getTeamColor(Entity entity) {
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return null;
        return teamColors.get(teamName);
    }

    public static Component getTeamPrefix(Entity entity) {
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return null;
        return teamPrefixes.get(teamName);
    }

}