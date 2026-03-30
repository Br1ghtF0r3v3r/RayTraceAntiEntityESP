package RayTraceAntiEntityESP.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.config.Config.*;

public class NameDisplayUtils {

    private static BukkitTask task;
    public static final Map<UUID, Map<UUID, TextDisplay>> nameplates = new HashMap<>();
    public static long currentFakeDisplayNamePeriodTicks;

    public static void startFakeNameDisplayUpdating() {
        if (task != null) task.cancel();
        currentFakeDisplayNamePeriodTicks = fakeDisplayNamePeriodTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isFakeDisplayNameEnabled) {
                removeAllNameplates();
                return;
            }
            if (currentFakeDisplayNamePeriodTicks != fakeDisplayNamePeriodTicks) {
                startFakeNameDisplayUpdating();
                return;
            }
            nameplates.forEach((viewerUUID, playerNameplates) -> {
                Player viewer = Bukkit.getPlayer(viewerUUID);
                if (viewer == null) return;
                playerNameplates.forEach((entityUUID, display) -> {
                    if (!display.isValid()) return;
                    for (Entity e : viewer.getWorld().getEntities()) {
                        if (e.getUniqueId().equals(entityUUID) && e instanceof LivingEntity living) {
                            display.teleport(living.getLocation().add(0, living.getHeight() + 0.3, 0));
                            break;
                        }
                    }
                });
            });
        }, 0L, fakeDisplayNamePeriodTicks);
    }

    public static Team getTeam(LivingEntity entity) {
        return Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entity.getScoreboardEntryName());
    }

    public static Team.OptionStatus getTeamVisibility(Team team) {
        if (team == null) return Team.OptionStatus.ALWAYS;
        return team.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

    public static boolean isNameDisplayVisible(Player viewer, LivingEntity entity) {
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

    public static void updateNameplate(Player viewer, LivingEntity entity, boolean entityHidden) {
        boolean needsNameplate = isNameDisplayVisible(viewer, entity) && entityHidden;
        Map<UUID, TextDisplay> playerNameplates = nameplates.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        if (needsNameplate) {
            TextDisplay existing = playerNameplates.get(entity.getUniqueId());
            if (existing == null || !existing.isValid()) {
                TextDisplay display = spawnNameplate(viewer, entity);
                playerNameplates.put(entity.getUniqueId(), display);
            } else {
                Component name = entity instanceof Player ? Component.text(entity.getName()) : entity.customName();
                existing.text(name);
            }
        } else {
            removeNameplate(viewer, entity);
        }
    }

    public static TextDisplay spawnNameplate(Player viewer, LivingEntity entity) {
        Component name = entity instanceof Player
                ? Component.text(entity.getName())
                : entity.customName() != null ? entity.customName() : Component.text(entity.getName());
        Location loc = entity.getLocation().add(0, entity.getHeight() + 0.5, 0);
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(name);
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setTextOpacity((byte) 128);
            d.setSeeThrough(true);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    public static void removeNameplate(Player viewer, LivingEntity entity) {
        Map<UUID, TextDisplay> playerNameplates = nameplates.get(viewer.getUniqueId());
        if (playerNameplates == null) return;
        TextDisplay display = playerNameplates.remove(entity.getUniqueId());
        if (display != null) display.remove();
        if (playerNameplates.isEmpty()) nameplates.remove(viewer.getUniqueId());
    }

    public static void removeAllNameplates(Player viewer) {
        Map<UUID, TextDisplay> playerNameplates = nameplates.remove(viewer.getUniqueId());
        if (playerNameplates != null) playerNameplates.values().forEach(Entity::remove);
    }

    public static void removeAllNameplates() {
        nameplates.values().forEach(map -> map.values().forEach(Entity::remove));
        nameplates.clear();
    }

}
