package com.vapez.smp.shop;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ShopHolder implements InventoryHolder {

    private final String kind;
    private final String category;

    public ShopHolder(String kind, String category) {
        this.kind = kind;
        this.category = category;
    }

    public String kind() {
        return kind;
    }

    public String category() {
        return category;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 9);
    }
}
