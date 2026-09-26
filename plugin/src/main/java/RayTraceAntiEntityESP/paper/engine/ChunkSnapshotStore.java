package RayTraceAntiEntityESP.paper.engine;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.ConcurrentHashMap;

public final class ChunkSnapshotStore {

    private record Key(Object worldKey, int chunkX, int chunkZ) {}

    private record Entry(org.bukkit.ChunkSnapshot snapshot, int fetchedAtTick) {}

    private final ConcurrentHashMap<Key, Entry> snapshots = new ConcurrentHashMap<>();

    public void ensureFresh(World world, int chunkX, int chunkZ, int currentTick, int ttlTicks) {
        Key key = new Key(world, chunkX, chunkZ);
        Entry existing = snapshots.get(key);
        if (existing != null && (currentTick - existing.fetchedAtTick()) < ttlTicks) return;

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            snapshots.remove(key);
            return;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        org.bukkit.ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        snapshots.put(key, new Entry(snapshot, currentTick));
    }

    public boolean isSolid(World world, int x, int y, int z) {
        int chunkX = x >> 4, chunkZ = z >> 4;
        Entry entry = snapshots.get(new Key(world, chunkX, chunkZ));
        if (entry == null) return false;

        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;

        try {
            BlockData data = entry.snapshot().getBlockData(x & 15, y, z & 15);
            return data.getMaterial().isOccluding();
        } catch (Throwable t) {
            return false;
        }
    }

    public void invalidateWorld(World world) {
        snapshots.keySet().removeIf(k -> k.worldKey().equals(world));
    }

    public void clearAll() {
        snapshots.clear();
    }

    public BlockSolidSource asBlockSolidSource() {
        return this::isSolid;
    }
}
