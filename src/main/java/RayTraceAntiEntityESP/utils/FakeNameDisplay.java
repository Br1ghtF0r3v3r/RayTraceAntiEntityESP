package RayTraceAntiEntityESP.utils;

import RayTraceAntiEntityESP.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.config.Config.fakeDisplayNameOffSetY;
import static RayTraceAntiEntityESP.utils.DebugsUtils.DEBUG_KEY;

public class FakeNameDisplay {

    private static BukkitTask task;

    public static final NamespacedKey FAKE_DISPLAY_NAME_KEY = new NamespacedKey(plugin, "is_fake_textDisplay_name");
    public static final Map<UUID, Map<UUID, TextDisplay>> fakeNameDisplay = new HashMap<>();

    public static void killTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAllDisplays();
    }

    public static void startTask() {
        killTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, FakeNameDisplay::updateDisplays, 0L, Config.fakeDisplayNamePeriodTicks);
    }

    private static Component buildName(Entity entity) {
        Component name;
        if (entity instanceof Player) {
            name = Component.text(entity.getName());
        } else if (entity.customName() != null) {
            name = entity.customName();
        } else {
            name = Component.text(entity.getName());
        }

        NamedTextColor teamColor = TeamUtils.getTeamColor(entity);
        if (teamColor != null && name != null) name = name.color(teamColor);

        Component teamPrefix = TeamUtils.getTeamPrefix(entity);
        if (teamPrefix != null && name != null) name = teamPrefix.append(name);

        return name;
    }

    public static void updateDisplays() {
        for (Map.Entry<UUID, Map<UUID, TextDisplay>> viewerEntry : fakeNameDisplay.entrySet()) {
            Player viewer = Bukkit.getPlayer(viewerEntry.getKey());
            if (viewer == null) continue;
            for (Map.Entry<UUID, TextDisplay> displayEntry : viewerEntry.getValue().entrySet()) {
                Entity entity = Bukkit.getEntity(displayEntry.getKey());
                if (entity == null) continue;
                TextDisplay display = displayEntry.getValue();
                if (!display.isValid()) continue;

                display.text(buildName(entity));
                display.teleport(entity.getLocation().add(0, entity.getHeight() + fakeDisplayNameOffSetY, 0));

                if (!VisibilityUtils.isNameVisible(viewer, entity) || viewer.canSee(entity) || entity.isInvisible() || (entity instanceof Player player && player.isSneaking())) {
                    viewer.hideEntity(plugin, display);
                    continue;
                }

                viewer.showEntity(plugin, display);
            }
        }
    }

    public static void applyDisplay(Player viewer, Entity entity) {
        if (entity.getPersistentDataContainer().has(DEBUG_KEY, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE)) return;
        if (!entity.isValid() || entity.isDead()) {
            removeDisplay(viewer, entity);
            return;
        }
        if (!VisibilityUtils.isNameVisible(viewer, entity) || viewer.canSee(entity)) {
            removeDisplay(viewer, entity);
            return;
        }
        Map<UUID, TextDisplay> viewerDisplays = fakeNameDisplay.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        TextDisplay existing = viewerDisplays.get(entity.getUniqueId());
        if (existing == null || !existing.isValid()) {
            viewerDisplays.put(entity.getUniqueId(), spawnDisplay(entity));
        }
    }

    public static TextDisplay spawnDisplay(Entity entity) {
        Component finalName = buildName(entity);
        Location loc = entity.getLocation().add(0, entity.getHeight() + fakeDisplayNameOffSetY, 0);
        return entity.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(finalName);
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setTextOpacity((byte) 128);
            d.setSeeThrough(true);
            d.getPersistentDataContainer().set(FAKE_DISPLAY_NAME_KEY, PersistentDataType.BYTE, (byte) 1);
            d.setPersistent(false);
        });
    }

    public static void removeDisplay(Player viewer, Entity entity) {
        Map<UUID, TextDisplay> display = fakeNameDisplay.get(viewer.getUniqueId());
        if (display == null) return;
        TextDisplay textDisplay = display.remove(entity.getUniqueId());
        if (textDisplay == null) {
            return;
        } else {
            textDisplay.remove();
        }
        if (display.isEmpty()) fakeNameDisplay.remove(viewer.getUniqueId());
    }

    public static void removeDisplay(Player viewer) {
        Map<UUID, TextDisplay> viewerFakeNameDisplay = fakeNameDisplay.remove(viewer.getUniqueId());
        if (viewerFakeNameDisplay == null) return;
        for (TextDisplay textDisplay : viewerFakeNameDisplay.values()) {
            textDisplay.remove();
        }
    }

    public static void removeAllDisplays() {
        for (Map<UUID, TextDisplay> playerNameplates : fakeNameDisplay.values()) {
            for (TextDisplay textDisplay : playerNameplates.values()) {
                textDisplay.remove();
            }
        }
        fakeNameDisplay.clear();
    }

}
