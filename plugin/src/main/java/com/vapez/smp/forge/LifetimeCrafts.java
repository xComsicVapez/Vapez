package com.vapez.smp.forge;

import com.vapez.smp.VapezPlugin;
import com.vapez.smp.util.YamlStore;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class LifetimeCrafts {

    private final VapezPlugin plugin;
    private final YamlStore backup;

    public LifetimeCrafts(VapezPlugin plugin) {
        this.plugin = plugin;
        this.backup = new YamlStore(plugin, "lifetime-crafts.yml");
    }

    public boolean hasCrafted(Player player) {
        Byte pdc = player.getPersistentDataContainer().get(plugin.items().keys().sovereignCrafted, PersistentDataType.BYTE);
        if (pdc != null && pdc == (byte) 1) {
            return true;
        }
        return backup.yaml().getBoolean("crafted." + player.getUniqueId(), false);
    }

    public void markCrafted(Player player) {
        player.getPersistentDataContainer().set(plugin.items().keys().sovereignCrafted, PersistentDataType.BYTE, (byte) 1);
        backup.yaml().set("crafted." + player.getUniqueId(), true);
        backup.yaml().set("crafted-at." + player.getUniqueId(), System.currentTimeMillis());
        backup.yaml().set("name." + player.getUniqueId(), player.getName());
        backup.save();
    }

    public boolean hasCrafted(UUID uuid) {
        return backup.yaml().getBoolean("crafted." + uuid, false);
    }

    public void saveSync() {
        backup.save();
    }
}
