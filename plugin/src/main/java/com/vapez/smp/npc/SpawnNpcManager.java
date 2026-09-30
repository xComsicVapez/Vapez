package com.vapez.smp.npc;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.forge.ForgeListener;
import com.vapez.smp.shop.ShopCatalog;
import com.vapez.smp.util.Positions;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpawnNpcManager implements Listener {

    private final VapezPlugin plugin;
    private final List<UUID> spawned = new ArrayList<>();

    public SpawnNpcManager(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawn(World world) {
        despawn();
        spawnOne(world, 0.5, 65, 32.5, "shop", "<aqua><bold>Market Keeper</bold></aqua>", Villager.Profession.LIBRARIAN);
        spawnOne(world, 32.5, 65, 0.5, "auction", "<gold><bold>Auctioneer</bold></gold>", Villager.Profession.CARTOGRAPHER);
        spawnOne(world, 0.5, 65, -32.5, "warp", "<green><bold>Warp Scribe</bold></green>", Villager.Profession.CLERIC);
        spawnOne(world, -32.5, 65, 0.5, "forge", "<light_purple><bold>Aetherforge Smith</bold></light_purple>", Villager.Profession.WEAPONSMITH);
        spawnOne(world, 0.5, 65, 44.5, "crates", "<yellow><bold>Crate Warden</bold></yellow>", Villager.Profession.MASON);
    }

    private void spawnOne(World world, double x, double y, double z, String id, String name, Villager.Profession profession) {
        Location loc = new Location(world, x, y, z);
        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        villager.customName(Text.mm(name));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setAI(false);
        villager.setGravity(false);
        villager.setProfession(profession);
        villager.setVillagerLevel(5);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.getPersistentDataContainer().set(plugin.items().keys().npcId, PersistentDataType.STRING, id);
        spawned.add(villager.getUniqueId());
    }

    public void despawn() {
        for (UUID id : spawned) {
            var entity = plugin.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        spawned.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        String id = villager.getPersistentDataContainer().get(plugin.items().keys().npcId, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (id) {
            case "shop" -> player.openInventory(ShopCatalog.openRoot(plugin, player));
            case "auction" -> plugin.auctions().open(player);
            case "warp" -> player.sendMessage(plugin.warps().list());
            case "forge" -> new ForgeListener(plugin).open(player);
            case "crates" -> player.sendMessage(plugin.settings().prefix().append(
                    Component.text("Right-click a Citadel chest with a crate key.")));
            default -> {
            }
        }
    }
}
