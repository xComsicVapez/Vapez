package com.vapez.smp.items;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemListener implements Listener {

    private final VapezPlugin plugin;
    private final Map<UUID, Long> dashCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> slamCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hasteCooldown = new ConcurrentHashMap<>();

    public ItemListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String id = plugin.items().id(item);
        if (id == null) {
            return;
        }
        if (CustomItems.HEART.equals(id) && (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.lifesteal().absorbHeart(player);
            item.setAmount(item.getAmount() - 1);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        switch (id) {
            case CustomItems.EDGE, CustomItems.SWORD -> dash(player);
            case CustomItems.AXE -> slam(player);
            case CustomItems.PICKAXE -> haste(player);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.items().isSovereign(item) && !plugin.items().is(item, CustomItems.SWORD)) {
            return;
        }
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() > 0.18) {
            return;
        }
        LightningStrike bolt = player.getWorld().strikeLightningEffect(victim.getLocation());
        victim.damage(4.0, player);
        victim.getWorld().spawnParticle(Particle.SONIC_BOOM, victim.getLocation().add(0, 1, 0), 1);
        victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4,
                new Particle.DustOptions(Color.fromRGB(123, 255, 233), 1.3f));
        if (bolt != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.8f);
        }
    }

    private void dash(Player player) {
        if (cooling(player, dashCooldown, 12_000, "Void Rend")) {
            return;
        }
        Vector dir = player.getLocation().getDirection().normalize().multiply(2.4);
        dir.setY(Math.max(0.15, dir.getY() * 0.35 + 0.15));
        player.setVelocity(dir);
        player.setFallDistance(0f);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation(), 1);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 40, 0.4, 0.2, 0.4, 0.08);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.6f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, true, false, false));
    }

    private void slam(Player player) {
        if (cooling(player, slamCooldown, 16_000, "Void Shockwave")) {
            return;
        }
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 3, 0.2, 0.1, 0.2);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 50, 1.5, 0.3, 1.5, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
        player.getNearbyEntities(5, 2, 5).forEach(entity -> {
            if (entity instanceof LivingEntity living && entity != player) {
                Vector knock = living.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                knock.setY(0.45);
                living.setVelocity(knock.multiply(1.4));
                living.damage(6.0, player);
            }
        });
    }

    private void haste(Player player) {
        if (cooling(player, hasteCooldown, 20_000, "Aether Pulse")) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 12, 2, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 12, 0, true, false, false));
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.7f);
    }

    private boolean cooling(Player player, Map<UUID, Long> map, long millis, String ability) {
        long now = System.currentTimeMillis();
        long ready = map.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            long left = (ready - now + 999) / 1000;
            player.sendActionBar(Component.text(ability + " recharging — " + left + "s", NamedTextColor.RED));
            return true;
        }
        map.put(player.getUniqueId(), now + millis);
        player.sendActionBar(Text.mm("<aqua>" + ability + "</aqua> <gray>unleashed</gray>"));
        return false;
    }
}
