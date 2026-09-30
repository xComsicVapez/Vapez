package com.vapez.smp.warp;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class WarpCommand implements CommandExecutor, TabCompleter {

    private final VapezPlugin plugin;

    public WarpCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("vapez.warp")) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            player.sendMessage(plugin.warps().list());
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
            if (!player.hasPermission("vapez.warp.set")) {
                return true;
            }
            if (plugin.warps().set(player, args[1])) {
                player.sendMessage(plugin.settings().prefix().append(Text.mm(
                        "<green>Created warp</green> <yellow>" + args[1].toLowerCase(Locale.ROOT) + "</yellow>.")));
            }
            return true;
        }
        if ((args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del")) && args.length >= 2) {
            if (plugin.warps().delete(player, args[1])) {
                player.sendMessage(Component.text("Warp deleted.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Cannot delete that warp.", NamedTextColor.RED));
            }
            return true;
        }
        plugin.warps().teleport(player, args[0]);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("set", "delete", "list");
        }
        return List.of();
    }
}
