package com.vapez.smp.guard;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Positions;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class SpawnGuardListener implements Listener {

    private final VapezPlugin plugin;

    public SpawnGuardListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean inSpawn(Location loc) {
        if (!plugin.settings().fallbackGuard() || loc == null || loc.getWorld() == null) {
            return false;
        }
        World world = loc.getWorld();
        if (!world.getName().equals(plugin.settings().overworldName())) {
            return false;
        }
        Location center = world.getSpawnLocation();
        return Positions.inside(loc.getBlockX(), loc.getBlockZ(),
                center.getBlockX(), center.getBlockZ(), plugin.settings().spawnRadius());
    }

    private boolean bypass(Player player) {
        return player != null && player.hasPermission("vapez.admin");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.settings().denyBreak() && inSpawn(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.settings().denyPlace() && inSpawn(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (plugin.settings().denyPlace() && inSpawn(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.settings().denyDamage()) {
            return;
        }
        if (event.getEntity() instanceof Player player && inSpawn(player.getLocation()) && !bypass(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setRemainingAir(player.getMaximumAir());
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleportAsync(player.getWorld().getSpawnLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!plugin.settings().denyHunger()) {
            return;
        }
        if (event.getEntity() instanceof Player player && inSpawn(player.getLocation()) && !bypass(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(ExplosionPrimeEvent event) {
        if (inSpawn(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeBlocks(EntityExplodeEvent event) {
        if (inSpawn(event.getLocation())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.settings().denyMobs()) {
            return;
        }
        if (event.getEntity() instanceof Monster && inSpawn(event.getLocation())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (inSpawn(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (inSpawn(event.getBlock().getLocation()) && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent event) {
        if (inSpawn(event.getEntity().getLocation())
                && event.getRemover() instanceof Player player && !bypass(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (inSpawn(event.getRightClicked().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (plugin.settings().denyDamage() && inSpawn(event.getTo()) && !bypass(event.getPlayer())) {
            event.getPlayer().setFireTicks(0);
        }
    }
}
