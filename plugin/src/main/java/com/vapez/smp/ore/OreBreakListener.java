package com.vapez.smp.ore;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Positions;
import com.vapez.smp.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public final class OreBreakListener implements Listener {

    private final VapezPlugin plugin;

    public OreBreakListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != plugin.settings().oreBlock()) {
            return;
        }
        if (block.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            return;
        }
        int y = block.getY();
        if (y < plugin.settings().oreMinY() - 2 || y > plugin.settings().oreMaxY() + 2) {
            return;
        }
        if (Positions.inside(block, 0, 0, plugin.settings().spawnRadius())) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!isNetheritePick(tool) && !plugin.items().is(tool, "aetherium_pickaxe")) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Text.mm("<red>Aetherium ore requires a netherite (or better) pickaxe.</red>"));
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(plugin.getConfig().getInt("ore.drop-xp", 25));
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        int amount = 1;
        if (fortune > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < fortune * 12) {
            amount++;
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.2, 0.5), plugin.items().crystal(amount));
        plugin.levels().addXp(event.getPlayer(), plugin.settings().aetheriumXp(), "aetherium");
        plugin.stats().addMined(event.getPlayer().getUniqueId(), amount);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != plugin.settings().oreBlock()) {
            return;
        }
        // Players may place heavy cores from vanilla vaults; only generated bedrock-layer
        // cores drop aetherium. No extra handling required for placement.
    }

    private boolean isNetheritePick(ItemStack tool) {
        if (tool == null) {
            return false;
        }
        Material type = tool.getType();
        return type == Material.NETHERITE_PICKAXE || plugin.settings().requiredTool() == type;
    }
}
