package com.vapez.smp.forge;

import com.vapez.smp.VapezPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AetherforgeCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public AetherforgeCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("vapez.forge")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        plugin.getServer().getPluginManager().getPlugin("VapezCore");
        new ForgeListener(plugin).open(player);
        return true;
    }
}
