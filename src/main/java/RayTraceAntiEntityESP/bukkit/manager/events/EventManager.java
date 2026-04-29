package RayTraceAntiEntityESP.bukkit.manager.events;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.manager.engine.PacketManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.NametagCloneManager;
import RayTraceAntiEntityESP.bukkit.manager.engine.VerticesDebugManager;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import io.papermc.paper.event.player.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

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

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity entity : player.getWorld().getEntities()) {
                if (entity == player) continue;
                if (!player.canSee(entity) && isDisplayNameEnabled) {
                    NametagCloneManager.applyDisplay(player, entity);
                }
            }
        }, 20L);

    }

    public static void playerLeaveManager(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (isDisplayNameEnabled) {
            NametagCloneManager.removeDisplay(player);
            VerticesDebugManager.removeDisplay(player);
            NametagCloneManager.removeDisplayForEntity(player);
            VerticesDebugManager.removeDisplayForEntity(player);
        }
    }

    public static void packetSendManager(PacketSendEvent event) {
        PacketManager.packetManager(event);

    }

    public static void entityDeathManager(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (isDisplayNameEnabled) {
            NametagCloneManager.removeDisplayForEntity(entity);
            VerticesDebugManager.removeDisplayForEntity(entity);
        }
    }

}
