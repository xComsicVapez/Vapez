package com.vapez.smp.shop;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopCatalog {

    public static final Component TITLE = Text.mm("<gradient:#7BFFE9:#C084FC><bold>Citadel Market</bold></gradient>");
    public static final Map<String, List<ShopOffer>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Blocks", List.of(
                offer("cobble", "Cobblestone", Material.COBBLESTONE, 4, 1, 16),
                offer("stone", "Stone", Material.STONE, 8, 2, 16),
                offer("deepslate", "Deepslate", Material.DEEPSLATE, 10, 2, 16),
                offer("oak", "Oak Log", Material.OAK_LOG, 12, 4, 16),
                offer("obsidian", "Obsidian", Material.OBSIDIAN, 80, 20, 1),
                offer("endstone", "End Stone", Material.END_STONE, 18, 4, 16),
                offer("glass", "Glass", Material.GLASS, 10, 2, 16),
                offer("quartz", "Quartz Block", Material.QUARTZ_BLOCK, 40, 12, 8)
        ));
        CATEGORIES.put("Food", List.of(
                offer("steak", "Steak", Material.COOKED_BEEF, 8, 2, 8),
                offer("gapple", "Golden Apple", Material.GOLDEN_APPLE, 250, 80, 1),
                offer("egapple", "Enchanted Golden Apple", Material.ENCHANTED_GOLDEN_APPLE, 8000, 2000, 1),
                offer("bread", "Bread", Material.BREAD, 6, 1, 16),
                offer("carrot", "Golden Carrot", Material.GOLDEN_CARROT, 18, 4, 8)
        ));
        CATEGORIES.put("Combat", List.of(
                offer("pearl", "Ender Pearl", Material.ENDER_PEARL, 45, 10, 4),
                offer("totem", "Totem of Undying", Material.TOTEM_OF_UNDYING, 6500, 1800, 1),
                offer("xp", "XP Bottle", Material.EXPERIENCE_BOTTLE, 35, 8, 8),
                offer("arrow", "Arrow", Material.ARROW, 8, 1, 16),
                offer("tnt", "TNT", Material.TNT, 120, 30, 4),
                offer("crystal", "End Crystal", Material.END_CRYSTAL, 900, 220, 1),
                offer("obs", "Obsidian", Material.OBSIDIAN, 80, 20, 8)
        ));
        CATEGORIES.put("Ores", List.of(
                offer("iron", "Iron Ingot", Material.IRON_INGOT, 24, 8, 8),
                offer("gold", "Gold Ingot", Material.GOLD_INGOT, 28, 9, 8),
                offer("diamond", "Diamond", Material.DIAMOND, 180, 70, 1),
                offer("emerald", "Emerald", Material.EMERALD, 160, 60, 1),
                offer("netherite", "Netherite Ingot", Material.NETHERITE_INGOT, 3500, 1200, 1),
                offer("debris", "Ancient Debris", Material.ANCIENT_DEBRIS, 2200, 700, 1),
                offer("star", "Nether Star", Material.NETHER_STAR, 12000, 4000, 1)
        ));
        CATEGORIES.put("Spawner-Fuel", List.of(
                offer("blaze", "Blaze Rod", Material.BLAZE_ROD, 90, 25, 4),
                offer("bone", "Bone", Material.BONE, 8, 2, 16),
                offer("gunpowder", "Gunpowder", Material.GUNPOWDER, 22, 6, 8),
                offer("string", "String", Material.STRING, 10, 2, 16),
                offer("slime", "Slime Ball", Material.SLIME_BALL, 40, 10, 8),
                offer("shulker", "Shulker Shell", Material.SHULKER_SHELL, 1800, 500, 1)
        ));
        CATEGORIES.put("Farming", List.of(
                offer("cane", "Sugar Cane", Material.SUGAR_CANE, 6, 1, 16),
                offer("bamboo", "Bamboo", Material.BAMBOO, 4, 1, 16),
                offer("kelp", "Kelp", Material.KELP, 4, 1, 16),
                offer("melon", "Melon Slice", Material.MELON_SLICE, 3, 1, 16),
                offer("pumpkin", "Pumpkin", Material.PUMPKIN, 10, 2, 8),
                offer("wart", "Nether Wart", Material.NETHER_WART, 14, 4, 8)
        ));
    }

    private ShopCatalog() {
    }

    public static Inventory openRoot(VapezPlugin plugin, Player player) {
        Inventory inv = Bukkit.createInventory(new ShopHolder("root", null), 27, TITLE);
        int slot = 10;
        Material[] icons = {Material.BRICKS, Material.COOKED_BEEF, Material.DIAMOND_SWORD,
                Material.DIAMOND, Material.SPAWNER, Material.WHEAT};
        int i = 0;
        for (String category : CATEGORIES.keySet()) {
            ItemStack icon = new ItemStack(icons[Math.min(i++, icons.length - 1)]);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Text.mm("<aqua><bold>" + category + "</bold></aqua>"));
            meta.lore(List.of(
                    Text.mm("<gray>Click to browse.</gray>"),
                    Text.mm("<dark_gray>Balance: </dark_gray><yellow>" + plugin.vault().format(plugin.vault().balance(player)) + "</yellow>")
            ));
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot += 2;
        }
        return inv;
    }

    public static Inventory openCategory(VapezPlugin plugin, Player player, String category) {
        List<ShopOffer> offers = CATEGORIES.get(category);
        Inventory inv = Bukkit.createInventory(new ShopHolder("cat", category), 54,
                Text.mm("<gradient:#7BFFE9:#C084FC><bold>" + category + "</bold></gradient>"));
        if (offers == null) {
            return inv;
        }
        int slot = 0;
        for (ShopOffer offer : offers) {
            ItemStack icon = new ItemStack(offer.material(), Math.min(64, offer.amount()));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Text.mm("<white>" + offer.name() + "</white>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.mm("<gray>Amount:</gray> <white>x" + offer.amount() + "</white>"));
            lore.add(Text.mm("<green>Left-click BUY</green> <dark_gray>•</dark_gray> <yellow>" + plugin.vault().format(offer.buy()) + "</yellow>"));
            lore.add(Text.mm("<red>Right-click SELL</red> <dark_gray>•</dark_gray> <yellow>" + plugin.vault().format(offer.sell()) + "</yellow>"));
            lore.add(Text.mm("<dark_gray>Shift-click sells a full stack if present.</dark_gray>"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(Text.mm("<gray>Back</gray>"));
        back.setItemMeta(meta);
        inv.setItem(49, back);
        return inv;
    }

    public static ShopOffer offerAt(String category, int slot) {
        List<ShopOffer> offers = CATEGORIES.get(category);
        if (offers == null || slot < 0 || slot >= offers.size()) {
            return null;
        }
        return offers.get(slot);
    }

    private static ShopOffer offer(String id, String name, Material material, double buy, double sell, int amount) {
        return new ShopOffer(id, name, material, buy, sell, amount);
    }
}
