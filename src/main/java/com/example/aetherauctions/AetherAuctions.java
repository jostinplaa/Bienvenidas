package com.example.aetherauctions;

import com.example.aetherauctions.auction.AuctionManager;
import com.example.aetherauctions.commands.SubastaCommand;
import com.example.aetherauctions.database.DatabaseManager;
import com.example.aetherauctions.gui.GuiManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AetherAuctions extends JavaPlugin {

    private static final Logger logger = Logger.getLogger("AetherAuctions");
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private GuiManager guiManager;
    private static Economy econ = null;
    private BukkitTask expiredAuctionTask;
    private FileConfiguration messagesConfig;

    // NamespacedKeys for PersistentDataContainer
    public NamespacedKey auctionIdKey;
    // Add more keys as needed, e.g., for button identifiers


    @Override
    public void onEnable() {
        this.auctionIdKey = new NamespacedKey(this, "auction_id");

        saveDefaultConfig(); // Saves config.yml if not present
        loadMessagesConfig(); // Load messages.yml

        if (!setupEconomy()) {
            logger.severe("Vault or an Economy plugin not found! Disabling AetherAuctions.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        logger.info("Vault hook established successfully.");

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initializeDatabase();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to or initialize the database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auctionManager = new AuctionManager(this);
        guiManager = new GuiManager(this);

        // Register command executors
        getCommand("subasta").setExecutor(new SubastaCommand(this));
        // Register event listeners
        getServer().getPluginManager().registerEvents(guiManager, this);


        // Scheduler for expired auctions
        long checkInterval = getConfig().getLong("auction-expiry-check-interval-seconds", 60) * 20L; // Default 60 seconds
        expiredAuctionTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (auctionManager != null) {
                auctionManager.processExpiredAuctions();
            }
        }, 20L * 10, checkInterval); // Initial delay of 10 seconds, then repeat

        logger.info("AetherAuctions has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (expiredAuctionTask != null && !expiredAuctionTask.isCancelled()) {
            expiredAuctionTask.cancel();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        logger.info("AetherAuctions has been disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.warning("Vault plugin not found. Economy features will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("No economy provider found through Vault. Economy features will be disabled.");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public void loadMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        try (InputStream defMessagesStream = getResource("messages.yml");
             InputStreamReader reader = new InputStreamReader(defMessagesStream, StandardCharsets.UTF_8)) {
            if (defMessagesStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                messagesConfig.setDefaults(defConfig);
                messagesConfig.options().copyDefaults(true); // Copy new keys from defaults
                messagesConfig.save(messagesFile); // Save to include any new default messages
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not load or save messages.yml", e);
        }
    }

    public FileConfiguration getMessages() {
        if (messagesConfig == null) {
            // This should ideally not happen if onEnable is called first.
            // However, as a fallback:
            getLogger().warning("getMessages() called before messagesConfig was initialized. Attempting to load now.");
            loadMessagesConfig();
        }
        return messagesConfig;
    }


    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public Economy getEconomy() {
        return econ;
    }

    public static Logger getPluginLogger() {
        return logger;
    }
}
