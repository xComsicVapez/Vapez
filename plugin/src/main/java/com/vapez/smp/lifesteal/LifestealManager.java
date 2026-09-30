package com.vapez.smp.lifesteal;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

public final class LifestealManager implements Listener {

    private final VapezPlugin plugin;

    public LifestealManager(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public int hearts(Player player) {
        Integer stored = player.getPersistentDataContainer().get(plugin.items().keys().hearts, PersistentDataType.INTEGER);
        if (stored == null) {
            return 10;
        }
        return stored;
    }

    public void setHearts(Player player, int hearts) {
        int clamped = Math.max(plugin.settings().minHearts(), Math.min(plugin.settings().maxHearts(), hearts));
        player.getPersistentDataContainer().set(plugin.items().keys().hearts, PersistentDataType.INTEGER, clamped);
        apply(player, clamped);
    }

    public void applyStored(Player player) {
        if (!plugin.settings().lifesteal()) {
            return;
        }
        if (!player.getPersistentDataContainer().has(plugin.items().keys().hearts, PersistentDataType.INTEGER)) {
            setHearts(player, 10);
            return;
        }
        apply(player, hearts(player));
    }

    public void absorbHeart(Player player) {
        setHearts(player, hearts(player) + 1);
        player.sendMessage(plugin.settings().prefix().append(Text.mm("<red>You absorbed a stolen heart.</red>")));
    }

    public boolean withdraw(Player player) {
        if (!plugin.getConfig().getBoolean("lifesteal.withdraw-enabled", true)) {
            return false;
        }
        int current = hearts(player);
        if (current <= plugin.settings().minHearts()) {
            return false;
        }
        setHearts(player, current - 1);
        player.getInventory().addItem(plugin.items().heart(1))
                .values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.settings().lifesteal()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        plugin.stats().addDeath(victim.getUniqueId());
        plugin.levels().addXp(victim, -plugin.getConfig().getInt("levels.death-xp-penalty", 10), "death");
        if (killer == null || killer.equals(victim)) {
            return;
        }
        plugin.stats().addKill(killer.getUniqueId());
        plugin.levels().addXp(killer, plugin.settings().killXp(), "kill");
        int stolen = plugin.settings().heartsPerKill();
        int victimHearts = hearts(victim);
        if (victimHearts <= plugin.settings().minHearts()) {
            if (plugin.settings().dropHeartItem()) {
                event.getDrops().add(plugin.items().heart(stolen));
            }
            return;
        }
        setHearts(victim, victimHearts - stolen);
        int killerHearts = hearts(killer);
        if (killerHearts < plugin.settings().maxHearts()) {
            setHearts(killer, killerHearts + stolen);
            killer.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<red>You stole a heart from <white>" + victim.getName() + "</white>.</red>")));
        } else if (plugin.settings().dropHeartItem()) {
            killer.getWorld().dropItemNaturally(killer.getLocation(), plugin.items().heart(stolen));
            killer.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<gray>You are at max hearts. A Stolen Heart dropped instead.</gray>")));
        }
        double bounty = plugin.stats().of(victim.getUniqueId()).bounty;
        if (bounty > 0) {
            plugin.vault().deposit(killer, bounty);
            plugin.stats().of(victim.getUniqueId()).bounty = 0;
            killer.sendMessage(plugin.settings().prefix().append(Text.mm(
                    "<gold>Bounty claimed: " + plugin.vault().format(bounty) + "</gold>")));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyStored(event.getPlayer()));
    }

    private void apply(Player player, int hearts) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        double health = hearts * plugin.settings().healthPerHeart();
        max.setBaseValue(Math.max(2.0, health));
        if (player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
    }
}
