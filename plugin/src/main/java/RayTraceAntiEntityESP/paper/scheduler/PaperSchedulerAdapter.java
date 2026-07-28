package RayTraceAntiEntityESP.paper.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;

    public PaperSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runTaskLater(Runnable task, long delayTicks) {
        if (delayTicks < 0) delayTicks = 0;
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public ScheduledTaskHandle runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (delayTicks < 0) delayTicks = 0;
        if (periodTicks < 1) periodTicks = 1;
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    @Override
    public void runEntityTask(Entity entity, Runnable task) {

        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}