package com.vapez.smp.border;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
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

public final class BorderCommand implements CommandExecutor, TabCompleter {

    private final VapezPlugin plugin;

    public BorderCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        World world = sender instanceof Player player
                ? player.getWorld()
                : plugin.getServer().getWorld(plugin.settings().overworldName());
        if (world == null) {
            sender.sendMessage(Component.text("World not loaded.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<gray>Border diameter:</gray> <white>" + (int) plugin.border().current(world)
                            + "</white> <dark_gray>(max " + (int) plugin.settings().maxBorder() + ")</dark_gray>")));
            sender.sendMessage(Text.mm("<gray>Far Lands engine:</gray> "
                    + (plugin.settings().farlandsEnabled() ? "<green>ON</green>" : "<red>OFF</red>")
                    + " <dark_gray>threshold " + plugin.settings().farlandsThreshold() + "</dark_gray>"));
            sender.sendMessage(Text.mm("<gray>Vanilla ops command is</gray> <white>/minecraft:worldborder</white>"));
            return true;
        }
        if (!sender.hasPermission("vapez.border")) {
            sender.sendMessage(Component.text("No permission to mutate the border.", NamedTextColor.RED));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/worldborder set <diameter>", NamedTextColor.YELLOW));
                    return true;
                }
                plugin.border().set(world, Double.parseDouble(args[1]));
                sender.sendMessage(Text.mm("<green>Border set to " + (int) plugin.border().current(world) + "</green>"));
            }
            case "expand" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/worldborder expand <blocks>", NamedTextColor.YELLOW));
                    return true;
                }
                plugin.border().expand(world, Double.parseDouble(args[1]));
                sender.sendMessage(Text.mm("<green>Border expanded to " + (int) plugin.border().current(world) + "</green>"));
            }
            case "toggle", "farlands" -> {
                boolean on = plugin.border().toggleFarlands();
                sender.sendMessage(Text.mm("Far Lands engine: " + (on ? "<green>enabled</green>" : "<red>disabled</red>")
                        + "<gray>. Newly generated chunks past "
                        + plugin.settings().farlandsThreshold() + " will distort when enabled.</gray>"));
            }
            case "pregen" -> sender.sendMessage(Text.mm(
                    "<aqua>Run:</aqua> <white>chunky world world</white> then <white>chunky worldborder</white> then <white>chunky start</white>"));
            default -> sender.sendMessage(Component.text("/worldborder <status|set|expand|farlands|pregen>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Stream.of("status", "set", "expand", "farlands", "toggle", "pregen")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
