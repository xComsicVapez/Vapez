package com.vapez.smp.forge;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.items.CustomItems;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class ForgeListener implements Listener, InventoryHolder {

    public static final Component TITLE = Text.mm("<gradient:#7BFFE9:#C084FC><bold>Aetherforge</bold></gradient> <dark_gray>— one craft per lifetime</dark_gray>");
    private static final int FORGE_SLOT = 22;
    private static final int[] PANE_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 41, 42, 43, 44};

    private final VapezPlugin plugin;

    public ForgeListener(VapezPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 45, TITLE);
        ItemStack glass = pane();
        for (int slot : PANE_SLOTS) {
            inv.setItem(slot, glass);
        }
        inv.setItem(4, hint());
        inv.setItem(FORGE_SLOT, forgeButton(player));
        inv.setItem(40, resultPreview(player));
        player.openInventory(inv);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 45, TITLE);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().title().equals(TITLE))) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory().getHolder() != event.getView().getTopInventory().getHolder()
                && event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ForgeListener)
                && !event.getView().title().equals(TITLE)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 45) {
            event.setCancelled(true);
        }
        if (slot == FORGE_SLOT) {
            attemptForge(player, event.getView().getTopInventory());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // no stored items in forge — ingredients stay in player inventory
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !plugin.items().isSovereign(result)) {
            return;
        }
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        if (plugin.settings().oneCraft() && plugin.lifetimeCrafts().hasCrafted(player)) {
            event.getInventory().setResult(null);
        }
    }

    private void attemptForge(Player player, Inventory gui) {
        if (plugin.settings().oneCraft() && plugin.lifetimeCrafts().hasCrafted(player)) {
            player.sendMessage(plugin.settings().prefix().append(
                    Component.text("You already forged your Sovereign Edge. One craft per lifetime.", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.7f, 0.5f);
            return;
        }
        Map<Material, Integer> vanilla = new HashMap<>();
        vanilla.put(Material.NETHER_STAR, plugin.settings().requiredStars());
        vanilla.put(Material.NETHERITE_INGOT, 1);
        vanilla.put(Material.HEAVY_CORE, 1);
        vanilla.put(Material.DRAGON_HEAD, 1);
        vanilla.put(Material.BEACON, 1);
        vanilla.put(Material.ENCHANTED_GOLDEN_APPLE, 1);
        vanilla.put(Material.ELYTRA, 1);
        vanilla.put(Material.TOTEM_OF_UNDYING, 1);
        vanilla.put(Material.HEART_OF_THE_SEA, 1);
        vanilla.put(Material.ECHO_SHARD, plugin.settings().requiredEcho());

        if (!hasIngots(player, plugin.settings().requiredIngots()) || !hasVanilla(player, vanilla)) {
            player.sendMessage(plugin.settings().prefix().append(Component.text(
                    "Missing ingredients. Need " + plugin.settings().requiredIngots()
                            + " Aetherium Ingots, Nether Star, Netherite Ingot, Heavy Core, Dragon Head, Beacon, Enchanted Golden Apple, Elytra, Totem, Heart of the Sea, "
                            + plugin.settings().requiredEcho() + " Echo Shards.", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        removeIngots(player, plugin.settings().requiredIngots());
        removeVanilla(player, vanilla);
        plugin.lifetimeCrafts().markCrafted(player);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(plugin.items().sovereignEdge());
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(plugin.settings().prefix().append(Text.mm(
                "<gradient:#F5D0FE:#7BFFE9>The Aetherforge accepts your offering. The Sovereign Edge is yours — and yours alone.</gradient>")));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        player.closeInventory();
        gui.setItem(40, resultPreview(player));
    }

    private boolean hasIngots(Player player, int amount) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (plugin.items().is(stack, CustomItems.INGOT)) {
                found += stack.getAmount();
            }
        }
        return found >= amount;
    }

    private void removeIngots(Player player, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!plugin.items().is(stack, CustomItems.INGOT)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
        }
    }

    private boolean hasVanilla(Player player, Map<Material, Integer> required) {
        for (Map.Entry<Material, Integer> e : required.entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(e.getKey()), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private void removeVanilla(Player player, Map<Material, Integer> required) {
        for (Map.Entry<Material, Integer> e : required.entrySet()) {
            player.getInventory().removeItem(new ItemStack(e.getKey(), e.getValue()));
        }
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var meta = stack.getItemMeta();
        meta.displayName(Text.mm("<dark_gray> </dark_gray>"));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack hint() {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK);
        var meta = stack.getItemMeta();
        meta.displayName(Text.mm("<aqua><bold>Ritual Ingredients</bold></aqua>"));
        meta.lore(java.util.List.of(
                Text.mm("<gray>Keep these in your inventory, then press Forge:</gray>"),
                Text.mm("<white>8 × Aetherium Ingot</white>"),
                Text.mm("<white>1 × Nether Star</white>"),
                Text.mm("<white>1 × Netherite Ingot</white>"),
                Text.mm("<white>1 × Heavy Core</white>"),
                Text.mm("<white>1 × Dragon Head</white>"),
                Text.mm("<white>1 × Beacon</white>"),
                Text.mm("<white>1 × Enchanted Golden Apple</white>"),
                Text.mm("<white>1 × Elytra</white>"),
                Text.mm("<white>1 × Totem of Undying</white>"),
                Text.mm("<white>1 × Heart of the Sea</white>"),
                Text.mm("<white>4 × Echo Shard</white>"),
                Text.mm("<red>One successful craft per player lifetime (PDC).</red>")
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack forgeButton(Player player) {
        boolean locked = plugin.settings().oneCraft() && plugin.lifetimeCrafts().hasCrafted(player);
        ItemStack stack = new ItemStack(locked ? Material.BARRIER : Material.ANVIL);
        var meta = stack.getItemMeta();
        meta.displayName(locked
                ? Text.mm("<red><bold>Already Forged</bold></red>")
                : Text.mm("<green><bold>FORGE SOVEREIGN EDGE</bold></green>"));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack resultPreview(Player player) {
        if (plugin.lifetimeCrafts().hasCrafted(player)) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            var meta = barrier.getItemMeta();
            meta.displayName(Text.mm("<red>Lifetime lock active</red>"));
            barrier.setItemMeta(meta);
            return barrier;
        }
        return plugin.items().sovereignEdge();
    }
}
