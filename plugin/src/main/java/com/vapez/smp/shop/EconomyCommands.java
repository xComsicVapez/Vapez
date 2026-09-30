package com.vapez.smp.shop;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class EconomyCommands implements CommandExecutor {

    private final VapezPlugin plugin;

    public EconomyCommands(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("balance") || name.equals("bal") || name.equals("money")) {
            return balance(sender, args);
        }
        if (name.equals("pay")) {
            return pay(sender, args);
        }
        return false;
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) && args.length == 0) {
            sender.sendMessage(Component.text("/bal <player>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = args.length == 0 ? (Player) sender : Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gray>Balance of <white>" + target.getName() + "</white>: <yellow>"
                        + plugin.vault().format(plugin.vault().balance(target)) + "</yellow>")));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("/pay <player> <amount>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            return true;
        }
        if (!plugin.vault().has(player, amount)) {
            player.sendMessage(Component.text("Not enough money.", NamedTextColor.RED));
            return true;
        }
        plugin.vault().withdraw(player, amount);
        plugin.vault().deposit(target, amount);
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gray>Paid <yellow>" + plugin.vault().format(amount) + "</yellow> to <white>" + target.getName() + "</white>.")));
        target.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gray>Received <yellow>" + plugin.vault().format(amount) + "</yellow> from <white>" + player.getName() + "</white>.")));
        return true;
    }
}
