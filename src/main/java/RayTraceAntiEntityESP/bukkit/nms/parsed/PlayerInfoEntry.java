package RayTraceAntiEntityESP.bukkit.nms.parsed;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

public record PlayerInfoEntry(
        UUID profileId,
        GameProfile profile,
        boolean listed,
        int latency,
        Object gameMode,
        String displayNamePlain,
        boolean showHat,
        int listOrder
) {
}