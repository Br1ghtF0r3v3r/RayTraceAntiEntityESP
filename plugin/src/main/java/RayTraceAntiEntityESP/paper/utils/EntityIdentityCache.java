package RayTraceAntiEntityESP.paper.utils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityIdentityCache {

    private static final ConcurrentHashMap<Integer, UUID> idToUuid = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> uuidToId = new ConcurrentHashMap<>();
    private static final Set<Integer> playerEntityIds = ConcurrentHashMap.newKeySet();

    public static int registerAndGetPreviousId(int entityId, UUID uuid, boolean isPlayer) {
        idToUuid.put(entityId, uuid);
        if (isPlayer) {
            playerEntityIds.add(entityId);
        } else {
            playerEntityIds.remove(entityId);
        }
        Integer previousId = uuidToId.put(uuid, entityId);
        return (previousId != null && previousId != entityId) ? previousId : -1;
    }

    public static UUID getUuid(int entityId) {
        return idToUuid.get(entityId);
    }

    public static boolean isPlayer(int entityId) {
        return playerEntityIds.contains(entityId);
    }

    public static void remove(int entityId) {
        UUID uuid = idToUuid.remove(entityId);
        playerEntityIds.remove(entityId);
        if (uuid != null) {
            uuidToId.remove(uuid, entityId);
        }
    }

    public static void clearAll() {
        idToUuid.clear();
        uuidToId.clear();
        playerEntityIds.clear();
    }
}