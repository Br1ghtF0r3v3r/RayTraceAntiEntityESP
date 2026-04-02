package RayTraceAntiEntityESP.manager.events;

import RayTraceAntiEntityESP.config.Config;
import RayTraceAntiEntityESP.utils.DebugsUtils;
import RayTraceAntiEntityESP.utils.FakeNameDisplay;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import io.papermc.paper.event.player.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import static RayTraceAntiEntityESP.manager.engine.PacketFilterManager.packetFilter;

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

    public static void playerLeaveManager(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (Config.isFakeDisplayNameEnabled) {
            FakeNameDisplay.removeDisplay(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                FakeNameDisplay.removeDisplay(online, player);
            }
        }
        if (Config.isDebugEnabled) {
            DebugsUtils.removeDisplay(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                DebugsUtils.removeDisplay(online, player);
            }
        }
    }

    public static void packetSendManager(PacketSendEvent event) {
        packetFilter(event);
    }

    public static void entityDeathManager(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (Config.isDebugEnabled) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                DebugsUtils.removeDisplay(online, entity);
            }
        }
        if (Config.isFakeDisplayNameEnabled) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                FakeNameDisplay.removeDisplay(online, entity);
            }
        }
    }

}
