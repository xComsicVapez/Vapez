package com.vapez.smp.items;

import com.vapez.smp.VapezKeys;
import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class CustomItems {

    public static final String CRYSTAL = "aetherium_crystal";
    public static final String INGOT = "aetherium_ingot";
    public static final String EDGE = "sovereign_edge";
    public static final String SWORD = "aetherium_sword";
    public static final String PICKAXE = "aetherium_pickaxe";
    public static final String AXE = "aetherium_axe";
    public static final String HELMET = "aetherium_helmet";
    public static final String CHEST = "aetherium_chestplate";
    public static final String LEGS = "aetherium_leggings";
    public static final String BOOTS = "aetherium_boots";
    public static final String HEART = "heart";
    public static final String KEY = "crate_key";

    private final VapezPlugin plugin;
    private final VapezKeys keys;

    public CustomItems(VapezPlugin plugin) {
        this.plugin = plugin;
        this.keys = new VapezKeys(plugin);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // keys already constructed
        });
    }

    public VapezKeys keys() {
        return keys;
    }

    public ItemStack create(String id, int amount) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case CRYSTAL -> crystal(amount);
            case INGOT -> ingot(amount);
            case EDGE -> sovereignEdge();
            case SWORD -> tool(SWORD, Material.NETHERITE_SWORD, "<gradient:#7BFFE9:#C084FC><bold>Aetherium Saber</bold></gradient>",
                    9.0, 1.8, EquipmentSlotGroup.MAINHAND, "A netherite-surpassing blade grown from void-tide crystals.");
            case PICKAXE -> tool(PICKAXE, Material.NETHERITE_PICKAXE, "<gradient:#7BFFE9:#A78BFA><bold>Aetherium Drill</bold></gradient>",
                    7.0, 1.3, EquipmentSlotGroup.MAINHAND, "Mines deepslate as if it were snow. Haste pulse on right-click.");
            case AXE -> tool(AXE, Material.NETHERITE_AXE, "<gradient:#C084FC:#7BFFE9><bold>Aetherium Cleaver</bold></gradient>",
                    11.0, 1.1, EquipmentSlotGroup.MAINHAND, "Right-click to slam the ground with a void shockwave.");
            case HELMET -> armor(HELMET, Material.NETHERITE_HELMET, "<gradient:#7BFFE9:#E879F9><bold>Aetherium Helm</bold></gradient>",
                    4.0, 4.0, EquipmentSlotGroup.HEAD);
            case CHEST -> armor(CHEST, Material.NETHERITE_CHESTPLATE, "<gradient:#7BFFE9:#E879F9><bold>Aetherium Aegis</bold></gradient>",
                    10.0, 5.0, EquipmentSlotGroup.CHEST);
            case LEGS -> armor(LEGS, Material.NETHERITE_LEGGINGS, "<gradient:#7BFFE9:#E879F9><bold>Aetherium Greaves</bold></gradient>",
                    8.0, 4.0, EquipmentSlotGroup.LEGS);
            case BOOTS -> armor(BOOTS, Material.NETHERITE_BOOTS, "<gradient:#7BFFE9:#E879F9><bold>Aetherium Treads</bold></gradient>",
                    4.0, 4.0, EquipmentSlotGroup.FEET);
            case HEART -> heart(amount);
            case KEY, "key" -> crateKey(amount);
            default -> null;
        };
    }

    public ItemStack crystal(int amount) {
        ItemStack stack = base(Material.AMETHYST_SHARD, CRYSTAL, amount,
                "<gradient:#A78BFA:#7BFFE9><bold>Aetherium Crystal</bold></gradient>",
                List.of(
                        Text.mm("<gray>Late-game ore, rarer than diamonds.</gray>"),
                        Text.mm("<dark_gray>Found in deepslate near bedrock (Y=-64 to -50).</dark_gray>"),
                        Text.mm("<aqua>Smelt in a blast furnace → Aetherium Ingot</aqua>")
                ));
        model(stack, CRYSTAL);
        return stack;
    }

    public ItemStack ingot(int amount) {
        ItemStack stack = base(Material.NETHERITE_INGOT, INGOT, amount,
                "<gradient:#7BFFE9:#C084FC><bold>Aetherium Ingot</bold></gradient>",
                List.of(
                        Text.mm("<gray>Refined void-tide metal. Outclasses netherite.</gray>"),
                        Text.mm("<yellow>Used at the Aetherforge for Sovereign gear.</yellow>")
                ));
        model(stack, INGOT);
        return stack;
    }

    public ItemStack sovereignEdge() {
        ItemStack stack = tool(EDGE, Material.NETHERITE_SWORD,
                "<gradient:#F5D0FE:#7BFFE9:#C084FC><bold>Sovereign Edge</bold></gradient>",
                14.0, 2.0, EquipmentSlotGroup.MAINHAND,
                "One craft per player lifetime. Right-click: Void Rend dash.");
        ItemMeta meta = stack.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 7, true);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 4, true);
        meta.addEnchant(Enchantment.LOOTING, 5, true);
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, modifier(EDGE + "_speed", 0.04, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, modifier(EDGE + "_kb", 0.3, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.MAX_HEALTH, modifier(EDGE + "_hp", 4.0, EquipmentSlotGroup.MAINHAND));
        meta.lore(List.of(
                Text.mm("<light_purple>Legendary relic — unique to you, forever.</light_purple>"),
                Text.mm("<gray>Right-click: <aqua>Void Rend</aqua> dash + sonic burst</gray>"),
                Text.mm("<gray>Hit: 18% chance to chain void lightning</gray>"),
                Text.mm("<dark_gray>PersistentData: vapez:sovereign_crafted</dark_gray>")
        ));
        stack.setItemMeta(meta);
        model(stack, EDGE);
        return stack;
    }

    public ItemStack heart(int amount) {
        ItemStack stack = base(Material.NETHER_STAR, HEART, amount,
                "<red><bold>Stolen Heart</bold></red>",
                List.of(Text.mm("<gray>Right-click to absorb +1 lifesteal heart.</gray>")));
        model(stack, HEART);
        return stack;
    }

    public ItemStack crateKey(int amount) {
        ItemStack stack = base(Material.TRIPWIRE_HOOK, KEY, amount,
                "<gold><bold>Citadel Crate Key</bold></gold>",
                List.of(Text.mm("<gray>Use on a Citadel crate at spawn.</gray>")));
        stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        model(stack, KEY);
        return stack;
    }

    public boolean is(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String value = stack.getItemMeta().getPersistentDataContainer().get(keys.itemId, PersistentDataType.STRING);
        return id.equals(value);
    }

    public String id(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.itemId, PersistentDataType.STRING);
    }

    public boolean isAetheriumGear(ItemStack stack) {
        String id = id(stack);
        return id != null && (id.startsWith("aetherium_") || EDGE.equals(id));
    }

    public boolean isSovereign(ItemStack stack) {
        return is(stack, EDGE);
    }

    private ItemStack tool(String id, Material material, String name, double damage, double speed,
                           EquipmentSlotGroup slot, String lore) {
        ItemStack stack = base(material, id, 1, name, List.of(Text.mm("<gray>" + lore + "</gray>")));
        ItemMeta meta = stack.getItemMeta();
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier(id + "_dmg", damage, slot));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, modifier(id + "_spd", speed - 4.0, slot));
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        stack.setItemMeta(meta);
        model(stack, id);
        return stack;
    }

    private ItemStack armor(String id, Material material, String name, double armor, double toughness,
                            EquipmentSlotGroup slot) {
        ItemStack stack = base(material, id, 1, name, List.of(
                Text.mm("<gray>Superior to netherite. Full set: void cloak + extra hearts.</gray>")));
        ItemMeta meta = stack.getItemMeta();
        meta.addAttributeModifier(Attribute.ARMOR, modifier(id + "_arm", armor, slot));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, modifier(id + "_tuf", toughness, slot));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, modifier(id + "_kb", 0.12, slot));
        meta.addAttributeModifier(Attribute.MAX_HEALTH, modifier(id + "_hp", 2.0, slot));
        meta.addEnchant(Enchantment.PROTECTION, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        stack.setItemMeta(meta);
        model(stack, id);
        return stack;
    }

    private ItemStack base(Material material, String id, int amount, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.itemName(name));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keys.itemId, PersistentDataType.STRING, id);
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private void model(ItemStack stack, String id) {
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("vapez", id));
        stack.setItemMeta(meta);
    }

    private AttributeModifier modifier(String key, double amount, EquipmentSlotGroup slot) {
        return new AttributeModifier(
                new NamespacedKey(plugin, key),
                amount,
                AttributeModifier.Operation.ADD_NUMBER,
                slot
        );
    }
}
