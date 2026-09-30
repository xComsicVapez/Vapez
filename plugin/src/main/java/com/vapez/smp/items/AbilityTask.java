package com.vapez.smp.items;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class AbilityTask {

    private final VapezPlugin plugin;

    public AbilityTask(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Tasks.repeat(40L, plugin.settings().particleInterval(), this::tick);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean gear = false;
            boolean fullSet = true;
            ItemStack[] armor = player.getInventory().getArmorContents();
            String[] expected = {CustomItems.BOOTS, CustomItems.LEGS, CustomItems.CHEST, CustomItems.HELMET};
            for (int i = 0; i < 4; i++) {
                if (!plugin.items().is(armor[i], expected[i])) {
                    fullSet = false;
                } else {
                    gear = true;
                }
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (plugin.items().isAetheriumGear(hand) || plugin.items().isSovereign(hand)) {
                gear = true;
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                        2, 0.15, 0.4, 0.15, 0.0);
            }
            if (gear) {
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.15, 0),
                        6, 0.25, 0.05, 0.25, new Particle.DustOptions(Color.fromRGB(192, 132, 252), 0.9f));
            }
            if (fullSet) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, false));
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0),
                        4, 0.3, 0.5, 0.3, 0.0);
            }
        }
    }
}
