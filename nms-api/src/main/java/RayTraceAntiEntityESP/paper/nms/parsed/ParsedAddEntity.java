package RayTraceAntiEntityESP.paper.nms.parsed;

import java.util.UUID;

public record ParsedAddEntity(int entityId, UUID uuid, boolean isPlayer, String entityTypeKey) {}