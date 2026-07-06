package RayTraceAntiEntityESP.bukkit.config;

import RayTraceAntiEntityESP.bukkit.listener.PacketManager;
import RayTraceAntiEntityESP.bukkit.utils.VisibilityUtils;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class ExcludeBypassManager {

    private static File file;
    private static YamlConfiguration data;

    public static final Set<UUID> excluded = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();

    public static void load() {
        file = new File(plugin.getDataFolder(), "entities.yml");
        data = YamlConfiguration.loadConfiguration(file);

        excluded.clear();
        for (String raw : data.getStringList("exclude")) {
            try {
                excluded.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }

        for (UUID uuid : bypassed) PacketManager.removeBypass(uuid);
        bypassed.clear();
        for (String raw : data.getStringList("bypass")) {
            try {
                UUID uuid = UUID.fromString(raw);
                bypassed.add(uuid);
                PacketManager.addBypass(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void save() {
        List<String> excludeList = new ArrayList<>();
        for (UUID uuid : excluded) excludeList.add(uuid.toString());
        Collections.sort(excludeList);

        List<String> bypassList = new ArrayList<>();
        for (UUID uuid : bypassed) bypassList.add(uuid.toString());
        Collections.sort(bypassList);

        data.set("exclude", excludeList);
        data.set("bypass", bypassList);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save entities.yml: " + e.getMessage());
        }
    }

    public static boolean addExclude(UUID uuid) {
        boolean added = excluded.add(uuid);
        if (added) {
            save();
            forceUnhideEntityForAllViewers(uuid);
        }
        return added;
    }

    public static boolean removeExclude(UUID uuid) {
        boolean removed = excluded.remove(uuid);
        if (removed) save();
        return removed;
    }

    public static Set<UUID> listExclude() {
        return Collections.unmodifiableSet(excluded);
    }

    public static int clearExclude() {
        int count = excluded.size();
        if (count > 0) {
            excluded.clear();
            save();
        }
        return count;
    }

    public static boolean isExcluded(UUID uuid) {
        return !excluded.isEmpty() && excluded.contains(uuid);
    }

    public static boolean addBypass(UUID uuid) {
        boolean added = bypassed.add(uuid);
        if (added) {
            save();
            PacketManager.addBypass(uuid);
            forceUnhideAllForViewer(uuid);
            RayTraceAntiEntityESP.bukkit.engine.NametagCloneRenderer.removeDisplay(uuid);
        }
        return added;
    }

    public static boolean removeBypass(UUID uuid) {
        boolean removed = bypassed.remove(uuid);
        if (removed) {
            save();
            PacketManager.removeBypass(uuid);
        }
        return removed;
    }

    public static Set<UUID> listBypass() {
        return Collections.unmodifiableSet(bypassed);
    }

    public static int clearBypass() {
        int count = bypassed.size();
        if (count > 0) {
            for (UUID uuid : bypassed) PacketManager.removeBypass(uuid);
            bypassed.clear();
            save();
        }
        return count;
    }

    private static void forceUnhideEntityForAllViewers(UUID targetUUID) {
        Entity target = Bukkit.getEntity(targetUUID);
        if (target == null) return;
        int targetEntityId = target.getEntityId();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (VisibilityUtils.isHidden(viewer.getEntityId(), targetEntityId)) {
                VisibilityUtils.setNotHidden(viewer, target);
            }
        }
    }

    private static void forceUnhideAllForViewer(UUID viewerUUID) {
        Player viewer = Bukkit.getPlayer(viewerUUID);
        if (viewer == null) return;
        IntSet hidden = VisibilityUtils.getHiddenSet(viewer.getEntityId());
        if (hidden == null || hidden.isEmpty()) return;
        Set<Integer> ids = new HashSet<>(hidden);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (ids.contains(entity.getEntityId())) {
                    VisibilityUtils.setNotHidden(viewer, entity);
                }
            }
        }
    }
}