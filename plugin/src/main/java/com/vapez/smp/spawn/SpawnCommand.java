package com.vapez.smp.spawn;

import com.vapez.smp.VapezPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SpawnCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public SpawnCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        player.teleportAsync(plugin.settings().spawnLocation(
                plugin.getServer().getWorld(plugin.settings().overworldName()) == null
                        ? player.getWorld()
                        : plugin.getServer().getWorld(plugin.settings().overworldName())));
        player.sendMessage(plugin.settings().prefix().append(Component.text("Welcome to the Aether Citadel.", NamedTextColor.GRAY)));
        return true;
    }
}
