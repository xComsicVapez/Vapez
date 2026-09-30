package com.vapez.smp.spawn;

import com.vapez.smp.VapezPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Original non-copyrighted megastructure: the Aether Citadel.
 * Concentric rings, four market wings, crate pavilion, forge sanctum, leaderboard terrace.
 */
public final class CitadelBuilder {

    private static final int Y = 64;
    private final VapezPlugin plugin;
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private BukkitTask pumpTask;

    public CitadelBuilder(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void buildIfNeeded(World world) {
        if (!plugin.settings().buildCitadel()) {
            return;
        }
        Byte flag = world.getPersistentDataContainer().get(plugin.items().keys().citadelBuilt, PersistentDataType.BYTE);
        if (flag != null && flag == (byte) 1) {
            world.setSpawnLocation(plugin.settings().spawnLocation(world));
            return;
        }
        forceRebuild(world);
    }

    public void forceRebuild(World world) {
        queue.clear();
        plugin.getLogger().info("Queueing Aether Citadel generation (original build, not a third-party schematic).");
        flatten(world);
        plaza(world);
        rings(world);
        wallsAndGates(world);
        towers(world);
        marketWings(world);
        forgeSanctum(world);
        cratePavilion(world);
        leaderboardTerrace(world);
        lighting(world);
        water(world);
        queue.add(() -> {
            world.setSpawnLocation(plugin.settings().spawnLocation(world));
            world.getPersistentDataContainer().set(plugin.items().keys().citadelBuilt, PersistentDataType.BYTE, (byte) 1);
            plugin.getLogger().info("Aether Citadel complete.");
        });
        pump();
    }

    private void flatten(World world) {
        for (int cx = -8; cx <= 8; cx++) {
            for (int cz = -8; cz <= 8; cz++) {
                int chunkX = cx;
                int chunkZ = cz;
                queue.add(() -> {
                    int bx = chunkX << 4;
                    int bz = chunkZ << 4;
                    for (int x = bx; x < bx + 16; x++) {
                        for (int z = bz; z < bz + 16; z++) {
                            int top = Math.max(Y - 8, world.getMinHeight() + 1);
                            for (int y = Y - 8; y <= Y + 40; y++) {
                                Block block = world.getBlockAt(x, y, z);
                                if (y < Y) {
                                    block.setType(y == Y - 1 ? Material.DEEPSLATE : Material.DEEPSLATE, false);
                                } else {
                                    block.setType(Material.AIR, false);
                                }
                            }
                            // keep a solid foundation
                            world.getBlockAt(x, Y - 1, z).setType(Material.POLISHED_DEEPSLATE, false);
                            world.getBlockAt(x, Y - 2, z).setType(Material.DEEPSLATE, false);
                            world.getBlockAt(x, Y - 3, z).setType(Material.BEDROCK, false);
                        }
                    }
                });
            }
        }
    }

    private void plaza(World world) {
        queue.add(() -> {
            fill(world, -6, Y, -6, 6, Y, 6, Material.AMETHYST_BLOCK);
            fill(world, -4, Y, -4, 4, Y, 4, Material.PURPUR_BLOCK);
            fill(world, -2, Y, -2, 2, Y, 2, Material.CRYING_OBSIDIAN);
            world.getBlockAt(0, Y, 0).setType(Material.LODESTONE, false);
            // beacon pyramid
            fill(world, -2, Y - 1, -2, 2, Y - 1, 2, Material.IRON_BLOCK);
            fill(world, -1, Y - 1, -1, 1, Y - 1, 1, Material.NETHERITE_BLOCK);
            world.getBlockAt(0, Y + 1, 0).setType(Material.BEACON, false);
            world.getBlockAt(0, Y + 2, 0).setType(Material.LIGHT, false);
        });
    }

    private void rings(World world) {
        queue.add(() -> ring(world, 12, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES));
        queue.add(() -> ring(world, 20, Material.DEEPSLATE_TILES, Material.AMETHYST_BLOCK));
        queue.add(() -> ring(world, 28, Material.POLISHED_BLACKSTONE, Material.GILDED_BLACKSTONE));
        queue.add(() -> ring(world, 36, Material.SMOOTH_QUARTZ, Material.QUARTZ_PILLAR));
        queue.add(() -> ring(world, 48, Material.OXIDIZED_COPPER, Material.WAXED_COPPER_BULB));
        queue.add(() -> {
            // radial paths
            fill(world, -1, Y, -48, 1, Y, 48, Material.POLISHED_DEEPSLATE);
            fill(world, -48, Y, -1, 48, Y, 1, Material.POLISHED_DEEPSLATE);
            fill(world, -1, Y, -48, 1, Y, 48, Material.POLISHED_ANDESITE);
            for (int i = -48; i <= 48; i++) {
                if (Math.abs(i) % 4 == 0) {
                    world.getBlockAt(i, Y, 2).setType(Material.SOUL_LANTERN, false);
                    world.getBlockAt(i, Y, -2).setType(Material.SOUL_LANTERN, false);
                    world.getBlockAt(2, Y, i).setType(Material.SOUL_LANTERN, false);
                    world.getBlockAt(-2, Y, i).setType(Material.SOUL_LANTERN, false);
                }
            }
        });
    }

    private void ring(World world, int radius, Material floor, Material accent) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                int r2 = radius * radius;
                int inner = (radius - 3) * (radius - 3);
                if (d2 <= r2 && d2 >= inner) {
                    Material mat = ((x + z) & 1) == 0 ? floor : accent;
                    world.getBlockAt(x, Y, z).setType(mat, false);
                }
            }
        }
    }

    private void wallsAndGates(World world) {
        queue.add(() -> {
            int r = 56;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                    if (!edge) {
                        continue;
                    }
                    boolean gate = (Math.abs(x) <= 4 && Math.abs(z) == r) || (Math.abs(z) <= 4 && Math.abs(x) == r);
                    if (gate) {
                        for (int y = Y; y <= Y + 5; y++) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                        world.getBlockAt(x, Y, z).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                        continue;
                    }
                    for (int y = Y; y <= Y + 8; y++) {
                        Material mat = (y == Y + 8) ? Material.DEEPSLATE_BRICK_WALL
                                : ((x + z + y) % 5 == 0 ? Material.CRYING_OBSIDIAN : Material.DEEPSLATE_BRICKS);
                        world.getBlockAt(x, y, z).setType(mat, false);
                    }
                    if ((x + z) % 6 == 0) {
                        world.getBlockAt(x, Y + 6, z).setType(Material.SOUL_LANTERN, false);
                    }
                }
            }
            // gate arches
            arch(world, 0, r, BlockFace.SOUTH);
            arch(world, 0, -r, BlockFace.NORTH);
            arch(world, r, 0, BlockFace.EAST);
            arch(world, -r, 0, BlockFace.WEST);
        });
    }

    private void arch(World world, int x, int z, BlockFace facing) {
        for (int i = -5; i <= 5; i++) {
            int gx = facing == BlockFace.EAST || facing == BlockFace.WEST ? x : x + i;
            int gz = facing == BlockFace.NORTH || facing == BlockFace.SOUTH ? z : z + i;
            world.getBlockAt(gx, Y + 6, gz).setType(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, false);
            Block b = world.getBlockAt(gx, Y + 6, gz);
            if (b.getBlockData() instanceof Stairs stairs) {
                stairs.setFacing(facing);
                b.setBlockData(stairs, false);
            }
        }
        fill(world, x - (facing.getModX() == 0 ? 5 : 0), Y + 7, z - (facing.getModZ() == 0 ? 5 : 0),
                x + (facing.getModX() == 0 ? 5 : 0), Y + 8, z + (facing.getModZ() == 0 ? 5 : 0),
                Material.POLISHED_BLACKSTONE);
    }

    private void towers(World world) {
        int[][] corners = {{52, 52}, {52, -52}, {-52, 52}, {-52, -52}};
        for (int[] c : corners) {
            int tx = c[0];
            int tz = c[1];
            queue.add(() -> {
                fill(world, tx - 4, Y, tz - 4, tx + 4, Y + 18, tz + 4, Material.DEEPSLATE_BRICKS);
                fill(world, tx - 3, Y + 1, tz - 3, tx + 3, Y + 17, tz + 3, Material.AIR);
                fill(world, tx - 4, Y + 18, tz - 4, tx + 4, Y + 18, tz + 4, Material.POLISHED_BLACKSTONE);
                world.getBlockAt(tx, Y + 19, tz).setType(Material.LIGHTNING_ROD, false);
                world.getBlockAt(tx, Y + 16, tz).setType(Material.LANTERN, false);
                for (int y = Y + 2; y < Y + 16; y += 4) {
                    world.getBlockAt(tx + 4, y, tz).setType(Material.AMETHYST_CLUSTER, false);
                    world.getBlockAt(tx - 4, y, tz).setType(Material.AMETHYST_CLUSTER, false);
                    world.getBlockAt(tx, y, tz + 4).setType(Material.AMETHYST_CLUSTER, false);
                    world.getBlockAt(tx, y, tz - 4).setType(Material.AMETHYST_CLUSTER, false);
                }
            });
        }
    }

    private void marketWings(World world) {
        // North: shop street, East: auction, South: warps, West: info
        queue.add(() -> stallRow(world, 0, 32, 0, 1));
        queue.add(() -> stallRow(world, 32, 0, 1, 0));
        queue.add(() -> stallRow(world, 0, -32, 0, -1));
        queue.add(() -> stallRow(world, -32, 0, -1, 0));
        queue.add(() -> {
            signBoard(world, 0, 38, "MARKET", Material.RAW_GOLD_BLOCK);
            signBoard(world, 38, 0, "AUCTIONS", Material.RAW_COPPER_BLOCK);
            signBoard(world, 0, -38, "WARPS", Material.RAW_IRON_BLOCK);
            signBoard(world, -38, 0, "FORGE", Material.CRYING_OBSIDIAN);
        });
    }

    private void stallRow(World world, int cx, int cz, int dx, int dz) {
        for (int i = -8; i <= 8; i += 4) {
            int x = cx + (dx == 0 ? i : 0);
            int z = cz + (dz == 0 ? i : 0);
            fill(world, x - 1, Y, z - 1, x + 1, Y + 3, z + 1, Material.STRIPPED_DARK_OAK_LOG);
            fill(world, x, Y + 1, z, x, Y + 2, z, Material.AIR);
            world.getBlockAt(x, Y, z).setType(Material.BARREL, false);
            world.getBlockAt(x, Y + 3, z).setType(Material.WAXED_COPPER_GRATE, false);
        }
    }

    private void signBoard(World world, int x, int z, String ignored, Material material) {
        fill(world, x - 2, Y + 4, z - 2, x + 2, Y + 7, z + 2, material);
        world.getBlockAt(x, Y + 8, z).setType(Material.END_ROD, false);
    }

    private void forgeSanctum(World world) {
        queue.add(() -> {
            int x = -38;
            int z = 0;
            fill(world, x - 5, Y, z - 5, x + 5, Y, z + 5, Material.OBSIDIAN);
            fill(world, x - 3, Y + 1, z - 3, x + 3, Y + 5, z + 3, Material.AIR);
            fill(world, x - 4, Y + 1, z - 4, x + 4, Y + 6, z + 4, Material.CRYING_OBSIDIAN);
            fill(world, x - 3, Y + 1, z - 3, x + 3, Y + 5, z + 3, Material.AIR);
            world.getBlockAt(x, Y + 1, z).setType(Material.RESPAWN_ANCHOR, false);
            world.getBlockAt(x, Y + 2, z).setType(Material.END_ROD, false);
            world.getBlockAt(x + 2, Y + 1, z).setType(Material.ANVIL, false);
            world.getBlockAt(x - 2, Y + 1, z).setType(Material.ENCHANTING_TABLE, false);
            world.getBlockAt(x, Y + 1, z + 2).setType(Material.SOUL_CAMPFIRE, false);
        });
    }

    private void cratePavilion(World world) {
        queue.add(() -> {
            int x = 0;
            int z = 44;
            fill(world, x - 6, Y, z - 6, x + 6, Y, z + 6, Material.DARK_PRISMARINE);
            fill(world, x - 5, Y + 1, z - 5, x + 5, Y + 4, z + 5, Material.AIR);
            for (int i = -4; i <= 4; i += 4) {
                for (int j = -4; j <= 4; j += 4) {
                    world.getBlockAt(x + i, Y + 1, z + j).setType(Material.CHEST, false);
                    world.getBlockAt(x + i, Y + 5, z + j).setType(Material.CHAIN, false);
                }
            }
            fill(world, x - 6, Y + 5, z - 6, x + 6, Y + 5, z + 6, Material.PRISMARINE_BRICKS);
        });
    }

    private void leaderboardTerrace(World world) {
        queue.add(() -> {
            int x = 0;
            int z = -44;
            fill(world, x - 8, Y, z - 6, x + 8, Y, z + 6, Material.SMOOTH_QUARTZ);
            fill(world, x - 7, Y + 1, z - 5, x + 7, Y + 1, z + 5, Material.AIR);
            world.getBlockAt(x - 5, Y + 1, z).setType(Material.QUARTZ_PILLAR, false);
            world.getBlockAt(x, Y + 1, z).setType(Material.QUARTZ_PILLAR, false);
            world.getBlockAt(x + 5, Y + 1, z).setType(Material.QUARTZ_PILLAR, false);
            world.getBlockAt(x - 5, Y + 4, z).setType(Material.SEA_LANTERN, false);
            world.getBlockAt(x, Y + 4, z).setType(Material.SEA_LANTERN, false);
            world.getBlockAt(x + 5, Y + 4, z).setType(Material.SEA_LANTERN, false);
        });
    }

    private void lighting(World world) {
        queue.add(() -> {
            for (int x = -50; x <= 50; x += 5) {
                for (int z = -50; z <= 50; z += 5) {
                    if ((Math.abs(x) > 8 || Math.abs(z) > 8) && world.getBlockAt(x, Y, z).getType() != Material.AIR) {
                        Block above = world.getBlockAt(x, Y + 1, z);
                        if (above.getType().isAir() && ((x * 31 + z) & 7) == 0) {
                            above.setType(Material.SOUL_TORCH, false);
                        }
                    }
                }
            }
        });
    }

    private void water(World world) {
        queue.add(() -> {
            // moat just inside the wall
            for (int x = -54; x <= 54; x++) {
                for (int z = -54; z <= 54; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (d == 54) {
                        world.getBlockAt(x, Y, z).setType(Material.WATER, false);
                        world.getBlockAt(x, Y - 1, z).setType(Material.DARK_PRISMARINE, false);
                    }
                }
            }
        });
    }

    private void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private void pump() {
        if (pumpTask != null && !pumpTask.isCancelled()) {
            return;
        }
        pumpTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int budget = 2;
            while (budget-- > 0 && !queue.isEmpty()) {
                queue.removeFirst().run();
            }
            if (queue.isEmpty() && pumpTask != null) {
                pumpTask.cancel();
                pumpTask = null;
            }
        }, 1L, 1L);
    }
}
