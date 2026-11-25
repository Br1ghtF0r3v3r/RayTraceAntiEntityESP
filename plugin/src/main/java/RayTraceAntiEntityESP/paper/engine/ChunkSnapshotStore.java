package RayTraceAntiEntityESP.paper.engine;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.ConcurrentHashMap;

public final class ChunkSnapshotStore {

    private record Key(Object worldKey, int chunkX, int chunkZ) {}
    private record Entry(org.bukkit.ChunkSnapshot snapshot, int fetchedAtTick, boolean stale, int minHeight, int maxHeight) {}
    private final ConcurrentHashMap<Key, Entry> snapshots = new ConcurrentHashMap<>();

    public void ensureFresh(World world, int chunkX, int chunkZ, int currentTick, int ttlTicks) {
        Key key = new Key(world, chunkX, chunkZ);
        Entry existing = snapshots.get(key);
        if (existing != null && existing.snapshot() != null && !existing.stale()
                && (currentTick - existing.fetchedAtTick()) < ttlTicks) return;

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            snapshots.put(key, new Entry(null, currentTick, false, minHeight, maxHeight));
            return;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        org.bukkit.ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        snapshots.put(key, new Entry(snapshot, currentTick, false, minHeight, maxHeight));
    }

    public void markStale(World world, int chunkX, int chunkZ) {
        if (snapshots.isEmpty()) return;
        snapshots.computeIfPresent(new Key(world, chunkX, chunkZ),
                (k, e) -> e.stale() ? e : new Entry(e.snapshot(), e.fetchedAtTick(), true, e.minHeight(), e.maxHeight()));
    }

    public void evictOlderThan(int currentTick, int maxAgeTicks) {
        snapshots.values().removeIf(e -> (currentTick - e.fetchedAtTick()) > maxAgeTicks);
    }

    public int size() {
        return snapshots.size();
    }

    public boolean isSolid(World world, int x, int y, int z) {
        int chunkX = x >> 4, chunkZ = z >> 4;
        Entry entry = snapshots.get(new Key(world, chunkX, chunkZ));
        if (entry == null || entry.snapshot() == null) return true;

        if (y < entry.minHeight() || y >= entry.maxHeight()) return false;

        try {
            BlockData data = entry.snapshot().getBlockData(x & 15, y, z & 15);
            return data.getMaterial().isOccluding();
        } catch (Throwable t) {
            return true;
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
