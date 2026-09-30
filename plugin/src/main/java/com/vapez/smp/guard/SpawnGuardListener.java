package com.vapez.smp.guard;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Positions;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public final class SpawnGuardListener implements Listener {

    private final VapezPlugin plugin;

    public SpawnGuardListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean spawn(org.bukkit.Location loc) {
        return plugin.settings().fallbackGuard()
                && loc.getWorld() != null
                && loc.getWorld().getName().equals(plugin.settings().overworldName())
                && Positions.inside(loc, 0, 0, plugin.settings().spawnRadius());
    }

    private boolean bypass(Player player) {
        return player.hasPermission("vapez.admin");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (spawn(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (spawn(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (spawn(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && spawn(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(ExplosionPrimeEvent event) {
        if (spawn(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster && spawn(event.getLocation())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }
}
