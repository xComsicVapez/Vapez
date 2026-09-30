package com.vapez.smp.border;

import com.vapez.smp.VapezPlugin;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public final class BorderManager {

    private final VapezPlugin plugin;
    private boolean applied;

    public BorderManager(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyInitial(World world) {
        if (applied) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        if (Math.abs(border.getSize() - plugin.settings().initialBorder()) > 1.0
                && border.getSize() > plugin.settings().initialBorder()) {
            // Operators already expanded — do not shrink a live season.
            plugin.getLogger().info("World border already expanded to " + border.getSize() + " — leaving it.");
        } else if (border.getSize() < 1 || Math.abs(border.getSize() - 59_999_968) < 1
                || border.getSize() > plugin.settings().maxBorder()) {
            set(world, plugin.settings().initialBorder());
        } else if (border.getSize() == world.getWorldBorder().getMaxSize()
                || border.getSize() >= 60_000_000) {
            set(world, plugin.settings().initialBorder());
        } else if (!applied && border.getSize() > 50_000_000) {
            set(world, plugin.settings().initialBorder());
        }
        // Fresh worlds typically have size 59999968. Always clamp those.
        if (border.getSize() >= 30_000_000) {
            set(world, plugin.settings().initialBorder());
        }
        applied = true;
        plugin.getLogger().info("World border diameter: " + world.getWorldBorder().getSize());
    }

    public void set(World world, double diameter) {
        double clamped = Math.max(16, Math.min(plugin.settings().maxBorder(), diameter));
        WorldBorder border = world.getWorldBorder();
        border.setCenter(plugin.getConfig().getDouble("border.center-x", 0.0),
                plugin.getConfig().getDouble("border.center-z", 0.0));
        border.setSize(clamped);
        border.setDamageBuffer(plugin.getConfig().getDouble("border.damage-buffer", 5.0));
        border.setDamageAmount(plugin.getConfig().getDouble("border.damage-amount", 0.2));
        border.setWarningDistance(plugin.getConfig().getInt("border.warning-distance", 8));
        plugin.getConfig().set("border.live-diameter", clamped);
        plugin.saveConfig();
    }

    public void expand(World world, double extra) {
        set(world, world.getWorldBorder().getSize() + extra);
    }

    public boolean toggleFarlands() {
        boolean next = !plugin.settings().farlandsEnabled();
        plugin.settings().farlandsEnabled(next);
        return next;
    }

    public double current(World world) {
        return world.getWorldBorder().getSize();
    }
}
