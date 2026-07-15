package RayTraceAntiEntityESP.bukkit.nms.parsed;

import com.mojang.authlib.GameProfile;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public record PlayerInfoEntry(
        UUID profileId,
        GameProfile profile,
        boolean listed,
        int latency,
        Object gameMode,
        Component displayName,
        boolean showHat,
        int listOrder
) {}