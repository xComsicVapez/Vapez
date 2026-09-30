package com.vapez.smp.stats;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpCommand implements CommandExecutor {

    private final VapezPlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public RtpCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("vapez.rtp")) {
            return true;
        }
        long now = System.currentTimeMillis();
        long ready = cooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            player.sendMessage(Component.text("RTP cooldown.", NamedTextColor.RED));
            return true;
        }
        World world = player.getWorld();
        double radius = Math.max(32, world.getWorldBorder().getSize() / 2.0 - 32);
        int min = plugin.settings().rtpMin();
        for (int i = 0; i < plugin.settings().rtpAttempts(); i++) {
            int x = rand((int) radius);
            int z = rand((int) radius);
            if (Math.abs(x) < min && Math.abs(z) < min) {
                continue;
            }
            Block highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (highest.isLiquid() || !highest.getType().isSolid()) {
                continue;
            }
            Location loc = highest.getLocation().add(0.5, 1, 0.5);
            loc.setPitch(0);
            player.teleportAsync(loc);
            cooldown.put(player.getUniqueId(), now + plugin.settings().rtpCooldown() * 1000L);
            player.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<gray>Wild teleport to</gray> <white>" + x + " " + z + "</white>")));
            return true;
        }
        player.sendMessage(Component.text("Could not find a safe spot. Try again.", NamedTextColor.RED));
        return true;
    }

    private int rand(int radius) {
        int v = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        return v;
    }
}
