package RayTraceAntiEntityESP.paper.utils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealEntityCache {

    private static final Set<UUID> knownEntities = ConcurrentHashMap.newKeySet();

    private RealEntityCache() {}

    public static void add(UUID uuid) {
        knownEntities.add(uuid);
    }

    public static void remove(UUID uuid) {
        knownEntities.remove(uuid);
    }

    public static boolean isReal(UUID uuid) {
        return knownEntities.contains(uuid);
    }

    public static void clearAll() {
        knownEntities.clear();
    }
}