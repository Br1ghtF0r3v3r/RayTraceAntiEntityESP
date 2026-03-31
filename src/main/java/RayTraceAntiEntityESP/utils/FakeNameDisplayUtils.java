package RayTraceAntiEntityESP.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.config.Config.*;
import static RayTraceAntiEntityESP.misc.Team.getTeam;
import static RayTraceAntiEntityESP.misc.Team.getTeamVisibility;
import static RayTraceAntiEntityESP.utils.DebugsUtils.DEBUG_KEY;

public class FakeNameDisplayUtils {

    private static BukkitTask task;
    private static long currentFakeNameDisplayPeriodTicks;

    public static final NamespacedKey FAKE_DISPLAY_NAME_KEY = new NamespacedKey(plugin, "is_fake_textDisplay_name");
    public static final Map<UUID, Map<UUID, TextDisplay>> fakeNameDisplay = new HashMap<>();

    public static void startFakeNameDisplayUpdating() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        currentFakeNameDisplayPeriodTicks = fakeDisplayNamePeriodTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isFakeDisplayNameEnabled) {
                removeAllFakeNameDisplay();
                task.cancel();
                task = null;
                return;
            }
            if (currentFakeNameDisplayPeriodTicks != fakeDisplayNamePeriodTicks) {
                startFakeNameDisplayUpdating();
                return;
            }
            updateAllFakeNameDisplays();
        }, 0L, fakeDisplayNamePeriodTicks);
    }

    public static void updateAllFakeNameDisplays() {
        for (Map.Entry<UUID, Map<UUID, TextDisplay>> viewerEntry : fakeNameDisplay.entrySet()) {
            Player viewer = Bukkit.getPlayer(viewerEntry.getKey());
            if (viewer == null) continue;

            for (Map.Entry<UUID, TextDisplay> displayEntry : viewerEntry.getValue().entrySet()) {
                Entity entity = Bukkit.getEntity(displayEntry.getKey());
                if (entity == null) continue;

                TextDisplay display = displayEntry.getValue();
                if (!display.isValid()) continue;

                Component name = entity instanceof Player
                        ? Component.text(entity.getName())
                        : entity.customName() != null ? entity.customName() : Component.text(entity.getName());
                display.text(name);
                display.teleport(entity.getLocation().add(0, entity.getHeight() + fakeDisplayNameOffSetY, 0));
            }
        }
    }

    public static void applyFakeNameDisplay(Player viewer, Entity entity) {
        boolean shouldSkipFakeNameDisplay = entity.getPersistentDataContainer().has(DEBUG_KEY, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE);
        if (shouldSkipFakeNameDisplay) return;
        if (!entity.isValid() || entity.isDead()) {
            removeFakeNameDisplay(viewer, entity);
            return;
        }
        if (!isNametextDisplayVisible(viewer, entity)) {
            removeFakeNameDisplay(viewer, entity);
            return;
        }
        Map<UUID, TextDisplay> viewerDisplays = fakeNameDisplay.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        TextDisplay existingDisplay = viewerDisplays.get(entity.getUniqueId());
        if (existingDisplay == null || !existingDisplay.isValid()) {
            viewerDisplays.put(entity.getUniqueId(), spawnFakeNameDisplay(viewer, entity));
        }
    }

    public static TextDisplay spawnFakeNameDisplay(Player viewer, Entity entity) {
        Location loc = entity.getLocation();
        TextDisplay textDisplay = entity.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE, (byte) 1);
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setTextOpacity((byte) 128);
            d.setSeeThrough(true);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
        });
        viewer.showEntity(plugin, textDisplay);
        return textDisplay;
    }

    public static void removeFakeNameDisplay(Player viewer, Entity entity) {
        Map<UUID, TextDisplay> display = fakeNameDisplay.get(viewer.getUniqueId());
        if (display == null) return;
        TextDisplay textDisplay = display.remove(entity.getUniqueId());
        if (textDisplay != null) textDisplay.remove();
        if (display.isEmpty()) fakeNameDisplay.remove(viewer.getUniqueId());
    }

    public static void removeFakeNameDisplay(Player viewer) {
        Map<UUID, TextDisplay> viewerFakeNameDisplay = fakeNameDisplay.remove(viewer.getUniqueId());
        if (viewerFakeNameDisplay == null) return;
        for (TextDisplay textDisplay : viewerFakeNameDisplay.values()) {
            textDisplay.remove();
        }
    }

    public static void removeAllFakeNameDisplay() {
        for (Map<UUID, TextDisplay> playerNameplates : fakeNameDisplay.values()) {
            for (TextDisplay textDisplay : playerNameplates.values()) {
                textDisplay.remove();
            }
        }
        fakeNameDisplay.clear();
    }

    public static boolean isNametextDisplayVisible(Player viewer, Entity entity) {
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
