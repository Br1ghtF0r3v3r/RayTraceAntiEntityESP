package RayTraceAntiEntityESP.bukkit.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import io.papermc.paper.event.player.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.jspecify.annotations.NonNull;

import static RayTraceAntiEntityESP.bukkit.manager.events.EventManager.*;

public class EventListener extends PacketListenerAbstract implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerJoinManager(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLeaveManager(event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        entityDamageManager(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        playerDeathManager(event);
    }

    @EventHandler
    public void onStatisticIncrement(PlayerStatisticIncrementEvent event) {
        statisticIncementManager(event);
    }

    @EventHandler
    public void onRegainHealth(EntityRegainHealthEvent event) {
        regainHealthManager(event);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        playerMoveManager(event);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        playerTeleportManager(event);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        playerInteractManager(event);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        inventoryClickManager(event);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        dropManager(event);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        prepareCraftManager(event);
    }

    @EventHandler
    public void onInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        inventorySlotChangeManager(event);
    }

    @EventHandler
    public void onPlayerPickupExperience(PlayerExpChangeEvent event) {
        playerPickupExperienceManager(event);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        blockExplodeManager(event);
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        prepareSmithingManager(event);
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        prepareGrindstoneManager(event);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        itemSpawnManager(event);
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        playerSwapHandItemsManager(event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        blockPlaceManager(event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        blockBreakManager(event);
    }

    @EventHandler
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        playerPickupArrowManager(event);
    }

    @Override
    public void onPacketSend(@NonNull PacketSendEvent event) {
        packetSendManager(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        entityDeathManager(event);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        connectionCloseManager(event);
    }
}