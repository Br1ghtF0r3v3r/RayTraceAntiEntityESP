package RayTraceAntiEntityESP.paper.engine;

import org.bukkit.World;

@FunctionalInterface
public interface BlockSolidSource {
    boolean isSolid(World world, int x, int y, int z);
}
