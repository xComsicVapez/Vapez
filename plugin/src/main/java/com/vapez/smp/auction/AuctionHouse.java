package com.vapez.smp.auction;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Tasks;
import com.vapez.smp.util.Text;
import com.vapez.smp.util.YamlStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionHouse implements Listener, InventoryHolder {

    public static final Component TITLE = Text.mm("<gradient:#FBBF24:#F59E0B><bold>Auction House</bold></gradient>");
    private final VapezPlugin plugin;
    private final YamlStore store;
    private final Map<String, Listing> listings = new ConcurrentHashMap<>();

    public AuctionHouse(VapezPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "auctions.yml");
        load();
    }

    public void startTasks() {
        Tasks.repeat(20L * 60, 20L * 60, this::expire);
    }

    public int size() {
        return listings.size();
    }

    public long playerCount(UUID uuid) {
        return listings.values().stream().filter(l -> l.seller.equals(uuid)).count();
    }

    public boolean sell(Player player, ItemStack item, double price) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (playerCount(player.getUniqueId()) >= plugin.settings().maxListings()) {
            player.sendMessage(Text.mm("<red>You have too many listings.</red>"));
            return false;
        }
        if (plugin.settings().listingFee() > 0 && !plugin.vault().withdraw(player, plugin.settings().listingFee())) {
            player.sendMessage(Text.mm("<red>Cannot afford the listing fee.</red>"));
            return false;
        }
        Listing listing = new Listing();
        listing.id = UUID.randomUUID().toString().substring(0, 8);
        listing.seller = player.getUniqueId();
        listing.sellerName = player.getName();
        listing.item = item.clone();
        listing.price = price;
        listing.expiresAt = System.currentTimeMillis() + plugin.settings().auctionMillis();
        listings.put(listing.id, listing);
        saveSync();
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 54, TITLE);
        int slot = 0;
        for (Listing listing : listings.values()) {
            if (slot >= 45) {
                break;
            }
            ItemStack icon = listing.item.clone();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.mm("<dark_gray>────────────</dark_gray>"));
            lore.add(Text.mm("<gray>Seller:</gray> <white>" + listing.sellerName + "</white>"));
            lore.add(Text.mm("<gray>Price:</gray> <yellow>" + plugin.vault().format(listing.price) + "</yellow>"));
            lore.add(Text.mm("<gray>ID:</gray> <dark_gray>" + listing.id + "</dark_gray>"));
            lore.add(Text.mm("<green>Click to buy</green>"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }
        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Text.mm("<yellow>/ah sell <price></yellow>"));
        meta.lore(List.of(Text.mm("<gray>Hold an item and list it. Fee: "
                + plugin.vault().format(plugin.settings().listingFee()) + "</gray>")));
        info.setItemMeta(meta);
        inv.setItem(49, info);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AuctionHouse)) {
            return;
        }
        event.setCancelled(true);
        if (event.getSlot() >= 45 || event.getCurrentItem() == null) {
            return;
        }
        List<Listing> list = new ArrayList<>(listings.values());
        if (event.getSlot() >= list.size()) {
            return;
        }
        Listing listing = list.get(event.getSlot());
        buy(player, listing);
    }

    private void buy(Player player, Listing listing) {
        if (listing.seller.equals(player.getUniqueId())) {
            listings.remove(listing.id);
            player.getInventory().addItem(listing.item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(plugin.settings().prefix().append(Text.mm("<gray>Listing cancelled and item returned.</gray>")));
            saveSync();
            open(player);
            return;
        }
        if (!plugin.vault().has(player, listing.price)) {
            player.sendMessage(Text.mm("<red>Not enough money.</red>"));
            return;
        }
        plugin.vault().withdraw(player, listing.price);
        double tax = plugin.getConfig().getDouble("auction.tax-on-sale", 0.03);
        plugin.vault().deposit(listing.seller, listing.price * (1.0 - tax));
        listings.remove(listing.id);
        player.getInventory().addItem(listing.item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<green>Purchased listing</green> <dark_gray>" + listing.id + "</dark_gray>")));
        saveSync();
        open(player);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        Iterator<Listing> it = listings.values().iterator();
        while (it.hasNext()) {
            Listing listing = it.next();
            if (listing.expiresAt < now) {
                plugin.vault().deposit(listing.seller, 0); // keep seller mailbox simple: drop into storage
                store.yaml().set("expired." + listing.id + ".seller", listing.seller.toString());
                store.yaml().set("expired." + listing.id + ".item", encode(listing.item));
                it.remove();
            }
        }
        saveSync();
    }

    public void saveSync() {
        store.yaml().set("listings", null);
        for (Listing listing : listings.values()) {
            String path = "listings." + listing.id;
            store.yaml().set(path + ".seller", listing.seller.toString());
            store.yaml().set(path + ".name", listing.sellerName);
            store.yaml().set(path + ".price", listing.price);
            store.yaml().set(path + ".expires", listing.expiresAt);
            store.yaml().set(path + ".item", encode(listing.item));
        }
        store.save();
    }

    private void load() {
        var root = store.yaml().getConfigurationSection("listings");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            try {
                Listing listing = new Listing();
                listing.id = id;
                listing.seller = UUID.fromString(root.getString(id + ".seller"));
                listing.sellerName = root.getString(id + ".name", "unknown");
                listing.price = root.getDouble(id + ".price");
                listing.expiresAt = root.getLong(id + ".expires");
                listing.item = decode(root.getString(id + ".item"));
                if (listing.item != null) {
                    listings.put(id, listing);
                }
            } catch (Exception ignored) {
                // skip corrupt listing
            }
        }
    }

    private String encode(ItemStack item) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(buf)) {
            out.writeObject(item);
            return Base64.getEncoder().encodeToString(buf.toByteArray());
        } catch (IOException ex) {
            return "";
        }
    }

    private ItemStack decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try (ByteArrayInputStream buf = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(buf)) {
            return (ItemStack) in.readObject();
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54, TITLE);
    }
}
