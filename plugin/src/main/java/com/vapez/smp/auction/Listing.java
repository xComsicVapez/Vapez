package com.vapez.smp.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class Listing {
    public String id;
    public UUID seller;
    public String sellerName;
    public ItemStack item;
    public double price;
    public long expiresAt;
}
