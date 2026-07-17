package RayTraceAntiEntityESP.bukkit.nms.v1_21;

import RayTraceAntiEntityESP.bukkit.nms.NmsAdapter;
import RayTraceAntiEntityESP.bukkit.nms.EntityTypeResolver;
import RayTraceAntiEntityESP.bukkit.nms.parsed.*;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NmsAdapter_1_21_x implements NmsAdapter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    @Override
    public void sendPacket(Player viewer, Object packet) {
        ((CraftPlayer) viewer).getHandle().connection.send((Packet<?>) packet);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void bundleAndSend(Player viewer, List<Object> packets) {
        if (packets == null || packets.isEmpty()) return;
        List<Packet<? super ClientGamePacketListener>> cast = new ArrayList<>(packets.size());
        for (Object p : packets) cast.add((Packet<? super ClientGamePacketListener>) p);
        ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundBundlePacket(cast));
    }

    @Override
    public Object buildPlayerInfoRemovePacket(List<UUID> uuids) {
        return new ClientboundPlayerInfoRemovePacket(uuids);
    }

    @Override
    public Object buildRemoveEntitiesPacket(int... entityIds) {
        return new ClientboundRemoveEntitiesPacket(entityIds);
    }

    @Override
    public Object buildAddMemberToTeamPacket(String teamName, String entry) {
        net.minecraft.world.scores.PlayerTeam nmsTeam = MinecraftServer.getServer().getScoreboard().getPlayerTeam(teamName);
        if (nmsTeam == null) {
            nmsTeam = new net.minecraft.world.scores.PlayerTeam(
                    MinecraftServer.getServer().getScoreboard(), teamName);
        }
        return ClientboundSetPlayerTeamPacket.createPlayerPacket(
                nmsTeam, entry, ClientboundSetPlayerTeamPacket.Action.ADD);
    }

    @Override
    @SuppressWarnings({"rawtypes"})
    public Object buildArmorStandSpawnPacket(int entityId, UUID entityUuid, double x, double y, double z) {
        return new ClientboundAddEntityPacket(entityId, entityUuid, x, y, z, 0f, 0f, (EntityType) EntityTypeResolver.armorStand(), 0, Vec3.ZERO, 0.0);
    }

    @Override
    @SuppressWarnings({"rawtypes"})
    public Object buildBlockDisplaySpawnPacket(int entityId, UUID entityUuid, double x, double y, double z) {
        return new ClientboundAddEntityPacket(entityId, entityUuid, x, y, z, 0f, 0f, (EntityType) EntityTypeResolver.blockDisplay(), 0, Vec3.ZERO, 0.0);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object buildSetEntityDataPacket(int entityId, List<Object> dataValues) {
        List<SynchedEntityData.DataValue<?>> cast = (List) dataValues;
        return new ClientboundSetEntityDataPacket(entityId, cast);
    }

    @Override
    public Object buildMoveEntityPacket(int entityId, short dx, short dy, short dz, boolean onGround) {
        return new ClientboundMoveEntityPacket.Pos(entityId, dx, dy, dz, onGround);
    }

    @Override
    public List<Object> buildArmorStandMetadata(Component customName) {
        List<Object> out = new ArrayList<>(5);
        out.add(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x20));
        if (customName != null) {
            out.add(new SynchedEntityData.DataValue<>(2, EntityDataSerializers.OPTIONAL_COMPONENT,
                    Optional.of(PaperAdventure.asVanilla(customName))));
            out.add(new SynchedEntityData.DataValue<>(3, EntityDataSerializers.BOOLEAN, true));
        }
        out.add(new SynchedEntityData.DataValue<>(5, EntityDataSerializers.BOOLEAN, true));
        out.add(new SynchedEntityData.DataValue<>(15, EntityDataSerializers.BYTE, (byte) 0x19));
        return out;
    }

    @Override
    public List<Object> buildBlockDisplayMetadata(Object blockState, float scale, int interpolationTicks) {
        List<Object> out = new ArrayList<>(4);
        out.add(new SynchedEntityData.DataValue<>(10, EntityDataSerializers.INT, interpolationTicks));
        float half = scale * -0.5f;
        out.add(new SynchedEntityData.DataValue<>(11, EntityDataSerializers.VECTOR3,
                new Vector3f(half, half, half)));
        out.add(new SynchedEntityData.DataValue<>(12, EntityDataSerializers.VECTOR3,
                new Vector3f(scale, scale, scale)));
        out.add(new SynchedEntityData.DataValue<>(23, EntityDataSerializers.BLOCK_STATE,
                (BlockState) blockState));
        return out;
    }

    @Override
    public Object blockStateForName(String name) {
        if (name.equals("RED_WOOL")) return Blocks.RED_WOOL.defaultBlockState();
        return Blocks.LIME_WOOL.defaultBlockState();
    }

    @Override
    public ParsedAddEntity parseAddEntity(Object msg) {
        if (!(msg instanceof ClientboundAddEntityPacket p)) return null;
        return new ParsedAddEntity(p.getId(), p.getUUID(), p.getType() == EntityTypeResolver.player());
    }

    @Override
    public ParsedRemoveEntities parseRemoveEntities(Object msg) {
        if (!(msg instanceof ClientboundRemoveEntitiesPacket p)) return null;
        return new ParsedRemoveEntities(p.getEntityIds().toIntArray());
    }

    @Override
    public ParsedSetEntityData parseSetEntityData(Object msg) {
        if (!(msg instanceof ClientboundSetEntityDataPacket(int id, List<SynchedEntityData.DataValue<?>> packedItems)))
            return null;
        List<DataItem> items = new ArrayList<>(packedItems.size());
        for (SynchedEntityData.DataValue<?> dv : packedItems) {
            items.add(new DataItem(dv.id(), dv.value()));
        }
        return new ParsedSetEntityData(id, items);
    }

    @Override
    public ParsedPlayerInfoUpdate parsePlayerInfoUpdate(Object msg) {
        if (!(msg instanceof ClientboundPlayerInfoUpdatePacket p)) return null;
        Set<String> actions = p.actions().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        boolean touchesDisplayName = actions.contains("ADD_PLAYER")
                || actions.contains("UPDATE_DISPLAY_NAME");
        List<PlayerInfoEntry> entries = new ArrayList<>(p.entries().size());
        for (ClientboundPlayerInfoUpdatePacket.Entry e : p.entries()) {
            String displayNamePlain = (touchesDisplayName && e.displayName() != null)
                    ? e.displayName().getString()
                    : null;
            entries.add(new PlayerInfoEntry(
                    e.profileId(), e.profile(), e.listed(), e.latency(), e.gameMode(),
                    displayNamePlain,
                    e.showHat(), e.listOrder()));
        }
        return new ParsedPlayerInfoUpdate(actions, entries, p);
    }

    @Override
    public ParsedPlayerInfoRemove parsePlayerInfoRemove(Object msg) {
        if (!(msg instanceof ClientboundPlayerInfoRemovePacket(List<UUID> profileIds))) return null;
        return new ParsedPlayerInfoRemove(new ArrayList<>(profileIds));
    }

    @Override
    public ParsedSetObjective parseSetObjective(Object msg) {
        if (!(msg instanceof ClientboundSetObjectivePacket p)) return null;
        return new ParsedSetObjective(p.getMethod(), p.getObjectiveName());
    }

    @Override
    public ParsedSetDisplayObjective parseSetDisplayObjective(Object msg) {
        if (!(msg instanceof ClientboundSetDisplayObjectivePacket p)) return null;
        return new ParsedSetDisplayObjective(p.getSlot().name(), p.getObjectiveName());
    }

    @Override
    public ParsedSetScore parseSetScore(Object msg) {
        if (!(msg instanceof ClientboundSetScorePacket p)) return null;
        return new ParsedSetScore(p.owner(), p.objectiveName());
    }

    @Override
    public ParsedResetScore parseResetScore(Object msg) {
        if (!(msg instanceof ClientboundResetScorePacket(String owner, String objectiveName))) return null;
        return new ParsedResetScore(owner, objectiveName);
    }

    @Override
    public ParsedSetPlayerTeam parseSetPlayerTeam(Object msg) {
        if (!(msg instanceof ClientboundSetPlayerTeamPacket p)) return null;
        String teamAction = p.getTeamAction() == null ? "" : p.getTeamAction().name();
        String playerAction = p.getPlayerAction() == null ? "" : p.getPlayerAction().name();
        List<String> players = new ArrayList<>(p.getPlayers());

        NamedTextColor color = null;
        Component prefix = null, suffix = null;
        org.bukkit.scoreboard.Team.OptionStatus vis = null;

        if (p.getParameters().isPresent()) {
            var params = p.getParameters().get();
            color = extractColor(params);
            prefix = extractPrefix(params);
            suffix = extractSuffix(params);
            vis = extractVisibility(params);
        }
        return new ParsedSetPlayerTeam(p.getName(), teamAction, playerAction, players,
                color, prefix, suffix, vis);
    }

    private static NamedTextColor extractColor(Object params) {
        for (String methodName : new String[]{"getColor", "color"}) {
            try {
                java.lang.reflect.Method m = params.getClass().getMethod(methodName);
                Object result = m.invoke(params);
                switch (result) {
                    case net.minecraft.ChatFormatting cf -> {
                        net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromLegacyFormat(cf);
                        return tc == null ? null : NamedTextColor.namedColor(tc.getValue());
                    }
                    case Integer rgb -> {
                        return NamedTextColor.namedColor(rgb);
                    }
                    case null, default -> {
                        return null;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Component extractPrefix(Object params) {
        for (String methodName : new String[]{"getPlayerPrefix", "prefix"}) {
            try {
                java.lang.reflect.Method m = params.getClass().getMethod(methodName);
                Object result = m.invoke(params);
                if (result == null) return null;
                java.lang.reflect.Method getString = result.getClass().getMethod("getString");
                String text = (String) getString.invoke(result);
                return LEGACY.deserialize(text);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Component extractSuffix(Object params) {
        for (String methodName : new String[]{"getPlayerSuffix", "suffix"}) {
            try {
                java.lang.reflect.Method m = params.getClass().getMethod(methodName);
                Object result = m.invoke(params);
                if (result == null) return null;
                java.lang.reflect.Method getString = result.getClass().getMethod("getString");
                String text = (String) getString.invoke(result);
                return LEGACY.deserialize(text);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static org.bukkit.scoreboard.Team.OptionStatus extractVisibility(Object params) {
        for (String methodName : new String[]{"getNametagVisibility", "nametagVisibility"}) {
            try {
                java.lang.reflect.Method m = params.getClass().getMethod(methodName);
                Object result = m.invoke(params);
                return switch (result) {
                    case net.minecraft.world.scores.Team.Visibility v -> mapVisibility(v);
                    case String s -> switch (s) {
                        case "never", "NEVER" -> org.bukkit.scoreboard.Team.OptionStatus.NEVER;
                        case "hideForOtherTeams", "HIDE_FOR_OTHER_TEAMS" ->
                                org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM;
                        case "hideForOwnTeam", "HIDE_FOR_OWN_TEAM" ->
                                org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS;
                        default -> org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
                    };
                    case null, default -> org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
                };
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
            }
        }
        return null;
    }

    private static org.bukkit.scoreboard.Team.OptionStatus mapVisibility(net.minecraft.world.scores.Team.Visibility v) {
        if (v == null) return org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
        return switch (v) {
            case ALWAYS -> org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
            case NEVER -> org.bukkit.scoreboard.Team.OptionStatus.NEVER;
            case HIDE_FOR_OTHER_TEAMS -> org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM;
            case HIDE_FOR_OWN_TEAM -> org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS;
        };
    }

    @Override
    public Object rebuildPlayerInfoUpdate(ParsedPlayerInfoUpdate orig, Map<UUID, Component> forcedDisplayNames) {
        ClientboundPlayerInfoUpdatePacket src = (ClientboundPlayerInfoUpdatePacket) orig.rawPacket();
        if (forcedDisplayNames == null || forcedDisplayNames.isEmpty()) return src;

        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = src.actions();
        boolean rewrite = false;
        List<ClientboundPlayerInfoUpdatePacket.Entry> rebuilt = new ArrayList<>(src.entries().size());

        for (ClientboundPlayerInfoUpdatePacket.Entry e : src.entries()) {
            Component forced = forcedDisplayNames.get(e.profileId());
            if (forced != null) {
                rebuilt.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        e.profileId(), e.profile(), e.listed(), e.latency(),
                        e.gameMode(), PaperAdventure.asVanilla(forced),
                        e.showHat(), e.listOrder(), e.chatSession()));
                rewrite = true;
            } else {
                rebuilt.add(e);
            }
        }
        return rewrite ? new ClientboundPlayerInfoUpdatePacket(actions, rebuilt) : src;
    }

    @Override
    public boolean hasDisconnected(Player player) {
        return ((CraftPlayer) player).getHandle().hasDisconnected();
    }

    @Override
    public void forEachServerPlayer(Consumer<Player> action) {
        for (ServerPlayer sp : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            action.accept(sp.getBukkitEntity());
        }
    }

    @Override
    public double[] getEntityBoundingBox(Entity entity) {
        AABB box = ((CraftEntity) entity).getHandle().getBoundingBox();
        return new double[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
    }

    @Override
    public boolean isBlockSolidAt(World world, int x, int y, int z) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        var chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null) return false;
        int si = level.getSectionIndex(y);
        var secs = chunk.getSections();
        if (si < 0 || si >= secs.length) return false;
        var section = secs[si];
        if (section == null || section.hasOnlyAir()) return false;
        try {
            return section.getBlockState(x & 15, y & 15, z & 15).isSolidRender();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void getAllEntitiesInBox(World world,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ,
                                    Consumer<Entity> consumer) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        level.getEntities().get(new AABB(minX, minY, minZ, maxX, maxY, maxZ), e -> consumer.accept(e.getBukkitEntity()));
    }

    @Override
    public void resendAllTeamsTo(Player viewer) {
        ServerPlayer nmsViewer = ((CraftPlayer) viewer).getHandle();
        net.minecraft.world.scores.Scoreboard nmsBoard = MinecraftServer.getServer().getScoreboard();
        Scoreboard bukkitBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team team : bukkitBoard.getTeams()) {
            net.minecraft.world.scores.PlayerTeam nmsTeam = nmsBoard.getPlayerTeam(team.getName());
            if (nmsTeam == null) continue;
            nmsViewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(nmsTeam, true));
            for (String member : nmsTeam.getPlayers()) {
                nmsViewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        nmsTeam, member, ClientboundSetPlayerTeamPacket.Action.ADD));
            }
        }
    }

    @Override
    public io.netty.channel.Channel getChannel(Player player) {
        ServerPlayer nms = ((CraftPlayer) player).getHandle();
        return nms.connection.connection.channel;
    }
}