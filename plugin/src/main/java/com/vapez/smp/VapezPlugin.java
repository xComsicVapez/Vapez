package com.vapez.smp;

import com.vapez.smp.auction.AuctionCommand;
import com.vapez.smp.auction.AuctionHouse;
import com.vapez.smp.border.BorderCommand;
import com.vapez.smp.border.BorderManager;
import com.vapez.smp.config.Settings;
import com.vapez.smp.crate.CrateCommand;
import com.vapez.smp.crate.CrateManager;
import com.vapez.smp.farlands.FarLandsEngine;
import com.vapez.smp.forge.AetherforgeCommand;
import com.vapez.smp.forge.ForgeListener;
import com.vapez.smp.forge.LifetimeCrafts;
import com.vapez.smp.guard.SpawnGuardListener;
import com.vapez.smp.inspect.SpawnInspector;
import com.vapez.smp.inspect.SpawnMotdListener;
import com.vapez.smp.items.AbilityTask;
import com.vapez.smp.items.CustomItems;
import com.vapez.smp.items.ItemListener;
import com.vapez.smp.kit.FirstJoinListener;
import com.vapez.smp.level.LevelCommand;
import com.vapez.smp.level.LevelManager;
import com.vapez.smp.lifesteal.LifestealCommand;
import com.vapez.smp.lifesteal.LifestealManager;
import com.vapez.smp.npc.SpawnNpcManager;
import com.vapez.smp.ore.AetheriumPopulator;
import com.vapez.smp.ore.OreBreakListener;
import com.vapez.smp.placeholders.VapezExpansion;
import com.vapez.smp.recipe.CustomRecipes;
import com.vapez.smp.shop.EconomyCommands;
import com.vapez.smp.shop.ShopCommand;
import com.vapez.smp.shop.ShopListener;
import com.vapez.smp.shop.VaultHook;
import com.vapez.smp.spawn.CitadelBuilder;
import com.vapez.smp.spawn.SpawnCommand;
import com.vapez.smp.stats.BountyCommand;
import com.vapez.smp.stats.RtpCommand;
import com.vapez.smp.stats.StatsListener;
import com.vapez.smp.stats.StatsManager;
import com.vapez.smp.warp.WarpCommand;
import com.vapez.smp.warp.WarpManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class VapezPlugin extends JavaPlugin implements Listener {

    private static VapezPlugin instance;
    private Settings settings;
    private CustomItems items;
    private LifetimeCrafts lifetimeCrafts;
    private StatsManager stats;
    private LevelManager levels;
    private VaultHook vault;
    private AuctionHouse auctions;
    private WarpManager warps;
    private LifestealManager lifesteal;
    private BorderManager border;
    private CrateManager crates;
    private CitadelBuilder citadel;
    private SpawnNpcManager npcs;
    private FarLandsEngine farLands;
    private SpawnInspector inspector;

    public static VapezPlugin get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        this.settings = new Settings(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onEnable() {
        this.items = new CustomItems(this);
        this.lifetimeCrafts = new LifetimeCrafts(this);
        this.stats = new StatsManager(this);
        this.levels = new LevelManager(this);
        this.vault = new VaultHook(this);
        this.auctions = new AuctionHouse(this);
        this.warps = new WarpManager(this);
        this.lifesteal = new LifestealManager(this);
        this.border = new BorderManager(this);
        this.crates = new CrateManager(this);
        this.citadel = new CitadelBuilder(this);
        this.npcs = new SpawnNpcManager(this);
        this.farLands = new FarLandsEngine(this);
        this.inspector = new SpawnInspector(this);

        CustomRecipes.register(this);
        registerCommands();
        registerListeners();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VapezExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered (%vapez_*%).");
        }

        stats.startTasks();
        levels.startTasks();
        auctions.startTasks();
        new AbilityTask(this).start();
        crates.startTasks();
        inspector.start();

        getLogger().info("VapezCore enabled — original SMP framework ready.");
    }

    @Override
    public void onDisable() {
        if (stats != null) {
            stats.saveSync();
        }
        if (auctions != null) {
            auctions.saveSync();
        }
        if (warps != null) {
            warps.saveSync();
        }
        if (lifetimeCrafts != null) {
            lifetimeCrafts.saveSync();
        }
        if (npcs != null) {
            npcs.despawn();
        }
        if (inspector != null) {
            inspector.shutdown();
        }
        getLogger().info("VapezCore disabled.");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        world.getPopulators().add(new AetheriumPopulator(this));
        world.getPopulators().add(farLands != null ? farLands : new FarLandsEngine(this));
        getLogger().info("Registered aetherium + Far Lands populators for " + world.getName());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(settings.overworldName())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                border.applyInitial(world);
                citadel.buildIfNeeded(world);
                npcs.spawn(world);
                crates.placeIfNeeded(world);
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Failed to finalize overworld bootstrap", ex);
            }
        }, 40L);
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("shop")).setExecutor(new ShopCommand(this));
        Objects.requireNonNull(getCommand("ah")).setExecutor(new AuctionCommand(this));
        Objects.requireNonNull(getCommand("pwarp")).setExecutor(new WarpCommand(this));
        Objects.requireNonNull(getCommand("level")).setExecutor(new LevelCommand(this));
        Objects.requireNonNull(getCommand("worldborder")).setExecutor(new BorderCommand(this));
        Objects.requireNonNull(getCommand("spawn")).setExecutor(new SpawnCommand(this));
        Objects.requireNonNull(getCommand("lifesteal")).setExecutor(new LifestealCommand(this));
        Objects.requireNonNull(getCommand("crates")).setExecutor(new CrateCommand(this));
        Objects.requireNonNull(getCommand("aetherforge")).setExecutor(new AetherforgeCommand(this));
        Objects.requireNonNull(getCommand("vapez")).setExecutor(new VapezAdminCommand(this));
        EconomyCommands economy = new EconomyCommands(this);
        Objects.requireNonNull(getCommand("balance")).setExecutor(economy);
        Objects.requireNonNull(getCommand("pay")).setExecutor(economy);
        Objects.requireNonNull(getCommand("bounty")).setExecutor(new BountyCommand(this));
        Objects.requireNonNull(getCommand("rtp")).setExecutor(new RtpCommand(this));
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new FirstJoinListener(this), this);
        pm.registerEvents(new OreBreakListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new ForgeListener(this), this);
        pm.registerEvents(new ShopListener(this), this);
        pm.registerEvents(new StatsListener(this), this);
        pm.registerEvents(lifesteal, this);
        pm.registerEvents(new SpawnGuardListener(this), this);
        pm.registerEvents(new SpawnMotdListener(this), this);
        pm.registerEvents(npcs, this);
        pm.registerEvents(crates, this);
        pm.registerEvents(warps, this);
        pm.registerEvents(auctions, this);
    }

    public Settings settings() {
        return settings;
    }

    public CustomItems items() {
        return items;
    }

    public LifetimeCrafts lifetimeCrafts() {
        return lifetimeCrafts;
    }

    public StatsManager stats() {
        return stats;
    }

    public LevelManager levels() {
        return levels;
    }

    public VaultHook vault() {
        return vault;
    }

    public AuctionHouse auctions() {
        return auctions;
    }

    public WarpManager warps() {
        return warps;
    }

    public LifestealManager lifesteal() {
        return lifesteal;
    }

    public BorderManager border() {
        return border;
    }

    public CrateManager crates() {
        return crates;
    }

    public CitadelBuilder citadel() {
        return citadel;
    }

    public SpawnInspector inspector() {
        return inspector;
    }

    public void reloadAll() {
        reloadConfig();
        settings.reload();
        getLogger().info("Configuration reloaded.");
    }
}
