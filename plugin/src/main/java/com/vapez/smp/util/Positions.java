package com.vapez.smp.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class Positions {

    private Positions() {
    }

    public static boolean inside(int x, int z, int cx, int cz, int radius) {
        return Math.abs(x - cx) <= radius && Math.abs(z - cz) <= radius;
    }

    public static boolean inside(Location loc, int cx, int cz, int radius) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return inside(loc.getBlockX(), loc.getBlockZ(), cx, cz, radius);
    }

    public static boolean inside(Block block, int cx, int cz, int radius) {
        return Math.abs(block.getX() - cx) <= radius && Math.abs(block.getZ() - cz) <= radius;
    }

    public static Location center(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
