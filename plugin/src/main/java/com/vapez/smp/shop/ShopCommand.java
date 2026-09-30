package com.vapez.smp.shop;

import com.vapez.smp.VapezPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ShopCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public ShopCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("vapez.shop")) {
            return true;
        }
        player.openInventory(ShopCatalog.openRoot(plugin, player));
        return true;
    }
}
