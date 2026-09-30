package com.vapez.smp.crate;

import com.vapez.smp.VapezPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class CrateCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public CrateCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("vapez.crates.give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            int amount = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            target.getInventory().addItem(plugin.items().crateKey(amount));
            sender.sendMessage(Component.text("Gave keys.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Open Citadel chests north of spawn with a crate key. /crates give <player> [amount]", NamedTextColor.GRAY));
        return true;
    }
}
