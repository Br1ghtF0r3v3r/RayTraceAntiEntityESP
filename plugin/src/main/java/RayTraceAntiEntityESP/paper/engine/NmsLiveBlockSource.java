package RayTraceAntiEntityESP.paper.engine;

import RayTraceAntiEntityESP.paper.nms.NmsAdapter;
import org.bukkit.World;

public final class NmsLiveBlockSource implements BlockSolidSource {

    private final NmsAdapter adapter;

    public NmsLiveBlockSource(NmsAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public boolean isSolid(World world, int x, int y, int z) {
        try {
            return adapter.isBlockSolidAt(world, x, y, z);
        } catch (Throwable t) {
            return false;
        }
    }
}
