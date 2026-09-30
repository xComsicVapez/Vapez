package com.vapez.smp.warp;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Tasks;
import com.vapez.smp.util.Text;
import com.vapez.smp.util.YamlStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarpManager implements Listener {

    public record Warp(String name, UUID owner, String ownerName, String world, double x, double y, double z, float yaw, float pitch) {
        Location location() {
            World w = Bukkit.getWorld(world);
            if (w == null) {
                return null;
            }
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final VapezPlugin plugin;
    private final YamlStore store;
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();
    private final Set<UUID> warming = new HashSet<>();

    public WarpManager(VapezPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "warps.yml");
        load();
    }

    public int size() {
        return warps.size();
    }

    public Warp get(String name) {
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public long owned(UUID uuid) {
        return warps.values().stream().filter(w -> w.owner().equals(uuid)).count();
    }

    public boolean set(Player player, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (warps.containsKey(key) && !warps.get(key).owner().equals(player.getUniqueId())
                && !player.hasPermission("vapez.admin")) {
            player.sendMessage(Component.text("That warp already exists.", NamedTextColor.RED));
            return false;
        }
        if (owned(player.getUniqueId()) >= plugin.settings().maxWarps() && !warps.containsKey(key)) {
            player.sendMessage(Component.text("Warp limit reached.", NamedTextColor.RED));
            return false;
        }
        if (!plugin.vault().withdraw(player, plugin.settings().warpCost())) {
            player.sendMessage(Component.text("Need " + plugin.vault().format(plugin.settings().warpCost()) + " to set a warp.", NamedTextColor.RED));
            return false;
        }
        Location loc = player.getLocation();
        warps.put(key, new Warp(key, player.getUniqueId(), player.getName(), loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        saveSync();
        return true;
    }

    public boolean delete(Player player, String name) {
        Warp warp = get(name);
        if (warp == null) {
            return false;
        }
        if (!warp.owner().equals(player.getUniqueId()) && !player.hasPermission("vapez.admin")) {
            return false;
        }
        warps.remove(warp.name());
        saveSync();
        return true;
    }

    public void teleport(Player player, String name) {
        Warp warp = get(name);
        if (warp == null) {
            player.sendMessage(Component.text("Unknown warp.", NamedTextColor.RED));
            return;
        }
        Location loc = warp.location();
        if (loc == null) {
            player.sendMessage(Component.text("World is not loaded.", NamedTextColor.RED));
            return;
        }
        int warmup = plugin.settings().warpWarmup();
        if (warmup <= 0 || player.hasPermission("vapez.admin")) {
            player.teleportAsync(loc);
            return;
        }
        warming.add(player.getUniqueId());
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gray>Teleporting in <white>" + warmup + "s</white>. Don't move.</gray>")));
        Tasks.later(warmup * 20L, () -> {
            if (warming.remove(player.getUniqueId())) {
                player.teleportAsync(loc);
            }
        });
    }

    public Component list() {
        if (warps.isEmpty()) {
            return Component.text("No player warps yet. /pwarp set <name>", NamedTextColor.GRAY);
        }
        Component out = Text.mm("<aqua><bold>Player Warps</bold></aqua>");
        for (Warp warp : warps.values()) {
            out = out.append(Component.newline()).append(Text.mm(
                    "<yellow>/" + "pwarp " + warp.name() + "</yellow> <dark_gray>by</dark_gray> <white>" + warp.ownerName() + "</white>"));
        }
        return out;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warps.warmup-cancel-on-move", true)) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (warming.remove(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(Component.text("Teleport cancelled.", NamedTextColor.RED));
        }
    }

    public void saveSync() {
        store.yaml().set("warps", null);
        for (Warp warp : warps.values()) {
            String path = "warps." + warp.name();
            store.yaml().set(path + ".owner", warp.owner().toString());
            store.yaml().set(path + ".ownerName", warp.ownerName());
            store.yaml().set(path + ".world", warp.world());
            store.yaml().set(path + ".x", warp.x());
            store.yaml().set(path + ".y", warp.y());
            store.yaml().set(path + ".z", warp.z());
            store.yaml().set(path + ".yaw", warp.yaw());
            store.yaml().set(path + ".pitch", warp.pitch());
        }
        store.save();
    }

    private void load() {
        ConfigurationSection root = store.yaml().getConfigurationSection("warps");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            try {
                warps.put(name, new Warp(name, UUID.fromString(s.getString("owner")), s.getString("ownerName", "unknown"),
                        s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
            } catch (Exception ignored) {
                // skip
            }
        }
    }
}
