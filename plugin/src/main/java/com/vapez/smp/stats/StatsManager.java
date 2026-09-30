package com.vapez.smp.stats;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.shop.VaultHook;
import com.vapez.smp.util.Tasks;
import com.vapez.smp.util.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsManager {

    private final VapezPlugin plugin;
    private final YamlStore store;
    private final Map<UUID, PlayerStats> data = new ConcurrentHashMap<>();
    private final Map<UUID, Double> onlineBalanceCache = new ConcurrentHashMap<>();

    public StatsManager(VapezPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "stats.yml");
        load();
    }

    private void load() {
        ConfigurationSection root = store.yaml().getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                PlayerStats stats = new PlayerStats();
                stats.uuid = uuid;
                stats.name = s.getString("name", "unknown");
                stats.kills = s.getInt("kills");
                stats.deaths = s.getInt("deaths");
                stats.playtimeMillis = s.getLong("playtime");
                stats.level = Math.max(1, s.getInt("level", 1));
                stats.xp = s.getInt("xp");
                stats.aetheriumMined = s.getInt("aetherium");
                stats.bounty = s.getDouble("bounty");
                data.put(uuid, stats);
            } catch (IllegalArgumentException ignored) {
                // skip bad keys
            }
        }
    }

    public void startTasks() {
        Tasks.repeat(20L * 30, 20L * 30, this::saveSync);
    }

    public PlayerStats of(UUID uuid) {
        return data.computeIfAbsent(uuid, id -> {
            PlayerStats s = new PlayerStats();
            s.uuid = id;
            return s;
        });
    }

    public void ensure(Player player) {
        PlayerStats stats = of(player.getUniqueId());
        stats.name = player.getName();
        stats.uuid = player.getUniqueId();
    }

    public void touch(Player player) {
        ensure(player);
    }

    public void addKill(UUID uuid) {
        of(uuid).kills++;
    }

    public void addDeath(UUID uuid) {
        of(uuid).deaths++;
    }

    public void addPlaytime(UUID uuid, long millis) {
        of(uuid).playtimeMillis += millis;
    }

    public void addMined(UUID uuid, int amount) {
        of(uuid).aetheriumMined += amount;
    }

    public int size() {
        return data.size();
    }

    public List<PlayerStats> topKills(int n) {
        return data.values().stream()
                .sorted(Comparator.comparingInt((PlayerStats s) -> s.kills).reversed())
                .limit(n)
                .toList();
    }

    public List<PlayerStats> topPlaytime(int n) {
        return data.values().stream()
                .sorted(Comparator.comparingLong((PlayerStats s) -> s.playtimeMillis).reversed())
                .limit(n)
                .toList();
    }

    public List<PlayerStats> topLevels(int n) {
        return data.values().stream()
                .sorted(Comparator.comparingInt((PlayerStats s) -> s.level).reversed()
                        .thenComparingInt(s -> s.xp).reversed())
                .limit(n)
                .toList();
    }

    public record Wallet(String name, double amount) {
    }

    public List<Wallet> topWallets(int n) {
        VaultHook vault = plugin.vault();
        if (!vault.available()) {
            return List.of();
        }
        List<Wallet> wallets = new ArrayList<>();
        for (PlayerStats stats : data.values()) {
            double bal = vault.balance(stats.uuid);
            if (Double.isNaN(bal)) {
                continue;
            }
            wallets.add(new Wallet(stats.name.isEmpty() ? stats.uuid.toString() : stats.name, bal));
        }
        wallets.sort(Comparator.comparingDouble((Wallet w) -> w.amount).reversed());
        if (wallets.size() > n) {
            return new ArrayList<>(wallets.subList(0, n));
        }
        return wallets;
    }

    public String formatPlaytime(long millis) {
        long hours = millis / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        return hours + "h " + minutes + "m";
    }

    public void saveSync() {
        for (PlayerStats stats : data.values()) {
            String path = "players." + stats.uuid;
            store.yaml().set(path + ".name", stats.name);
            store.yaml().set(path + ".kills", stats.kills);
            store.yaml().set(path + ".deaths", stats.deaths);
            store.yaml().set(path + ".playtime", stats.playtimeMillis);
            store.yaml().set(path + ".level", stats.level);
            store.yaml().set(path + ".xp", stats.xp);
            store.yaml().set(path + ".aetherium", stats.aetheriumMined);
            store.yaml().set(path + ".bounty", stats.bounty);
        }
        store.save();
    }
}
