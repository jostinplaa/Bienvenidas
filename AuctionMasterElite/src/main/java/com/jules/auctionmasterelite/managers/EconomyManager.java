package com.jules.auctionmasterelite.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import static org.bukkit.Bukkit.getServer;

public class EconomyManager {

    private Economy economy = null;

    public boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasEnough(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public void withdraw(OfflinePlayer player, double amount) {
        economy.withdrawPlayer(player, amount);
    }

    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
