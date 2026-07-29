package RayTraceAntiEntityESP.paper.scheduler;

import org.bukkit.plugin.Plugin;

public final class SchedulerAdapterFactory {

    private static volatile SchedulerAdapter instance;
    private static volatile boolean folia;

    private SchedulerAdapterFactory() {
    }

    public static void init(Plugin plugin) {
        if (instance != null) return;
        synchronized (SchedulerAdapterFactory.class) {
            if (instance != null) return;
            boolean detected = detectFolia();
            folia = detected;
            if (detected) {
                try {
                    Class<?> clazz = Class.forName("RayTraceAntiEntityESP.paper.scheduler.folia.FoliaSchedulerAdapter");
                    instance = (SchedulerAdapter) clazz.getConstructor(Plugin.class).newInstance(plugin);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Folia detected but FoliaSchedulerAdapter could not be loaded", e);
                }
            } else {
                instance = new PaperSchedulerAdapter(plugin);
            }
        }
    }

    public static SchedulerAdapter get() {
        SchedulerAdapter local = instance;
        if (local == null) {
            throw new IllegalStateException("SchedulerAdapterFactory.init(plugin) has not been called yet");
        }
        return local;
    }

    public static boolean isFolia() {
        return folia;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}