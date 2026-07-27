package RayTraceAntiEntityESP.folia.scheduler;

import RayTraceAntiEntityESP.paper.scheduler.ScheduledTaskHandle;
import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;

    private final Object globalRegionScheduler;
    private final Object asyncScheduler;

    private final Method globalRunNow;
    private final Method globalRunDelayed;
    private final Method globalRunAtFixedRate;
    private final Method globalCancelTasks;

    private final Method asyncRunNow;
    private final Method asyncCancelTasks;

    private final Method entityGetScheduler;
    private final Method entitySchedulerRun;

    private final Method scheduledTaskCancel;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            Method getGlobalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            this.globalRegionScheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());

            Method getAsyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler");
            this.asyncScheduler = getAsyncScheduler.invoke(Bukkit.getServer());

            this.globalRunNow = globalRegionSchedulerClass.getMethod("run", Plugin.class, Consumer.class);

            this.globalRunDelayed = globalRegionSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

            this.globalRunAtFixedRate = globalRegionSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

            this.globalCancelTasks = globalRegionSchedulerClass.getMethod("cancelTasks", Plugin.class);

            this.asyncRunNow = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);

            this.asyncCancelTasks = asyncSchedulerClass.getMethod("cancelTasks", Plugin.class);

            this.entityGetScheduler = Entity.class.getMethod("getScheduler");

            this.entitySchedulerRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);

            this.scheduledTaskCancel = scheduledTaskClass.getMethod("cancel");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialise FoliaSchedulerAdapter: Folia scheduler API not found", e);
        }
    }

    @Override
    public void runTask(Runnable task) {
        try {
            globalRunNow.invoke(globalRegionScheduler, plugin, wrap(task));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke GlobalRegionScheduler.run", e);
        }
    }

    @Override
    public void runTaskLater(Runnable task, long delayTicks) {
        if (delayTicks < 1) delayTicks = 1;
        try {
            globalRunDelayed.invoke(globalRegionScheduler, plugin, wrap(task), delayTicks);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke GlobalRegionScheduler.runDelayed", e);
        }
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        try {
            asyncRunNow.invoke(asyncScheduler, plugin, wrap(task));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke AsyncScheduler.runNow", e);
        }
    }

    @Override
    public ScheduledTaskHandle runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (delayTicks < 1) delayTicks = 1;
        if (periodTicks < 1) periodTicks = 1;
        try {
            Object foliaTask = globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, wrap(task), delayTicks, periodTicks);
            return makeHandle(foliaTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke GlobalRegionScheduler.runAtFixedRate", e);
        }
    }

    @Override
    public void runEntityTask(Entity entity, Runnable task) {
        try {
            Object entityScheduler = entityGetScheduler.invoke(entity);
            if (entityScheduler == null) {
                return;
            }
            Consumer<Object> consumer = scheduledTask -> task.run();
            entitySchedulerRun.invoke(entityScheduler, plugin, consumer, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke EntityScheduler.run", e);
        }
    }

    @Override
    public void cancelAll() {
        try {
            globalCancelTasks.invoke(globalRegionScheduler, plugin);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to cancel global region tasks", e);
        }
        try {
            asyncCancelTasks.invoke(asyncScheduler, plugin);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to cancel async tasks", e);
        }
    }

    private static Consumer<Object> wrap(Runnable runnable) {
        return scheduledTask -> runnable.run();
    }

    private ScheduledTaskHandle makeHandle(Object foliaTask) {
        return () -> {
            try {
                scheduledTaskCancel.invoke(foliaTask);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to cancel Folia task", e);
            }
        };
    }
}