package com.vapez.smp.shop;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ShopListener implements Listener {

    private final VapezPlugin plugin;

    public ShopListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }
        if ("root".equals(holder.kind())) {
            List<String> cats = new ArrayList<>(ShopCatalog.CATEGORIES.keySet());
            int[] slots = {10, 12, 14, 16, 18, 20};
            for (int i = 0; i < cats.size(); i++) {
                if (event.getSlot() == slots[i]) {
                    player.openInventory(ShopCatalog.openCategory(plugin, player, cats.get(i)));
                    return;
                }
            }
            return;
        }
        if (event.getSlot() == 49) {
            player.openInventory(ShopCatalog.openRoot(plugin, player));
            return;
        }
        ShopOffer offer = ShopCatalog.offerAt(holder.category(), event.getSlot());
        if (offer == null) {
            return;
        }
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
            buy(player, offer);
        } else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            sell(player, offer, event.getClick().isShiftClick());
        }
    }

    private void buy(Player player, ShopOffer offer) {
        if (!plugin.vault().available()) {
            player.sendMessage(Component.text("Economy is offline (install Vault + EssentialsX).", NamedTextColor.RED));
            return;
        }
        if (!plugin.vault().has(player, offer.buy())) {
            player.sendMessage(plugin.settings().prefix().append(Component.text("Not enough money.", NamedTextColor.RED)));
            return;
        }
        if (!plugin.vault().withdraw(player, offer.buy())) {
            return;
        }
        player.getInventory().addItem(new ItemStack(offer.material(), offer.amount()))
                .values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<green>Bought</green> <white>" + offer.name() + " x" + offer.amount()
                        + "</white> for <yellow>" + plugin.vault().format(offer.buy()) + "</yellow>")));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    private void sell(Player player, ShopOffer offer, boolean stack) {
        if (!plugin.vault().available()) {
            return;
        }
        int have = count(player, offer.material());
        int selling = stack ? Math.min(have, 64) : Math.min(have, offer.amount());
        if (selling <= 0) {
            player.sendMessage(Component.text("You don't have any " + offer.name() + ".", NamedTextColor.RED));
            return;
        }
        int units = Math.max(1, (int) Math.floor(selling / (double) offer.amount()));
        if (!stack) {
            units = 1;
            selling = offer.amount();
            if (count(player, offer.material()) < selling) {
                player.sendMessage(Component.text("You need " + offer.amount() + " to sell this listing.", NamedTextColor.RED));
                return;
            }
        } else {
            selling = units * offer.amount();
        }
        remove(player, offer.material(), selling);
        double gross = units * offer.sell();
        double net = gross * (1.0 - plugin.settings().sellTax());
        plugin.vault().deposit(player, net);
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<red>Sold</red> <white>" + offer.name() + " x" + selling
                        + "</white> for <yellow>" + plugin.vault().format(net) + "</yellow>")));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 0.7f, 1.1f);
    }

    private int count(Player player, org.bukkit.Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void remove(Player player, org.bukkit.Material material, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
        }
    }
}
