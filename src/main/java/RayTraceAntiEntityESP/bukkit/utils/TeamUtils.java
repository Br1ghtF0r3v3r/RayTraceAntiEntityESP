package RayTraceAntiEntityESP.bukkit.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Team;

import java.util.concurrent.ConcurrentHashMap;

public class TeamUtils {

    public static final ConcurrentHashMap<String, NamedTextColor> teamColors = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamPrefixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamSuffixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> entryToTeam = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Team.OptionStatus> teamVisibilities = new ConcurrentHashMap<>();

    public static Team.OptionStatus getTeamVisibility(Entity entity) {
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return Team.OptionStatus.ALWAYS;
        Team.OptionStatus status = teamVisibilities.get(teamName);
        return status != null ? status : Team.OptionStatus.ALWAYS;
    }

    public static String getEntryTeamName(Entity entity) {
        return entryToTeam.get(entity.getScoreboardEntryName());
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

    public static Component getTeamSuffix(Entity entity) {
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return null;
        return teamSuffixes.get(teamName);
    }

    public static String getTeamName(Entity entity) {
        return entryToTeam.get(entity.getScoreboardEntryName());
    }
}