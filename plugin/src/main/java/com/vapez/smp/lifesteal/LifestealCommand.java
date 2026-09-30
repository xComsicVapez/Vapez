package com.vapez.smp.lifesteal;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class LifestealCommand implements CommandExecutor {

    private final VapezPlugin plugin;

    public LifestealCommand(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<red>Hearts:</red> <white>" + plugin.lifesteal().hearts(player) + "</white>")));
            return true;
        }
        if (args[0].equalsIgnoreCase("withdraw")) {
            if (plugin.lifesteal().withdraw(player)) {
                player.sendMessage(Text.mm("<red>Withdrew 1 heart into an item.</red>"));
            } else {
                player.sendMessage(Component.text("Cannot withdraw.", NamedTextColor.RED));
            }
            return true;
        }
        player.sendMessage(Component.text("/lifesteal  |  /lifesteal withdraw", NamedTextColor.YELLOW));
        return true;
    }
}
