package RayTraceAntiEntityESP.paper.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TeamUtils {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<Component, Boolean> emptyComponentCache = Collections.synchronizedMap(new IdentityHashMap<>());
    public static final ConcurrentHashMap<String, NamedTextColor> teamColors = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamPrefixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Component> teamSuffixes = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> entryToTeam = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Team.OptionStatus> teamVisibilities = new ConcurrentHashMap<>();

    public record TeamInfo(NamedTextColor color, Component prefix, Component suffix) {}

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

    public static void putViewerTeamInfo(UUID viewer, String teamName, NamedTextColor color, Component prefix, Component suffix) {
        ViewerTeamState state = viewerState(viewer, true);
        putOrRemove(state.teamColors, teamName, color);
        putOrRemove(state.teamPrefixes, teamName, isEmptyComponent(prefix) ? null : prefix);
        putOrRemove(state.teamSuffixes, teamName, isEmptyComponent(suffix) ? null : suffix);
    }

    public static void removeViewerTeam(UUID viewer, String teamName) {
        ViewerTeamState state = viewerState(viewer, false);
        if (state == null) return;
        state.teamColors.remove(teamName);
        state.teamPrefixes.remove(teamName);
        state.teamSuffixes.remove(teamName);
        state.entryToTeam.values().removeIf(teamName::equals);
    }

    public static Component decorateName(String teamName, String plainName) {
        if (teamName == null) return null;

        NamedTextColor color = teamColors.get(teamName);
        Component prefix = teamPrefixes.get(teamName);
        Component suffix = teamSuffixes.get(teamName);
        boolean hasPrefix = prefix != null;
        boolean hasSuffix = suffix != null;
        if (color == null && !hasPrefix && !hasSuffix) return null;

        Component name = Component.text(plainName);
        if (color != null) name = name.color(color);
        if (hasPrefix) name = prefix.append(name);
        if (hasSuffix) name = name.append(suffix);

        return name;
    }

    public static Component decorateName(Player viewer, Entity entity, String plainName) {
        NamedTextColor color = getTeamColor(viewer, entity);
        Component prefix = getTeamPrefix(viewer, entity);
        Component suffix = getTeamSuffix(viewer, entity);
        boolean hasPrefix = prefix != null;
        boolean hasSuffix = suffix != null;
        if (color == null && !hasPrefix && !hasSuffix) return null;

        Component name = Component.text(plainName);
        if (color != null) name = name.color(color);
        if (hasPrefix) name = prefix.append(name);
        if (hasSuffix) name = name.append(suffix);

        return name;
    }

    public static boolean isEmptyComponent(Component c) {
        if (c == null) return true;
        Boolean cached = emptyComponentCache.get(c);
        if (cached != null) return cached;
        boolean empty = PLAIN.serialize(c).isEmpty();
        emptyComponentCache.put(c, empty);
        return empty;
    }

    public static <K, V> void putOrRemove(Map<K, V> map, K key, V value) {
        if (value != null) map.put(key, value);
        else map.remove(key);
    }

    public static void clearAll() {
        teamColors.clear();
        teamPrefixes.clear();
        teamSuffixes.clear();
        entryToTeam.clear();
        teamVisibilities.clear();
        viewerOverrides.clear();
        emptyComponentCache.clear();
    }

    private static void ensureTeamInfoFromBukkit(String entry) {
        if (entryToTeam.containsKey(entry)) return;
        if (!Bukkit.isPrimaryThread()) return;
        try {
            Team bukkitTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entry);
            if (bukkitTeam == null) return;
            String teamName = bukkitTeam.getName();
            entryToTeam.put(entry, teamName);

            if (!teamColors.containsKey(teamName)) {
                TextColor color = safeGet(bukkitTeam::color);
                if (color instanceof NamedTextColor named) teamColors.put(teamName, named);
            }
            if (isEmptyComponent(teamPrefixes.get(teamName))) {
                Component prefix = safeGet(bukkitTeam::prefix);
                if (!isEmptyComponent(prefix)) teamPrefixes.put(teamName, prefix);
            }
            if (isEmptyComponent(teamSuffixes.get(teamName))) {
                Component suffix = safeGet(bukkitTeam::suffix);
                if (!isEmptyComponent(suffix)) teamSuffixes.put(teamName, suffix);
            }
        } catch (Exception ignored) {
        }
    }

    private static <T> T safeGet(Callable<T> supplier) {
        try {
            return supplier.call();
        } catch (Exception e) {
            return null;
        }
    }

    public static Team.OptionStatus getTeamVisibility(Entity entity) {
        String teamName = getEntryTeamName(entity);
        if (teamName == null) return Team.OptionStatus.ALWAYS;
        Team.OptionStatus status = teamVisibilities.get(teamName);
        return status != null ? status : Team.OptionStatus.ALWAYS;
    }

    public static String getEntryTeamName(String entry) {
        String teamName = entryToTeam.get(entry);
        if (teamName == null) {
            ensureTeamInfoFromBukkit(entry);
            teamName = entryToTeam.get(entry);
        }
        return teamName;
    }

    public static String getEntryTeamName(Entity entity) {
        return getEntryTeamName(entity.getScoreboardEntryName());
    }

    public static NamedTextColor getTeamColor(Player viewer, Entity entity) {
        return resolveTeamValue(viewer, entity, s -> s.teamColors, teamColors, Objects::nonNull);
    }

    public static Component getTeamPrefix(Player viewer, Entity entity) {
        return resolveTeamValue(viewer, entity, s -> s.teamPrefixes, teamPrefixes, Objects::nonNull);
    }

    public static Component getTeamSuffix(Player viewer, Entity entity) {
        return resolveTeamValue(viewer, entity, s -> s.teamSuffixes, teamSuffixes, Objects::nonNull);
    }

    public static TeamInfo resolveTeamInfo(Player viewer, Entity entity) {
        String entry = entity.getScoreboardEntryName();
        ViewerTeamState state = viewerOverrides.get(viewer.getUniqueId());
        String viewerTeam = state != null ? state.entryToTeam.get(entry) : null;
        String globalTeam = getEntryTeamName(entry);

        NamedTextColor color = resolveFromNames(state, viewerTeam, globalTeam, s -> s.teamColors, teamColors, Objects::nonNull);
        Component prefix = resolveFromNames(state, viewerTeam, globalTeam, s -> s.teamPrefixes, teamPrefixes, Objects::nonNull);
        Component suffix = resolveFromNames(state, viewerTeam, globalTeam, s -> s.teamSuffixes, teamSuffixes, Objects::nonNull);
        return new TeamInfo(color, prefix, suffix);
    }

    private static <T> T resolveFromNames(
            ViewerTeamState state,
            String viewerTeam,
            String globalTeam,
            Function<ViewerTeamState, ConcurrentHashMap<String, T>> viewerMap,
            ConcurrentHashMap<String, T> globalMap,
            Predicate<T> isUsable
    ) {
        if (state != null && viewerTeam != null) {
            T value = viewerMap.apply(state).get(viewerTeam);
            if (isUsable.test(value)) return value;
        }
        if (globalTeam != null) {
            T value = globalMap.get(globalTeam);
            if (isUsable.test(value)) return value;
        }
        return null;
    }

    private static <T> T resolveTeamValue(
            Player viewer,
            Entity entity,
            Function<ViewerTeamState, ConcurrentHashMap<String, T>> viewerMap,
            ConcurrentHashMap<String, T> globalMap,
            Predicate<T> isUsable
    ) {
        String entry = entity.getScoreboardEntryName();

        ViewerTeamState state = viewerOverrides.get(viewer.getUniqueId());
        if (state != null) {
            String team = state.entryToTeam.get(entry);
            if (team != null) {
                T value = viewerMap.apply(state).get(team);
                if (isUsable.test(value)) return value;
            }
        }

        String teamName = getEntryTeamName(entry);
        if (teamName != null) {
            T value = globalMap.get(teamName);
            if (isUsable.test(value)) return value;
        }
        return null;
    }
}