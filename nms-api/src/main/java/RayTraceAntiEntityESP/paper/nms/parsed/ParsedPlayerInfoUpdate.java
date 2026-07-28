package RayTraceAntiEntityESP.paper.nms.parsed;

import java.util.List;
import java.util.Set;

public record ParsedPlayerInfoUpdate(Set<String> actions, List<PlayerInfoEntry> entries, Object rawPacket) {}
