package com.vapez.smp.kit;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.config.Settings;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.util.Locale;
import java.util.UUID;

public final class FirstJoinListener implements Listener {

    private static final DyeColor[] COLORS = DyeColor.values();
    private final VapezPlugin plugin;

    public FirstJoinListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.stats().ensure(player);
        plugin.lifesteal().applyStored(player);
        if (!plugin.settings().kitEnabled()) {
            return;
        }
        if (player.getPersistentDataContainer().has(plugin.items().keys().uniqueKit, PersistentDataType.BYTE)) {
            return;
        }
        if (player.hasPlayedBefore() && !player.hasPermission("vapez.kit.bypass")) {
            // Still stamp PDC so reinstalls do not re-issue kits to veterans.
            player.getPersistentDataContainer().set(plugin.items().keys().uniqueKit, PersistentDataType.BYTE, (byte) 1);
            return;
        }
        ItemStack box = uniqueShulker(player.getUniqueId(), player.getName());
        player.getInventory().addItem(box);
        plugin.vault().deposit(player, plugin.settings().starterMoney());
        player.getPersistentDataContainer().set(plugin.items().keys().uniqueKit, PersistentDataType.BYTE, (byte) 1);
        DyeColor color = colorFor(player.getUniqueId());
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gray>Welcome to <white>" + plugin.settings().serverName()
                        + "</white>. Your uniquely colored <bold>" + pretty(color)
                        + " Shulker Box</bold> holds a tailored starter kit. Spend it well.</gray>")));
        player.teleportAsync(plugin.settings().spawnLocation(player.getWorld()));
    }

    public ItemStack uniqueShulker(UUID uuid, String name) {
        DyeColor color = colorFor(uuid);
        Material material = Material.valueOf(color.name() + "_SHULKER_BOX");
        ItemStack box = new ItemStack(material);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        meta.displayName(Text.mm("<gradient:#7BFFE9:#C084FC><bold>" + name + "'s Starter Cache</bold></gradient>"));
        meta.lore(java.util.List.of(
                Text.mm("<gray>Issued once. Color fingerprint: <white>" + pretty(color) + "</white></gray>"),
                Text.mm("<dark_gray>" + uuid + "</dark_gray>")
        ));
        meta.getPersistentDataContainer().set(plugin.items().keys().starterShulker, PersistentDataType.STRING, uuid.toString());
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        int slot = 0;
        for (String entry : plugin.settings().kitContents()) {
            ItemStack item = parse(entry, uuid);
            if (item != null && slot < 27) {
                state.getInventory().setItem(slot++, item);
            }
        }
        state.getInventory().setItem(Math.min(slot, 26), plugin.items().crateKey(1));
        meta.setBlockState(state);
        box.setItemMeta(meta);
        return box;
    }

    private ItemStack parse(String entry, UUID uuid) {
        String[] parts = entry.split(":");
        Material material = Settings.material(parts[0], null);
        if (material == null) {
            return null;
        }
        int amount = parts.length > 1 ? parseInt(parts[1]) : 1;
        ItemStack stack = new ItemStack(material, amount);
        if (material.name().startsWith("LEATHER_")) {
            LeatherArmorMeta leather = (LeatherArmorMeta) stack.getItemMeta();
            java.awt.Color awt = colorAwt(colorFor(uuid));
            leather.setColor(org.bukkit.Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue()));
            stack.setItemMeta(leather);
        }
        return stack;
    }

    private static DyeColor colorFor(UUID uuid) {
        int idx = Math.floorMod(uuid.hashCode(), COLORS.length);
        return COLORS[idx];
    }

    private static String pretty(DyeColor color) {
        String raw = color.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static int parseInt(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static Color colorAwt(DyeColor color) {
        org.bukkit.Color c = color.getColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue());
    }
}
