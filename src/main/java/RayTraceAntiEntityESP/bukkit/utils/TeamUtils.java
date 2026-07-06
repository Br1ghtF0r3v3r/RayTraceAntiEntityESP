package RayTraceAntiEntityESP.bukkit.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamUtils {

    // ---- Global team state, derived from the real (vanilla) scoreboard ----
    public static final ConcurrentHashMap<String, NamedTextColor> teamColors = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamPrefixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamSuffixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> entryToTeam = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Team.OptionStatus> teamVisibilities = new ConcurrentHashMap<>();

    // ---- Per-viewer overrides, e.g. from plugins that drive team color per-client via packets ----
    private static final class ViewerTeamState {
        final ConcurrentHashMap<String, String> entryToTeam = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, NamedTextColor> teamColors = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Component> teamPrefixes = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Component> teamSuffixes = new ConcurrentHashMap<>();
    }

    private static final ConcurrentHashMap<UUID, ViewerTeamState> viewerOverrides = new ConcurrentHashMap<>();

    private static ViewerTeamState viewerState(UUID viewer, boolean createIfAbsent) {
        if (createIfAbsent) return viewerOverrides.computeIfAbsent(viewer, k -> new ViewerTeamState());
        return viewerOverrides.get(viewer);
    }

    public static void clearViewerOverrides(UUID viewer) {
        viewerOverrides.remove(viewer);
    }

    public static void putViewerTeamAssignment(UUID viewer, String entry, String teamName) {
        viewerState(viewer, true).entryToTeam.put(entry, teamName);
    }

    public static void removeViewerTeamAssignment(UUID viewer, String entry) {
        ViewerTeamState state = viewerState(viewer, false);
        if (state != null) state.entryToTeam.remove(entry);
    }

    public static void putViewerTeamInfo(UUID viewer, String teamName, NamedTextColor color,
                                         Component prefix, Component suffix) {
        ViewerTeamState state = viewerState(viewer, true);
        if (color != null) state.teamColors.put(teamName, color);
        else state.teamColors.remove(teamName);
        if (prefix != null) state.teamPrefixes.put(teamName, prefix);
        else state.teamPrefixes.remove(teamName);
        if (suffix != null) state.teamSuffixes.put(teamName, suffix);
        else state.teamSuffixes.remove(teamName);
    }

    public static void removeViewerTeam(UUID viewer, String teamName) {
        ViewerTeamState state = viewerState(viewer, false);
        if (state == null) return;
        state.teamColors.remove(teamName);
        state.teamPrefixes.remove(teamName);
        state.teamSuffixes.remove(teamName);
        state.entryToTeam.values().removeIf(teamName::equals);
    }

    public static Team.OptionStatus getTeamVisibility(Entity entity) {
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return Team.OptionStatus.ALWAYS;
        Team.OptionStatus status = teamVisibilities.get(teamName);
        return status != null ? status : Team.OptionStatus.ALWAYS;
    }

    public static String getEntryTeamName(Entity entity) {
        return entryToTeam.get(entity.getScoreboardEntryName());
    }

    public static NamedTextColor getTeamColor(Player viewer, Entity entity) {
        ViewerTeamState state = viewerOverrides.get(viewer.getUniqueId());
        if (state != null) {
            String team = state.entryToTeam.get(entity.getScoreboardEntryName());
            if (team != null) {
                NamedTextColor color = state.teamColors.get(team);
                if (color != null) return color;
            }
        }
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return null;
        return teamColors.get(teamName);
    }

    public static Component getTeamPrefix(Player viewer, Entity entity) {
        ViewerTeamState state = viewerOverrides.get(viewer.getUniqueId());
        if (state != null) {
            String team = state.entryToTeam.get(entity.getScoreboardEntryName());
            if (team != null) {
                Component prefix = state.teamPrefixes.get(team);
                if (prefix != null) return prefix;
            }
        }
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return null;
        return teamPrefixes.get(teamName);
    }

    public static Component getTeamSuffix(Player viewer, Entity entity) {
        ViewerTeamState state = viewerOverrides.get(viewer.getUniqueId());
        if (state != null) {
            String team = state.entryToTeam.get(entity.getScoreboardEntryName());
            if (team != null) {
                Component suffix = state.teamSuffixes.get(team);
                if (suffix != null) return suffix;
            }
        }
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) return null;
        return teamSuffixes.get(teamName);
    }

    public static String getTeamName(Entity entity) {
        return entryToTeam.get(entity.getScoreboardEntryName());
    }
}