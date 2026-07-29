package RayTraceAntiEntityESP.paper.nms;

import RayTraceAntiEntityESP.paper.nms.parsed.*;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NmsAdapter {

    void sendPacket(Player viewer, Object packet);

    void bundleAndSend(Player viewer, List<Object> packets);

    void resendPlayerInfoAdd(Player viewer, Player target);

    default void sendBundled(Player viewer, List<Object> outbox) {
        if (outbox == null || outbox.isEmpty()) return;
        bundleAndSend(viewer, outbox);
    }

    Object buildPlayerInfoRemovePacket(List<UUID> uuids);

    Object buildRemoveEntitiesPacket(int... entityIds);

    Object buildAddMemberToTeamPacket(String teamName, String entry);

    Object buildArmorStandSpawnPacket(int entityId, UUID entityUuid, double x, double y, double z);

    Object buildBlockDisplaySpawnPacket(int entityId, UUID entityUuid, double x, double y, double z);

    Object buildSetEntityDataPacket(int entityId, List<Object> dataValues);

    Object buildMoveEntityPacket(int entityId, short dx, short dy, short dz, boolean onGround);

    List<Object> buildArmorStandMetadata(Component customName);

    List<Object> buildBlockDisplayMetadata(Object blockState, float scale, int interpolationTicks);

    Object blockStateForName(String name);

    ParsedAddEntity parseAddEntity(Object msg);
    ParsedRemoveEntities parseRemoveEntities(Object msg);
    ParsedSetEntityData parseSetEntityData(Object msg);
    ParsedPlayerInfoUpdate parsePlayerInfoUpdate(Object msg);
    ParsedPlayerInfoRemove parsePlayerInfoRemove(Object msg);
    ParsedSetObjective parseSetObjective(Object msg);
    ParsedSetDisplayObjective parseSetDisplayObjective(Object msg);
    ParsedSetScore parseSetScore(Object msg);
    ParsedResetScore parseResetScore(Object msg);
    ParsedSetPlayerTeam parseSetPlayerTeam(Object msg);

    Object rebuildPlayerInfoUpdate(ParsedPlayerInfoUpdate orig, Map<UUID, Component> forcedDisplayNames);

    Object buildDisplayNameUpdatePacket(Player target, Component displayName);

    boolean hasDisconnected(Player player);

    void forEachServerPlayer(java.util.function.Consumer<Player> action);

    double[] getEntityBoundingBox(Entity entity);

    boolean isBlockSolidAt(World world, int x, int y, int z);

    void getAllEntitiesInBox(World world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, java.util.function.Consumer<Entity> consumer);

    void resendAllTeamsTo(Player viewer);

    Channel getChannel(Player player);
}