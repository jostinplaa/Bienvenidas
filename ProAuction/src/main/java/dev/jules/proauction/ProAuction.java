package dev.jules.proauction;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Added import
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender; // Added import
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import dev.jules.proauction.command.AuctionCommand;
import dev.jules.proauction.command.AuctionHouseCommand;
import dev.jules.proauction.gui.AuctionGUI;
import dev.jules.proauction.listener.GuiListener;
import dev.jules.proauction.model.Auction;
import dev.jules.proauction.storage.AuctionStorage;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProAuction extends JavaPlugin {

    private static Economy econ = null;
    private Map<UUID, Auction> activeAuctions = new HashMap<>();
    private AuctionManager auctionManager;
    private BukkitTask auctionEndTask;
    private AuctionStorage auctionStorage;
    private AuctionGUI auctionGUI;
    private String messagePrefix; // Added field

    @Override
    public void onEnable() {
        saveDefaultConfig(); // Ensure config.yml exists
        reloadPluginConfig(); // Load initial config including prefix

        if (!setupEconomy()) {
            // getLogger().severe("[ProAuction] Disabled due to no Vault dependency found or no economy plugin hooked!");
            logSevere("Disabled due to no Vault dependency found or no economy plugin hooked!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // getLogger().info("[ProAuction] Successfully hooked into Vault and found an economy plugin.");
        logInfo("Successfully hooked into Vault and found an economy plugin.");

        auctionStorage = new AuctionStorage(this);
        activeAuctions = auctionStorage.loadActiveAuctions();
        auctionManager = new AuctionManager(this);
        auctionGUI = new AuctionGUI(this);

        this.getCommand("auction").setExecutor(new AuctionCommand(this));
        this.getCommand("ah").setExecutor(new AuctionHouseCommand(this, auctionGUI));

        getServer().getPluginManager().registerEvents(new GuiListener(this, auctionGUI), this);

        auctionEndTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (auctionManager != null) {
                    auctionManager.checkActiveAuctions();
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        // getLogger().info("[ProAuction] enabled!");
        logInfo("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        // getLogger().info("[ProAuction] Unhooking from Vault.");
        logInfo("Unhooking from Vault.");
        econ = null;

        if (auctionEndTask != null && !auctionEndTask.isCancelled()) {
            auctionEndTask.cancel();
        }
        // getLogger().info("[ProAuction] Saving all active auctions...");
        logInfo("Saving all active auctions...");
        if (auctionStorage != null) {
            auctionStorage.saveAllAuctions(activeAuctions);
        }
        // getLogger().info("[ProAuction] Clearing active auctions map from memory.");
        logInfo("Clearing active auctions map from memory.");
        activeAuctions.clear();
        // getLogger().info("[ProAuction] disabled!");
        logInfo("Plugin disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            // getLogger().severe("[ProAuction] Vault plugin not found!");
            logSevere("Vault plugin not found!");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            // getLogger().severe("[ProAuction] No economy plugin found through Vault!");
            logSevere("No economy plugin found through Vault!");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    // Static methods will use Bukkit.getLogger() for now, prefixing manually if critical,
    // or rely on calling code (which should use instance methods) to have prefixed.
    // For internal logic/errors, a simple "[ProAuction]" might be fine.
    public static boolean hasEnough(OfflinePlayer player, double amount) {
        if (econ == null) {
            Bukkit.getLogger().warning("[ProAuction] Economy not initialized, cannot check balance.");
            return false;
        }
        return econ.has(player, amount);
    }

    public static boolean withdrawMoney(OfflinePlayer player, double amount) {
        if (econ == null) {
            Bukkit.getLogger().warning("[ProAuction] Economy not initialized, cannot withdraw money.");
            return false;
        }
        if (amount <= 0) {
            Bukkit.getLogger().warning("[ProAuction] Cannot withdraw zero or negative amount.");
            return false;
        }
        return econ.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean depositMoney(OfflinePlayer player, double amount) {
        if (econ == null) {
            Bukkit.getLogger().warning("[ProAuction] Economy not initialized, cannot deposit money.");
            return false;
        }
        if (amount <= 0) {
            Bukkit.getLogger().warning("[ProAuction] Cannot deposit zero or negative amount.");
            return false;
        }
        return econ.depositPlayer(player, amount).transactionSuccess();
    }

    public static String format(double amount) {
        if (econ == null) {
            Bukkit.getLogger().warning("[ProAuction] Economy not initialized, cannot format amount.");
            return String.valueOf(amount); // Fallback
        }
        return econ.format(amount);
    }

    // Auction Management Methods
    public void addAuction(Auction auction) {
        if (auction != null) {
            activeAuctions.put(auction.getAuctionId(), auction);
            if (auctionStorage != null) {
                auctionStorage.saveAuction(auction);
            }
        }
    }

    public Auction getAuction(UUID auctionId) {
        return activeAuctions.get(auctionId);
    }

    public void removeAuction(UUID auctionId) {
        activeAuctions.remove(auctionId);
        if (auctionStorage != null) {
            auctionStorage.deleteAuction(auctionId);
        }
    }

    public void updateAuctionInStorage(Auction auction) {
        if (auction != null && auctionStorage != null) {
            auctionStorage.saveAuction(auction);
        }
    }

    public Map<UUID, Auction> getActiveAuctions() {
        return Collections.unmodifiableMap(activeAuctions);
    }

    // Messaging Utilities
    public void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(this.messagePrefix + message);
    }

    public void broadcastMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Bukkit.broadcastMessage(this.messagePrefix + message);
    }

    public void logInfo(String message) {
        getLogger().info(ChatColor.stripColor(this.messagePrefix.trim()) + " " + message);
    }

    public void logWarning(String message) {
        getLogger().warning(ChatColor.stripColor(this.messagePrefix.trim()) + " " + message);
    }

    public void logSevere(String message) {
        getLogger().severe(ChatColor.stripColor(this.messagePrefix.trim()) + " " + message);
    }

    public String getMessagePrefix() { // Getter for prefix if needed externally
        return messagePrefix;
    }

    public void reloadPluginConfig() {
        reloadConfig(); // Bukkit's method to reload config.yml from disk
        this.messagePrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-prefix", "&6[ProAuction]&r "));
        logInfo("Configuration reloaded."); // Uses the new prefix
    }
}
