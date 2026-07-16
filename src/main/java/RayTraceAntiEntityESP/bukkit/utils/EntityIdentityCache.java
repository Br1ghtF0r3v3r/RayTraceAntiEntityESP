package RayTraceAntiEntityESP.bukkit.utils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityIdentityCache {

    private static final ConcurrentHashMap<Integer, UUID> idToUuid = new ConcurrentHashMap<>();
    private static final Set<Integer> playerEntityIds = ConcurrentHashMap.newKeySet();

    public static void register(int entityId, UUID uuid, boolean isPlayer) {
        idToUuid.put(entityId, uuid);
        if (isPlayer) {
            playerEntityIds.add(entityId);
        } else {
            playerEntityIds.remove(entityId);
        }
    }

    public static UUID getUuid(int entityId) {
        return idToUuid.get(entityId);
    }

    public static boolean isPlayer(int entityId) {
        return playerEntityIds.contains(entityId);
    }

    public static void remove(int entityId) {
        idToUuid.remove(entityId);
        playerEntityIds.remove(entityId);
    }

    public static void clearAll() {
        idToUuid.clear();
        playerEntityIds.clear();
    }
}
