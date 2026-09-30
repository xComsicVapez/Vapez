package com.vapez.smp.stats;

import com.vapez.smp.VapezPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StatsListener implements Listener {

    private final VapezPlugin plugin;

    public StatsListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.stats().ensure(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.stats().touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        switch (event.getBlock().getType()) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, ANCIENT_DEBRIS, EMERALD_ORE, DEEPSLATE_EMERALD_ORE ->
                    plugin.levels().addXp(event.getPlayer(), plugin.settings().oreXp(), "ore");
            default -> {
            }
        }
    }
}
