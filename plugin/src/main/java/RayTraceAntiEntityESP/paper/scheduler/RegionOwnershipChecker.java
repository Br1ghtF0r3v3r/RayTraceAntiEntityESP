package RayTraceAntiEntityESP.paper.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

public final class RegionOwnershipChecker {

    private static volatile boolean available;
    private static Method ownsEntity;
    private static Method ownsChunkRadius;

    private RegionOwnershipChecker() {
    }

    public static void init() {
        if (!SchedulerAdapterFactory.isFolia()) {
            available = false;
            return;
        }
        try {
            ownsEntity = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            ownsChunkRadius = Bukkit.class.getMethod("isOwnedByCurrentRegion", World.class, int.class, int.class, int.class);
            available = true;
        } catch (ReflectiveOperationException e) {
            available = false;
        }
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (!available) return true;
        try {
            return (boolean) ownsEntity.invoke(null, entity);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    public static boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ, int squareRadiusChunks) {
        if (!available) return true;
        try {
            return (boolean) ownsChunkRadius.invoke(null, world, chunkX, chunkZ, squareRadiusChunks);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}