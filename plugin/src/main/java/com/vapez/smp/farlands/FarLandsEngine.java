package com.vapez.smp.farlands;

import com.vapez.smp.VapezPlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Overlay generator that recreates the classic 32-bit noise overflow "Far Lands"
 * wall/shred once chunks generate past the historical 12,550,821 threshold.
 * Vanilla 1.18+ noise is 64-bit and will not overflow on its own; this populator
 * is the restoration engine, gated by /worldborder farlands.
 */
public final class FarLandsEngine extends BlockPopulator {

    private final VapezPlugin plugin;

    public FarLandsEngine(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random,
                         int chunkX, int chunkZ, @NotNull LimitedRegion region) {
        if (!plugin.settings().farlandsEnabled() || !plugin.settings().farlandsOverlay()) {
            return;
        }
        if (worldInfo.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        int threshold = plugin.settings().farlandsThreshold();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean farX = Math.abs(baseX) >= threshold;
        boolean farZ = Math.abs(baseZ) >= threshold;
        if (!farX && !farZ) {
            return;
        }
        int minY = worldInfo.getMinHeight();
        int maxY = Math.min(worldInfo.getMaxHeight() - 1, 320);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                if (!region.isInRegion(x, 64, z)) {
                    continue;
                }
                double overflow = overflowNoise(farX ? x : z, farZ && farX ? z : (farX ? z : x));
                int columnHeight = (int) Math.abs(overflow * 180.0);
                columnHeight = Math.min(maxY - minY - 8, Math.max(12, columnHeight));
                boolean solidStrip = ((int) overflowNoise(x * 0.25, z * 0.25) & 1) == 0;
                if (!solidStrip && random.nextInt(3) != 0) {
                    // shredded air corridors — classic "looping" far-lands look
                    continue;
                }
                for (int y = minY + 1; y < minY + 1 + columnHeight; y++) {
                    if (!region.isInRegion(x, y, z)) {
                        continue;
                    }
                    Material current = region.getType(x, y, z);
                    if (current == Material.AIR || current == Material.CAVE_AIR || current == Material.WATER
                            || current == Material.LAVA || current.isAir()) {
                        Material fill = pick(y, minY, random);
                        region.setType(x, y, z, fill);
                    }
                }
                if (random.nextInt(40) == 0) {
                    int y = minY + 1 + random.nextInt(Math.max(1, columnHeight - 4));
                    if (region.isInRegion(x, y, z)) {
                        region.setType(x, y, z, Material.CRYING_OBSIDIAN);
                    }
                }
            }
        }
    }

    /**
     * Simulates the Beta 1.7 noise overflow: multiply by 2^24, store in 32-bit int, divide back.
     */
    private double overflowNoise(double a, double b) {
        int ia = overflow24(a);
        int ib = overflow24(b);
        double mixed = (ia * 1.0 / 16_777_216.0) + (ib * 0.5 / 16_777_216.0);
        return Math.sin(mixed * Math.PI) + Math.cos(ia * 0.000001) * 0.35;
    }

    private int overflow24(double value) {
        long scaled = (long) (value * 16_777_216.0);
        return (int) scaled; // intentional 32-bit overflow
    }

    private Material pick(int y, int minY, Random random) {
        int depth = y - minY;
        if (depth < 8) {
            return Material.BEDROCK;
        }
        if (depth < 20) {
            return random.nextBoolean() ? Material.DEEPSLATE : Material.TUFF;
        }
        if (depth < 50) {
            return random.nextInt(8) == 0 ? Material.GRANITE : Material.STONE;
        }
        if (depth < 90) {
            return random.nextInt(12) == 0 ? Material.ANDESITE : Material.STONE;
        }
        return random.nextInt(6) == 0 ? Material.COBBLESTONE : Material.STONE;
    }
}
