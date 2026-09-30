package com.vapez.smp.config;

import com.vapez.smp.VapezPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Settings {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final VapezPlugin plugin;
    private FileConfiguration cfg;

    public Settings(VapezPlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        this.cfg = plugin.getConfig();
    }

    public Component prefix() {
        return MM.deserialize(cfg.getString("prefix", "<aqua>Vapez</aqua> <dark_gray>»</dark_gray> "));
    }

    public String serverName() {
        return cfg.getString("server-name", "Vapez SMP");
    }

    public String overworldName() {
        return cfg.getString("world.overworld", "world");
    }

    public Location spawnLocation(World world) {
        return new Location(
                world,
                cfg.getDouble("world.spawn.x", 0.5),
                cfg.getDouble("world.spawn.y", 67.0),
                cfg.getDouble("world.spawn.z", 0.5),
                (float) cfg.getDouble("world.spawn.yaw", 180.0),
                (float) cfg.getDouble("world.spawn.pitch", 0.0)
        );
    }

    public double initialBorder() {
        return cfg.getDouble("border.initial-diameter", 10000);
    }

    public double maxBorder() {
        return cfg.getDouble("border.max-diameter", 29_999_984);
    }

    public int farlandsThreshold() {
        return cfg.getInt("border.farlands-threshold", 12_550_821);
    }

    public boolean farlandsEnabled() {
        return cfg.getBoolean("border.farlands-enabled", false);
    }

    public void farlandsEnabled(boolean value) {
        cfg.set("border.farlands-enabled", value);
        plugin.saveConfig();
    }

    public boolean farlandsOverlay() {
        return cfg.getBoolean("border.farlands-overlay", true);
    }

    public boolean oreEnabled() {
        return cfg.getBoolean("ore.enabled", true);
    }

    public int oreChunkDivisor() {
        return Math.max(8, cfg.getInt("ore.chunk-divisor", 72));
    }

    public int oreMinY() {
        return cfg.getInt("ore.min-y", -64);
    }

    public int oreMaxY() {
        return cfg.getInt("ore.max-y", -50);
    }

    public int veinMin() {
        return Math.max(1, cfg.getInt("ore.vein-size-min", 1));
    }

    public int veinMax() {
        return Math.max(veinMin(), cfg.getInt("ore.vein-size-max", 3));
    }

    public Material oreBlock() {
        return material(cfg.getString("ore.block", "HEAVY_CORE"), Material.HEAVY_CORE);
    }

    public Set<Material> replaceable() {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : cfg.getStringList("ore.replaceable")) {
            Material mat = material(name, null);
            if (mat != null) {
                set.add(mat);
            }
        }
        if (set.isEmpty()) {
            set.add(Material.DEEPSLATE);
            set.add(Material.STONE);
            set.add(Material.TUFF);
        }
        return set;
    }

    public Material requiredTool() {
        return material(cfg.getString("ore.required-tool", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE);
    }

    public boolean oneCraft() {
        return cfg.getBoolean("forge.one-craft-per-lifetime", true);
    }

    public boolean kitEnabled() {
        return cfg.getBoolean("starter-kit.enabled", true);
    }

    public double starterMoney() {
        return cfg.getDouble("starter-kit.money", 500.0);
    }

    public List<String> kitContents() {
        return cfg.getStringList("starter-kit.contents");
    }

    public boolean lifesteal() {
        return cfg.getBoolean("lifesteal.enabled", true);
    }

    public int minHearts() {
        return Math.max(1, cfg.getInt("lifesteal.min-hearts", 1));
    }

    public int maxHearts() {
        return Math.max(minHearts(), cfg.getInt("lifesteal.max-hearts", 20));
    }

    public double healthPerHeart() {
        return cfg.getDouble("lifesteal.health-per-heart", 2.0);
    }

    public int heartsPerKill() {
        return Math.max(1, cfg.getInt("lifesteal.hearts-per-kill", 1));
    }

    public boolean dropHeartItem() {
        return cfg.getBoolean("lifesteal.drop-heart-item", true);
    }

    public boolean levels() {
        return cfg.getBoolean("levels.enabled", true);
    }

    public int playtimeXp() {
        return cfg.getInt("levels.playtime-xp-per-minute", 8);
    }

    public int killXp() {
        return cfg.getInt("levels.kill-xp", 40);
    }

    public int oreXp() {
        return cfg.getInt("levels.ore-xp", 3);
    }

    public int aetheriumXp() {
        return cfg.getInt("levels.aetherium-xp", 50);
    }

    public double baseXp() {
        return cfg.getDouble("levels.base-xp", 100);
    }

    public double xpGrowth() {
        return cfg.getDouble("levels.xp-growth", 1.18);
    }

    public int maxLevel() {
        return cfg.getInt("levels.max-level", 100);
    }

    public double sellTax() {
        return cfg.getDouble("shop.sell-tax", 0.05);
    }

    public double listingFee() {
        return cfg.getDouble("auction.listing-fee", 25.0);
    }

    public int maxListings() {
        return cfg.getInt("auction.max-listings-per-player", 12);
    }

    public long auctionMillis() {
        return cfg.getLong("auction.duration-hours", 48) * 3_600_000L;
    }

    public int maxWarps() {
        return cfg.getInt("warps.max-per-player", 3);
    }

    public double warpCost() {
        return cfg.getDouble("warps.set-cost", 2500.0);
    }

    public int warpWarmup() {
        return cfg.getInt("warps.teleport-warmup-seconds", 3);
    }

    public boolean buildCitadel() {
        return cfg.getBoolean("spawn.build-on-first-boot", true);
    }

    public int spawnRadius() {
        return cfg.getInt("spawn.protection-radius", 128);
    }

    public boolean fallbackGuard() {
        return cfg.getBoolean("spawn.fallback-guard", true);
    }

    public int hologramTop() {
        return cfg.getInt("holograms.top-size", 10);
    }

    public int rtpMin() {
        return cfg.getInt("rtp.min-distance", 250);
    }

    public int rtpAttempts() {
        return cfg.getInt("rtp.max-attempts", 16);
    }

    public int rtpCooldown() {
        return cfg.getInt("rtp.cooldown-seconds", 120);
    }

    public int particleInterval() {
        return Math.max(5, cfg.getInt("items.particle-interval-ticks", 10));
    }

    public int requiredIngots() {
        return cfg.getInt("forge.required.aetherium-ingots", 8);
    }

    public int requiredStars() {
        return cfg.getInt("forge.required.nether-star", 1);
    }

    public int requiredEcho() {
        return cfg.getInt("forge.required.echo-shards", 4);
    }

    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
