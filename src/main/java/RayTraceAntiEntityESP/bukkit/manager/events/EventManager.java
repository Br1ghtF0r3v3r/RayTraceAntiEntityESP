package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.NametagCloneManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.VerticesDebugManager;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import io.papermc.paper.event.player.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import java.util.UUID;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;
import static RayTraceAntiEntityESP.bukkit.config.Config.isDisplayNameEnabled;

public class EventManager {

    public static void regainHealthManager(EntityRegainHealthEvent event) {
    }

    public static void statisticIncementManager(PlayerStatisticIncrementEvent event) {
    }

    public static void playerDeathManager(PlayerDeathEvent event) {
    }

    public static void entityDamageManager(EntityDamageByEntityEvent event) {
    }

    public static void playerInteractManager(PlayerInteractEvent event) {
    }

    public static void playerMoveManager(PlayerMoveEvent event) {
    }

    public static void playerTeleportManager(PlayerMoveEvent event) {
    }

    public static void inventoryClickManager(InventoryClickEvent event) {
    }

    public static void itemSpawnManager(ItemSpawnEvent event) {
    }

    public static void prepareCraftManager(PrepareItemCraftEvent event) {
    }

    public static void inventorySlotChangeManager(PlayerInventorySlotChangeEvent event) {
    }

    public static void dropManager(PlayerDropItemEvent event) {
    }

    public static void playerSwapHandItemsManager(PlayerSwapHandItemsEvent event) {
    }

    public static void playerPickupExperienceManager(PlayerExpChangeEvent event) {
    }

    public static void blockExplodeManager(BlockExplodeEvent event) {
    }

    public static void prepareSmithingManager(PrepareSmithingEvent event) {
    }

    public static void prepareGrindstoneManager(PrepareGrindstoneEvent event) {
    }

    public static void blockPlaceManager(BlockPlaceEvent event) {
    }

    public static void blockBreakManager(BlockBreakEvent event) {
    }

    public static void playerPickupArrowManager(PlayerPickupArrowEvent event) {
    }

    public static void playerJoinManager(PlayerJoinEvent event) {
    }

    public static void connectionCloseManager(PlayerConnectionCloseEvent event) {
        UUID playerUUID = event.getPlayerUniqueId();

        if (isDisplayNameEnabled) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                NametagCloneManager.removeDisplay(playerUUID);
                VerticesDebugManager.removeDisplay(playerUUID);
                NametagCloneManager.removeDisplayForEntity(playerUUID);
                VerticesDebugManager.removeDisplayForEntity(playerUUID);
            }, 20L);
        }
    }

    public static void playerLeaveManager(PlayerQuitEvent event) {
    }

    public static void packetSendManager(PacketSendEvent event) {
        PacketManager.packetManager(event);

    }

    public static void entityDeathManager(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityUUID = entity.getUniqueId();

        if (isDisplayNameEnabled) {
            NametagCloneManager.removeDisplayForEntity(entityUUID);
            VerticesDebugManager.removeDisplayForEntity(entityUUID);
        }
    }

}
