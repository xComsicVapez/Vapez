package com.vapez.smp;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class VapezKeys {

    public final NamespacedKey itemId;
    public final NamespacedKey uniqueKit;
    public final NamespacedKey sovereignCrafted;
    public final NamespacedKey hearts;
    public final NamespacedKey crateType;
    public final NamespacedKey npcId;
    public final NamespacedKey citadelBuilt;
    public final NamespacedKey oreMark;
    public final NamespacedKey starterShulker;

    public VapezKeys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.uniqueKit = new NamespacedKey(plugin, "unique_kit");
        this.sovereignCrafted = new NamespacedKey(plugin, "sovereign_crafted");
        this.hearts = new NamespacedKey(plugin, "lifesteal_hearts");
        this.crateType = new NamespacedKey(plugin, "crate_type");
        this.npcId = new NamespacedKey(plugin, "npc_id");
        this.citadelBuilt = new NamespacedKey(plugin, "citadel_built");
        this.oreMark = new NamespacedKey(plugin, "aetherium_ore");
        this.starterShulker = new NamespacedKey(plugin, "starter_shulker");
    }
}
