package com.vapez.smp.inspect;

import com.vapez.smp.VapezPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Shows spawn status on the Minecraft server list / mcsrvstat.us so you can
 * check from a phone without joining.
 */
public final class SpawnMotdListener implements Listener {

    private final VapezPlugin plugin;

    public SpawnMotdListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!plugin.settings().motdStatus()) {
            return;
        }
        SpawnReport report = plugin.inspector().report();
        String status = report.motdLine;
        String current = event.getMotd();
        String first = current == null ? plugin.settings().serverName() : current.split("\n", 2)[0];
        event.setMotd(first + "\n§7" + status);
    }
}
