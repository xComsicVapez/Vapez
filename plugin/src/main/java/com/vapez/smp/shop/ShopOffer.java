package com.vapez.smp.shop;

import org.bukkit.Material;

public record ShopOffer(String id, String name, Material material, double buy, double sell, int amount) {
}
