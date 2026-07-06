package RayTraceAntiEntityESP.bukkit.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamUtils {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // ---- Global team state, derived from packets + Bukkit scoreboard fallback ----
    public static final ConcurrentHashMap<String, NamedTextColor> teamColors = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamPrefixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamSuffixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> entryToTeam = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Team.OptionStatus> teamVisibilities = new ConcurrentHashMap<>();

    // ---- Per-viewer overrides (e.g. PacketEvents bridge) ----
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

    private static boolean isEmptyComponent(Component c) {
        if (c == null) return true;
        return PLAIN.serialize(c).isEmpty();
    }

    private static void ensureTeamInfoFromBukkit(String entry) {
        if (entryToTeam.containsKey(entry)) return;
        try {
            Team bukkitTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entry);
            if (bukkitTeam == null) return;
            String teamName = bukkitTeam.getName();
            entryToTeam.put(entry, teamName);
            if (!teamColors.containsKey(teamName)) {
                try {
                    TextColor textColor = bukkitTeam.color();
                    if (textColor instanceof NamedTextColor namedColor) {
                        teamColors.put(teamName, namedColor);
                    }
                } catch (Exception ignored) {
                }
            }
            if (isEmptyComponent(teamPrefixes.get(teamName))) {
                try {
                    Component prefix = bukkitTeam.prefix();
                    if (!isEmptyComponent(prefix)) {
                        teamPrefixes.put(teamName, prefix);
                    }
                } catch (Exception ignored) {
                }
            }
            if (isEmptyComponent(teamSuffixes.get(teamName))) {
                try {
                    Component suffix = bukkitTeam.suffix();
                    if (!isEmptyComponent(suffix)) {
                        teamSuffixes.put(teamName, suffix);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static Team.OptionStatus getTeamVisibility(Entity entity) {
        String teamName = entryToTeam.get(entity.getScoreboardEntryName());
        if (teamName == null) {
            ensureTeamInfoFromBukkit(entity.getScoreboardEntryName());
            teamName = entryToTeam.get(entity.getScoreboardEntryName());
        }
        if (teamName == null) return Team.OptionStatus.ALWAYS;
        Team.OptionStatus status = teamVisibilities.get(teamName);
        return status != null ? status : Team.OptionStatus.ALWAYS;
    }

    public static String getEntryTeamName(Entity entity) {
        String entry = entity.getScoreboardEntryName();
        String teamName = entryToTeam.get(entry);
        if (teamName == null) {
            ensureTeamInfoFromBukkit(entry);
            teamName = entryToTeam.get(entry);
        }
        return teamName;
    }

    public static NamedTextColor getTeamColor(Player viewer, Entity entity) {
        String entry = entity.getScoreboardEntryName();
        UUID viewerId = viewer.getUniqueId();

        ViewerTeamState state = viewerOverrides.get(viewerId);
        if (state != null) {
            String team = state.entryToTeam.get(entry);
            if (team != null) {
                NamedTextColor color = state.teamColors.get(team);
                if (color != null) return color;
            }
        }

        ensureTeamInfoFromBukkit(entry);
        String teamName = entryToTeam.get(entry);
        if (teamName != null) {
            return teamColors.get(teamName);
        }
        return null;
    }

    public static Component getTeamPrefix(Player viewer, Entity entity) {
        String entry = entity.getScoreboardEntryName();
        UUID viewerId = viewer.getUniqueId();

        ViewerTeamState state = viewerOverrides.get(viewerId);
        if (state != null) {
            String team = state.entryToTeam.get(entry);
            if (team != null) {
                Component prefix = state.teamPrefixes.get(team);
                if (!isEmptyComponent(prefix)) return prefix;
            }
        }

        ensureTeamInfoFromBukkit(entry);
        String teamName = entryToTeam.get(entry);
        if (teamName != null) {
            Component prefix = teamPrefixes.get(teamName);
            if (!isEmptyComponent(prefix)) return prefix;
        }
        return null;
    }

    public static Component getTeamSuffix(Player viewer, Entity entity) {
        String entry = entity.getScoreboardEntryName();
        UUID viewerId = viewer.getUniqueId();

        ViewerTeamState state = viewerOverrides.get(viewerId);
        if (state != null) {
            String team = state.entryToTeam.get(entry);
            if (team != null) {
                Component suffix = state.teamSuffixes.get(team);
                if (!isEmptyComponent(suffix)) return suffix;
            }
        }

        ensureTeamInfoFromBukkit(entry);
        String teamName = entryToTeam.get(entry);
        if (teamName != null) {
            Component suffix = teamSuffixes.get(teamName);
            if (!isEmptyComponent(suffix)) return suffix;
        }
        return null;
    }

    public static String getTeamName(Entity entity) {
        return getEntryTeamName(entity);
    }
}