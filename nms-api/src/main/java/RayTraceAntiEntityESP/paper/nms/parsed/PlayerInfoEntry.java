package RayTraceAntiEntityESP.paper.nms.parsed;

import java.util.UUID;

public record PlayerInfoEntry(
        UUID profileId,
        String profileName,
        boolean listed,
        int latency,
        Object gameMode,
        String displayNamePlain,
        boolean showHat,
        int listOrder
) {
}
