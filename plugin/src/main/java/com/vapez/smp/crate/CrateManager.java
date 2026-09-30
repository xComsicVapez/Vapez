package com.vapez.smp.crate;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.items.CustomItems;
import com.vapez.smp.util.Positions;
import com.vapez.smp.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateManager implements Listener {

    private record Reward(ItemStack item, int weight) {
    }

    private final VapezPlugin plugin;
    private final List<Reward> table = List.of(
            new Reward(new ItemStack(Material.DIAMOND, 8), 18),
            new Reward(new ItemStack(Material.GOLDEN_APPLE, 8), 16),
            new Reward(new ItemStack(Material.EXPERIENCE_BOTTLE, 32), 16),
            new Reward(new ItemStack(Material.NETHERITE_SCRAP, 2), 8),
            new Reward(new ItemStack(Material.ELYTRA, 1), 2),
            new Reward(new ItemStack(Material.TOTEM_OF_UNDYING, 1), 4),
            new Reward(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 3),
            new Reward(new ItemStack(Material.BEACON, 1), 1)
    );

    public CrateManager(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World world = plugin.getServer().getWorld(plugin.settings().overworldName());
            if (world == null) {
                return;
            }
            world.spawnParticle(Particle.DUST, new org.bukkit.Location(world, 0.5, 67, 44.5),
                    12, 2.5, 0.6, 2.5, new Particle.DustOptions(Color.fromRGB(251, 191, 36), 1.0f));
        }, 40L, 20L);
    }

    public void placeIfNeeded(World world) {
        // chests are placed by the citadel builder
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.ENDER_CHEST)) {
            return;
        }
        if (!Positions.inside(block, 0, 44, 8)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!plugin.items().is(hand, CustomItems.KEY)) {
            event.setCancelled(true);
            player.sendActionBar(Text.mm("<gold>Need a Citadel Crate Key.</gold>"));
            return;
        }
        event.setCancelled(true);
        hand.setAmount(hand.getAmount() - 1);
        ItemStack reward = roll();
        player.getInventory().addItem(reward).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gold>Crate opened!</gold> <gray>You received</gray> <white>"
                        + reward.getType().name().toLowerCase().replace('_', ' ') + " x" + reward.getAmount() + "</white>")));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().add(0.5, 1, 0.5), 30, 0.3, 0.4, 0.3);
    }

    private ItemStack roll() {
        int total = table.stream().mapToInt(Reward::weight).sum();
        int pick = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (Reward reward : table) {
            cursor += reward.weight();
            if (pick < cursor) {
                ItemStack item = reward.item().clone();
                if (item.getType() == Material.DIAMOND && ThreadLocalRandom.current().nextInt(40) == 0) {
                    return plugin.items().crystal(1);
                }
                return item;
            }
        }
        return table.getFirst().item().clone();
    }
}
