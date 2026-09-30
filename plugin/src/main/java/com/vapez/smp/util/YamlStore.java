package com.vapez.smp.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public final class YamlStore {

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public YamlStore(Plugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        reload();
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public File file() {
        return file;
    }

    public void reload() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create " + file.getParent());
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving " + file.getName(), ex);
        }
    }
}
