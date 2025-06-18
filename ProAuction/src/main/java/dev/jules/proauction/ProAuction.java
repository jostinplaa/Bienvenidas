package dev.jules.proauction;

import dev.jules.proauction.util.LanguageManager; // Added import
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
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

import java.util.Collections; // Already present
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
    private String messagePrefix;
    private double salesTaxPercentage;
    private double listingFeePercentage;
    private String serverAccountName;
    private LanguageManager languageManager; // Added field
    private String currentLanguage; // Added field

    @Override
    public void onEnable() {
        saveDefaultConfig(); // Ensure config.yml exists first
        performReload();     // Load all configurations and initialize LanguageManager

        if (!setupEconomy()) {
            // If languageManager is initialized by performReload, this should be fine.
            // Otherwise, a hardcoded message or delayed message is needed.
            // Assuming performReload makes languageManager available:
            logSevere(languageManager.getMessage("plugin.vault.disabled"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        logInfo(languageManager.getMessage("plugin.vault.success"));

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

        logInfo(languageManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        logInfo(languageManager.getMessage("plugin.vault.unhook"));
        econ = null;

        if (auctionEndTask != null && !auctionEndTask.isCancelled()) {
            auctionEndTask.cancel();
        }
        logInfo(languageManager.getMessage("plugin.data.saveall"));
        if (auctionStorage != null) {
            auctionStorage.saveAllAuctions(activeAuctions);
        }
        logInfo(languageManager.getMessage("plugin.data.clearactive"));
        activeAuctions.clear();
        logInfo(languageManager.getMessage("plugin.disabled"));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            // This log occurs before LanguageManager might be ready if called early. Keep hardcoded.
            getLogger().severe("[ProAuction] Vault plugin not found! The plugin will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            // This log also occurs before LanguageManager might be ready. Keep hardcoded.
            getLogger().severe("[ProAuction] No economy plugin found through Vault! The plugin will be disabled.");
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
    public void sendMessage(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        if (sender == null || messageKey == null || messageKey.isEmpty()) return;
        // Ensure languageManager is available, otherwise fallback or log error
        if (languageManager == null) {
            sender.sendMessage(this.messagePrefix + ChatColor.RED + "Language manager not ready. Key: " + messageKey);
            return;
        }
        String message = languageManager.getMessage(messageKey, placeholders);
        sender.sendMessage(this.messagePrefix + message);
    }

    public void sendMessage(CommandSender sender, String messageKey) {
        sendMessage(sender, messageKey, Collections.emptyMap());
    }

    public void sendMessage(CommandSender sender, String messageKey, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        sendMessage(sender, messageKey, placeholders);
    }

    public void broadcastMessage(String messageKey, Map<String, String> placeholders) {
        if (messageKey == null || messageKey.isEmpty()) return;
        if (languageManager == null) {
            Bukkit.broadcastMessage(this.messagePrefix + ChatColor.RED + "Language manager not ready. Key: " + messageKey);
            return;
        }
        String message = languageManager.getMessage(messageKey, placeholders);
        Bukkit.broadcastMessage(this.messagePrefix + message);
    }

    public void broadcastMessage(String messageKey) {
        broadcastMessage(messageKey, Collections.emptyMap());
    }
     public void broadcastMessage(String messageKey, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        broadcastMessage(messageKey, placeholders);
    }


    // Log methods are for console, generally do not need translation or prefix if used for debugging/status.
    // However, if a log message is also a key user-facing status (like plugin enabled/disabled), it can use LanguageManager.
    public void logInfo(String message) { // This can be a simple wrapper or directly use getLogger()
        getLogger().info(message); // Raw message for info
    }

    public void logWarning(String message) {
        getLogger().warning(message); // Raw message for warning
    }

    public void logSevere(String message) {
        getLogger().severe(message); // Raw message for severe
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public LanguageManager getLanguageManager() { return this.languageManager; }

    private void loadMainConfiguration() {
        // Load message-prefix first as LanguageManager might use plugin.logInfo which uses the prefix
        this.messagePrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-prefix", "&6[ProAuction]&r "));

        // Determine and set language
        this.currentLanguage = getConfig().getString("language", "es"); // Default to "es"
        this.languageManager = new LanguageManager(this, this.currentLanguage); // Initialize/Re-initialize

        // Load other configurations
        this.salesTaxPercentage = getConfig().getDouble("sales-tax-percentage", 0.0);
        this.listingFeePercentage = getConfig().getDouble("listing-fee-percentage", 0.0);
        this.serverAccountName = getConfig().getString("server-account-name", "");

        // Log loaded settings to console (admin info) - Use getLogger() directly to avoid issues with LM init sequence
        getLogger().info("Loaded Message Prefix: " + this.messagePrefix);
        if (this.languageManager != null) { // Check if LM initialized before trying to get its filename
            getLogger().info("Loaded Language: " + this.currentLanguage + " (Using " + this.languageManager.getLangFileNameForLogging() + ")");
        } else {
            getLogger().warning("LanguageManager not initialized during loadMainConfiguration for logging language file.");
        }
        getLogger().info("Loaded Sales Tax: " + this.salesTaxPercentage + "%");
        getLogger().info("Loaded Listing Fee: " + this.listingFeePercentage + "%");
        getLogger().info("Loaded Server Account: '" + this.serverAccountName + "'");

        // This message should come after LM is confirmed ready.
        if (this.languageManager != null) {
             getLogger().info(this.languageManager.getMessage("plugin.reloaded"));
        }
    }

    public void performReload() {
        // Reload Bukkit's config.yml from disk
        reloadConfig();

        // Load the configuration values into plugin fields and reinitialize LanguageManager
        loadMainConfiguration();

        // Log to console (admin info, not using LanguageManager for this specific console log)
        getLogger().info("ProAuction configuration and language files have been reloaded via command.");
        // The LanguageManager itself will log which language file it (re)loaded during its own loadMessages().
    }

    // Public getters for configuration values
    public double getSalesTaxPercentage() { return this.salesTaxPercentage; }
    public double getListingFeePercentage() { return this.listingFeePercentage; }
    public String getServerAccountName() { return this.serverAccountName; }
}
