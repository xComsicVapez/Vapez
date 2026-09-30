package com.vapez.smp.stats;

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

public final class BountyCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public BountyCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Text.mm("<gold><bold>Bounties</bold></gold>"));
            plugin.stats();
            sender.sendMessage(Component.text("Place with /bounty add <player> <amount>", NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("add") && args.length >= 3 && sender instanceof Player player) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException ex) {
                return true;
            }
            if (amount < plugin.getConfig().getDouble("bounties.min", 100) || !plugin.vault().withdraw(player, amount)) {
                player.sendMessage(Component.text("Cannot place that bounty.", NamedTextColor.RED));
                return true;
            }
            plugin.stats().of(target.getUniqueId()).bounty += amount;
            plugin.stats().ensure(target);
            Bukkit.broadcast(plugin.settings().prefix().append(Text.mm(
                    "<gold>Bounty on <white>" + target.getName() + "</white> increased by "
                            + plugin.vault().format(amount) + "</gold>")));
            return true;
        }
        sender.sendMessage(Component.text("/bounty add <player> <amount>", NamedTextColor.YELLOW));
        return true;
    }
}
