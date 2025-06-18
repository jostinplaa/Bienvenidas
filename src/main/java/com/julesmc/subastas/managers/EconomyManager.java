package com.julesmc.subastas.managers;

import com.julesmc.subastas.SubastasPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer; // Import OfflinePlayer for broader compatibility
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

public class EconomyManager {

    private final SubastasPlugin plugin;
    private final LocaleManager localeManager;
    private Economy economy = null;

    public EconomyManager(SubastasPlugin plugin, LocaleManager localeManager) {
        this.plugin = plugin;
        this.localeManager = localeManager;
    }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            // This case should ideally be caught by SubastasPlugin's initial Vault check
            plugin.getLogger().severe(localeManager.getRawMessage("error.vault-not-found"));
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning(localeManager.getRawMessage("error.economy-provider-not-found"));
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasAccount(Player player) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available, cannot check account for " + player.getName());
            return false;
        }
        return economy.hasAccount(player);
    }

    public boolean hasAccount(OfflinePlayer player) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available, cannot check account for " + player.getName());
            return false;
        }
        return economy.hasAccount(player);
    }

    public double getBalance(Player player) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available, cannot get balance for " + player.getName());
            return 0;
        }
        return economy.getBalance(player);
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available, cannot get balance for " + player.getName());
            return 0;
        }
        return economy.getBalance(player);
    }

    public boolean hasEnough(Player player, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available, cannot check if " + player.getName() + " has enough funds.");
            return false;
        }
        return economy.has(player, amount);
    }

    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available, cannot check if " + player.getName() + " has enough funds.");
            return false;
        }
        return economy.has(player, amount);
    }

    public boolean withdrawPlayer(Player player, double amount) {
        if (economy == null) {
            plugin.getLogger().severe(localeManager.getRawMessage("error.economy-error", "action", "withdraw money (economy not found)"));
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Failed to withdraw " + amount + " from " + player.getName() + ": " + response.errorMessage);
            // Optionally, send a message to the player using LocaleManager if appropriate for the context
            // player.sendMessage(localeManager.getMessage("error.insufficient-funds", "amount", String.valueOf(amount)));
        }
        return response.transactionSuccess();
    }

    public boolean withdrawPlayer(OfflinePlayer player, double amount) {
        if (economy == null) {
            plugin.getLogger().severe(localeManager.getRawMessage("error.economy-error", "action", "withdraw money (economy not found)"));
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
         if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Failed to withdraw " + amount + " from " + player.getName() + ": " + response.errorMessage);
        }
        return response.transactionSuccess();
    }

    public boolean depositPlayer(Player player, double amount) {
        if (economy == null) {
            plugin.getLogger().severe(localeManager.getRawMessage("error.economy-error", "action", "deposit money (economy not found)"));
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Failed to deposit " + amount + " to " + player.getName() + ": " + response.errorMessage);
        }
        return response.transactionSuccess();
    }

    public boolean depositPlayer(OfflinePlayer player, double amount) {
        if (economy == null) {
            plugin.getLogger().severe(localeManager.getRawMessage("error.economy-error", "action", "deposit money (economy not found)"));
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Failed to deposit " + amount + " to " + player.getName() + ": " + response.errorMessage);
        }
        return response.transactionSuccess();
    }

    public Economy getEconomy() {
        return economy;
    }
}
