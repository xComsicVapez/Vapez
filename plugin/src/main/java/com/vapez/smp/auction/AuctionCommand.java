package com.vapez.smp.auction;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class AuctionCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public AuctionCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("vapez.auction")) {
            return true;
        }
        if (args.length == 0) {
            plugin.auctions().open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("sell") && args.length >= 2) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                player.sendMessage(Component.text("Hold the item you want to list.", NamedTextColor.RED));
                return true;
            }
            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(Component.text("Invalid price.", NamedTextColor.RED));
                return true;
            }
            if (price <= 0) {
                return true;
            }
            ItemStack listing = hand.clone();
            if (plugin.auctions().sell(player, listing, price)) {
                hand.setAmount(0);
                player.sendMessage(plugin.settings().prefix().append(Text.mm(
                        "<green>Listed</green> for <yellow>" + plugin.vault().format(price) + "</yellow>.")));
            }
            return true;
        }
        player.sendMessage(Component.text("/ah  |  /ah sell <price>", NamedTextColor.YELLOW));
        return true;
    }
}
