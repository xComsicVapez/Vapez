package com.vapez.smp.recipe;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.items.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;

public final class CustomRecipes {

    private CustomRecipes() {
    }

    public static void register(VapezPlugin plugin) {
        // Blast / furnace: crystal → ingot (custom ingredient via exact item match isn't supported
        // in vanilla furnaces, so we use a listener-backed shapeless-style furnace recipe on AMETHYST_SHARD
        // and OreBreakListener already drops the custom crystal). Players smelt the custom crystal
        // which is based on AMETHYST_SHARD.
        FurnaceRecipe furnace = new FurnaceRecipe(
                new NamespacedKey(plugin, "aetherium_ingot_smelt"),
                plugin.items().ingot(1),
                new RecipeChoice.ExactChoice(plugin.items().crystal(1)),
                4.0f,
                200
        );
        Bukkit.addRecipe(furnace);

        FurnaceRecipe blast = new FurnaceRecipe(
                new NamespacedKey(plugin, "aetherium_ingot_blast"),
                plugin.items().ingot(1),
                new RecipeChoice.ExactChoice(plugin.items().crystal(1)),
                4.0f,
                100
        );
        Bukkit.addRecipe(blast);

        ShapedRecipe saber = new ShapedRecipe(new NamespacedKey(plugin, "aetherium_sword"), plugin.items().create(CustomItems.SWORD, 1));
        saber.shape(" I ", " I ", " S ");
        saber.setIngredient('I', new RecipeChoice.ExactChoice(plugin.items().ingot(1)));
        saber.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(saber);

        ShapedRecipe pick = new ShapedRecipe(new NamespacedKey(plugin, "aetherium_pickaxe"), plugin.items().create(CustomItems.PICKAXE, 1));
        pick.shape("III", " S ", " S ");
        pick.setIngredient('I', new RecipeChoice.ExactChoice(plugin.items().ingot(1)));
        pick.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(pick);

        ShapedRecipe axe = new ShapedRecipe(new NamespacedKey(plugin, "aetherium_axe"), plugin.items().create(CustomItems.AXE, 1));
        axe.shape("II ", "IS ", " S ");
        axe.setIngredient('I', new RecipeChoice.ExactChoice(plugin.items().ingot(1)));
        axe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(axe);

        armor(plugin, "aetherium_helmet", plugin.items().create(CustomItems.HELMET, 1), "III", "I I", "   ");
        armor(plugin, "aetherium_chestplate", plugin.items().create(CustomItems.CHEST, 1), "I I", "III", "III");
        armor(plugin, "aetherium_leggings", plugin.items().create(CustomItems.LEGS, 1), "III", "I I", "I I");
        armor(plugin, "aetherium_boots", plugin.items().create(CustomItems.BOOTS, 1), "   ", "I I", "I I");

        // Smithing fallback: netherite sword + nether star + aetherium ingot is NOT the lifetime relic.
        // The Sovereign Edge is forge-only. This recipe upgrades a netherite sword into the lesser saber
        // if someone wants an alternate path.
        try {
            SmithingTransformRecipe smith = new SmithingTransformRecipe(
                    new NamespacedKey(plugin, "aetherium_smith_saber"),
                    plugin.items().create(CustomItems.SWORD, 1),
                    new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD),
                    new RecipeChoice.ExactChoice(plugin.items().ingot(1))
            );
            Bukkit.addRecipe(smith);
        } catch (Throwable ignored) {
            plugin.getLogger().info("Smithing transform recipe skipped on this API.");
        }
    }

    private static void armor(VapezPlugin plugin, String key, ItemStack result, String a, String b, String c) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, key), result);
        recipe.shape(a, b, c);
        recipe.setIngredient('I', new RecipeChoice.ExactChoice(plugin.items().ingot(1)));
        Bukkit.addRecipe(recipe);
    }
}
