package RayTraceAntiEntityESP.bukkit.nms.parsed;

import java.util.List;
import java.util.Set;

public record ParsedPlayerInfoUpdate(Set<String> actions, List<PlayerInfoEntry> entries, Object rawPacket) {}
