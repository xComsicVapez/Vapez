package com.vapez.smp;

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
import java.util.stream.Stream;

public final class VapezAdminCommand implements CommandExecutor, TabCompleter {

    private final VapezPlugin plugin;

    public VapezAdminCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vapez.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.settings().prefix().append(
                    Component.text("VapezCore " + plugin.getPluginMeta().getVersion(), NamedTextColor.AQUA)));
            sender.sendMessage(Component.text("/vapez reload | give | buildspawn | ore | stats", NamedTextColor.GRAY));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.settings().prefix().append(Component.text("Reloaded.", NamedTextColor.GREEN)));
            }
            case "buildspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                plugin.citadel().forceRebuild(player.getWorld());
                sender.sendMessage(plugin.settings().prefix().append(
                        Component.text("Citadel rebuild queued.", NamedTextColor.GREEN)));
            }
            case "give" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage(Component.text("/vapez give <item> [amount]", NamedTextColor.YELLOW));
                    return true;
                }
                int amount = args.length >= 3 ? parseInt(args[2], 1) : 1;
                var stack = plugin.items().create(args[1], amount);
                if (stack == null) {
                    sender.sendMessage(Component.text("Unknown item. Try aetherium_crystal, aetherium_ingot, sovereign_edge, heart.", NamedTextColor.RED));
                    return true;
                }
                player.getInventory().addItem(stack);
                sender.sendMessage(plugin.settings().prefix().append(Component.text("Gave " + args[1], NamedTextColor.GREEN)));
            }
            case "stats" -> sender.sendMessage(Component.text(
                    "Players tracked: " + plugin.stats().size()
                            + " | Listings: " + plugin.auctions().size()
                            + " | Warps: " + plugin.warps().size(), NamedTextColor.AQUA));
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Stream.of("reload", "give", "buildspawn", "stats")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Stream.of("aetherium_crystal", "aetherium_ingot", "sovereign_edge",
                            "aetherium_sword", "aetherium_pickaxe", "aetherium_axe",
                            "aetherium_helmet", "aetherium_chestplate", "aetherium_leggings",
                            "aetherium_boots", "heart", "crate_key")
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
