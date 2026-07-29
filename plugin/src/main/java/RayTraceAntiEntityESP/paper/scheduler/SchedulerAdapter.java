package RayTraceAntiEntityESP.paper.scheduler;

import org.bukkit.entity.Entity;

public interface SchedulerAdapter {

    ScheduledTaskHandle runTaskTimer(Runnable task, long delayTicks, long periodTicks);

    void runTask(Runnable task);

    void runTaskLater(Runnable task, long delayTicks);

    void runTaskAsynchronously(Runnable task);

    void runEntityTask(Entity entity, Runnable task);

    void cancelAll();
}