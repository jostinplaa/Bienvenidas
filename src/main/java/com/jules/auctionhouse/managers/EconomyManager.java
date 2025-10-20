package com.jules.auctionhouse.managers;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

public class EconomyManager {

    private Economy economy = null;
    private final Logger logger = Bukkit.getLogger();

    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.severe("Vault no encontrado. Deshabilitando AuctionHouse.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.severe("No se encontró un proveedor de economía compatible. Deshabilitando AuctionHouse.");
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public boolean hasEnough(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public EconomyResponse withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount);
    }

    public EconomyResponse deposit(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount);
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}