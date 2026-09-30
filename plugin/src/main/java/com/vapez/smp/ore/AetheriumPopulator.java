package com.vapez.smp.ore;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.config.Settings;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.Set;

public final class AetheriumPopulator extends BlockPopulator {

    private final VapezPlugin plugin;

    public AetheriumPopulator(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random,
                         int chunkX, int chunkZ, @NotNull LimitedRegion region) {
        Settings s = plugin.settings();
        if (!s.oreEnabled()) {
            return;
        }
        if (worldInfo.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        // Skip spawn citadel footprint so the megastructure is not riddled with ore.
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;
        if (Math.abs(worldX) < 160 && Math.abs(worldZ) < 160) {
            return;
        }
        if (random.nextInt(s.oreChunkDivisor()) != 0) {
            return;
        }
        int minY = Math.max(worldInfo.getMinHeight() + 1, s.oreMinY());
        int maxY = Math.min(worldInfo.getMaxHeight() - 1, s.oreMaxY());
        if (maxY < minY) {
            return;
        }
        Set<Material> replaceable = s.replaceable();
        Material ore = s.oreBlock();
        int veins = 1 + (random.nextInt(100) < 12 ? 1 : 0);
        for (int v = 0; v < veins; v++) {
            int x = worldX + random.nextInt(16);
            int z = worldZ + random.nextInt(16);
            if (!region.isInRegion(x, minY, z)) {
                continue;
            }
            int surface = region.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR_WG);
            if (surface < minY) {
                continue;
            }
            int y = minY + random.nextInt(maxY - minY + 1);
            int size = s.veinMin() + random.nextInt(s.veinMax() - s.veinMin() + 1);
            for (int i = 0; i < size; i++) {
                int bx = x + random.nextInt(3) - 1;
                int by = y + random.nextInt(3) - 1;
                int bz = z + random.nextInt(3) - 1;
                if (!region.isInRegion(bx, by, bz)) {
                    continue;
                }
                Material current = region.getType(bx, by, bz);
                if (replaceable.contains(current)) {
                    region.setType(bx, by, bz, ore);
                }
            }
        }
    }
}
