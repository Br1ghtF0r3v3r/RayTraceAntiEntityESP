package RayTraceAntiEntityESP.bukkit.listener;

import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.manager.events.EventManager;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.StructureGrowEvent;

public class EventListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        EventManager.playerJoinHandler(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        EventManager.entityDeathHandler(event);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        EventManager.connectionCloseHandler(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        EventManager.playerRespawnHandler(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        EventManager.playerQuitHandler(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        invalidate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        invalidate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        invalidate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        for (Block block : event.blockList()) invalidate(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) invalidate(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        invalidate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        invalidate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        invalidate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            RayTraceEngine.invalidateBlockAt(state.getX(), state.getY(), state.getZ());
        }
    }

    private static void invalidate(Block block) {
        RayTraceEngine.invalidateBlockAt(block.getX(), block.getY(), block.getZ());
    }
}