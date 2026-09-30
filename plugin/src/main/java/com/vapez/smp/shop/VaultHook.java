package com.vapez.smp.shop;

import com.vapez.smp.VapezPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class VaultHook {

    private final VapezPlugin plugin;
    private Economy economy;

    public VaultHook(VapezPlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found — shop/auction/pay will be disabled until Vault + EssentialsX Economy are installed.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy provider (install EssentialsX).");
            return;
        }
        this.economy = rsp.getProvider();
        plugin.getLogger().info("Hooked economy: " + economy.getName());
    }

    public boolean available() {
        return economy != null;
    }

    public double balance(Player player) {
        return available() ? economy.getBalance(player) : 0;
    }

    public double balance(UUID uuid) {
        if (!available()) {
            return Double.NaN;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return economy.getBalance(off);
    }

    public boolean withdraw(Player player, double amount) {
        if (!available()) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!available() || amount <= 0) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(UUID uuid, double amount) {
        if (!available() || amount <= 0) {
            return false;
        }
        return economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
    }

    public boolean has(Player player, double amount) {
        return available() && economy.has(player, amount);
    }

    public String format(double amount) {
        if (!available()) {
            return String.format("$%.2f", amount);
        }
        return economy.format(amount);
    }
}
