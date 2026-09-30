package com.vapez.smp.level;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.stats.PlayerStats;
import com.vapez.smp.util.Tasks;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class LevelManager {

    private final VapezPlugin plugin;

    public LevelManager(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        if (!plugin.settings().levels()) {
            return;
        }
        Tasks.repeat(20L * 60, 20L * 60, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                addXp(player, plugin.settings().playtimeXp(), "playtime");
                plugin.stats().addPlaytime(player.getUniqueId(), 60_000L);
            }
        });
    }

    public void addXp(Player player, int amount, String reason) {
        if (!plugin.settings().levels() || amount == 0) {
            return;
        }
        PlayerStats stats = plugin.stats().of(player.getUniqueId());
        stats.xp += amount;
        int gained = 0;
        while (stats.level < plugin.settings().maxLevel() && stats.xp >= xpToNext(stats.level)) {
            stats.xp -= xpToNext(stats.level);
            stats.level++;
            gained++;
        }
        plugin.stats().touch(player);
        if (gained > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<yellow>Level up!</yellow> You are now <aqua>Lv. " + stats.level + "</aqua>.")));
        }
    }

    public int xpToNext(int level) {
        return (int) Math.round(plugin.settings().baseXp() * Math.pow(plugin.settings().xpGrowth(), Math.max(0, level - 1)));
    }

    public Component card(Player viewer, PlayerStats stats, String name) {
        int need = xpToNext(stats.level);
        int pct = need == 0 ? 100 : (int) Math.min(100, (stats.xp * 100L) / need);
        String bar = bar(pct);
        return Text.mm("<gradient:#7BFFE9:#C084FC><bold>" + name + "</bold></gradient>")
                .append(Component.newline())
                .append(Text.mm("<gray>Level:</gray> <aqua>" + stats.level + "</aqua>  "
                        + "<dark_gray>" + stats.xp + "/" + need + " XP</dark_gray>"))
                .append(Component.newline())
                .append(Text.mm(bar + " <white>" + pct + "%</white>"))
                .append(Component.newline())
                .append(Text.mm("<gray>Kills:</gray> <red>" + stats.kills + "</red>  "
                        + "<gray>Deaths:</gray> <white>" + stats.deaths + "</white>  "
                        + "<gray>Playtime:</gray> <green>" + format(stats.playtimeMillis) + "</green>"));
    }

    private String bar(int pct) {
        int filled = pct / 5;
        StringBuilder sb = new StringBuilder("<dark_gray>[</dark_gray>");
        for (int i = 0; i < 20; i++) {
            sb.append(i < filled ? "<aqua>■</aqua>" : "<gray>■</gray>");
        }
        sb.append("<dark_gray>]</dark_gray>");
        return sb.toString();
    }

    private String format(long millis) {
        long hours = millis / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        return hours + "h " + minutes + "m";
    }
}
