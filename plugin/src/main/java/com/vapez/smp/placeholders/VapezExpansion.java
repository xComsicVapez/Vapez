package com.vapez.smp.placeholders;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.stats.PlayerStats;
import com.vapez.smp.stats.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VapezExpansion extends PlaceholderExpansion {

    private final VapezPlugin plugin;

    public VapezExpansion(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vapez";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Vapez";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        StatsManager stats = plugin.stats();
        if (params.equalsIgnoreCase("kills") && player != null) {
            return String.valueOf(stats.of(player.getUniqueId()).kills);
        }
        if (params.equalsIgnoreCase("deaths") && player != null) {
            return String.valueOf(stats.of(player.getUniqueId()).deaths);
        }
        if (params.equalsIgnoreCase("level") && player != null) {
            return String.valueOf(stats.of(player.getUniqueId()).level);
        }
        if (params.equalsIgnoreCase("xp") && player != null) {
            return String.valueOf(stats.of(player.getUniqueId()).xp);
        }
        if (params.equalsIgnoreCase("playtime") && player != null) {
            return stats.formatPlaytime(stats.of(player.getUniqueId()).playtimeMillis);
        }
        if (params.equalsIgnoreCase("hearts") && player != null) {
            return String.valueOf(plugin.lifesteal().hearts(player));
        }
        if (params.equalsIgnoreCase("balance") && player != null) {
            return plugin.vault().format(plugin.vault().balance(player));
        }
        if (params.equalsIgnoreCase("border")) {
            var world = plugin.getServer().getWorld(plugin.settings().overworldName());
            return world == null ? "?" : String.valueOf((int) world.getWorldBorder().getSize());
        }
        if (params.startsWith("kills_top_")) {
            return topName(stats.topKills(plugin.settings().hologramTop()), index(params, "kills_top_"), true);
        }
        if (params.startsWith("kills_topval_")) {
            return topKillsValue(stats.topKills(plugin.settings().hologramTop()), index(params, "kills_topval_"));
        }
        if (params.startsWith("playtime_top_")) {
            return topPlaytime(stats.topPlaytime(plugin.settings().hologramTop()), index(params, "playtime_top_"));
        }
        if (params.startsWith("bal_top_")) {
            return topWallet(stats.topWallets(plugin.settings().hologramTop()), index(params, "bal_top_"));
        }
        if (params.startsWith("level_top_")) {
            return topName(stats.topLevels(plugin.settings().hologramTop()), index(params, "level_top_"), false);
        }
        return null;
    }

    private int index(String params, String prefix) {
        try {
            return Integer.parseInt(params.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String topName(List<PlayerStats> list, int oneBased, boolean kills) {
        int i = oneBased - 1;
        if (i < 0 || i >= list.size()) {
            return "—";
        }
        PlayerStats s = list.get(i);
        return (i + 1) + ". " + s.name + " " + (kills ? s.kills : ("Lv." + s.level));
    }

    private String topKillsValue(List<PlayerStats> list, int oneBased) {
        int i = oneBased - 1;
        if (i < 0 || i >= list.size()) {
            return "0";
        }
        return String.valueOf(list.get(i).kills);
    }

    private String topPlaytime(List<PlayerStats> list, int oneBased) {
        int i = oneBased - 1;
        if (i < 0 || i >= list.size()) {
            return "—";
        }
        PlayerStats s = list.get(i);
        return (i + 1) + ". " + s.name + " " + plugin.stats().formatPlaytime(s.playtimeMillis);
    }

    private String topWallet(List<StatsManager.Wallet> list, int oneBased) {
        int i = oneBased - 1;
        if (i < 0 || i >= list.size()) {
            return "—";
        }
        StatsManager.Wallet w = list.get(i);
        return (i + 1) + ". " + w.name() + " " + plugin.vault().format(w.amount());
    }
}
