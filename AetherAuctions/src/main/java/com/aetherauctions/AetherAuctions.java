package com.aetherauctions;

import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.command.CommandManager;
import com.aetherauctions.config.ConfigManager; // Import ConfigManager
import com.aetherauctions.config.MessageManager; // Import MessageManager
import com.aetherauctions.database.DatabaseManager;
import com.aetherauctions.gui.GUIManager;
// import com.aetherauctions.gui.rework.NewGUIManager; // NewGUIManager is being removed
import com.aetherauctions.gui.rework.NewGUIInventoryListener; // Import NewGUIInventoryListener
import com.aetherauctions.listener.InventoryClickListener;
import com.aetherauctions.listeners.PlayerChatListener; // Changed to plural 'listeners'
import com.aetherauctions.listener.PlayerQuitListener; // Import PlayerQuitListener
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap; // Import HashMap
import java.util.Map;     // Import Map
import java.util.UUID;    // Import UUID
import java.util.logging.Level;

public class AetherAuctions extends JavaPlugin {

    private static Economy econ = null;
    private static AetherAuctions instance;

    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private GUIManager guiManager;
    // private NewGUIManager newGuiManager; // Field removed
    private NewGUIInventoryListener newGuiInventoryListener; // Add NewGUIInventoryListener field
    private CommandManager commandManager;
    private ConfigManager configManager; // Add ConfigManager
    private MessageManager messageManager; // Add MessageManager

    // For handling player input states
    public enum PlayerInputState {
        NONE,
        AWAITING_DURATION,
        AWAITING_START_PRICE,
        AWAITING_BUY_NOW_PRICE,
        AWAITING_BID_AMOUNT
    }
    private final Map<UUID, PlayerInputState> playerInputState = new HashMap<>();
    private final Map<UUID, Integer> playerTargetAuction = new HashMap<>(); // For bid command


    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Desactivado debido a que no se encontró Vault!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault encontrado y hookeado exitosamente.");

        // Initialize Config and Message Managers first
        configManager = new ConfigManager(this); // Loads config on instantiation
        messageManager = new MessageManager(this); // Loads messages on instantiation
        getLogger().info("ConfigManager y MessageManager inicializados.");

        databaseManager = new DatabaseManager(this); // DatabaseManager might use ConfigManager values (e.g. db file name)
        try {
            databaseManager.connect();
            databaseManager.createTables();
            getLogger().info("DatabaseManager inicializado y tablas creadas.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Error fatal al inicializar DatabaseManager. El plugin se desactivará.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auctionManager = new AuctionManager(this, databaseManager); // AuctionManager uses ConfigManager and MessageManager
        guiManager = new GUIManager(this, auctionManager); // GUIManager uses Config/MessageManagers

        auctionManager.loadActiveAuctions();
        auctionManager.startExpiredAuctionsTask();
        getLogger().info("AuctionManager y GUIManager inicializados.");

        // newGuiManager = new NewGUIManager(this); // Initialization removed
        // getLogger().info("NewGUIManager inicializado."); // Log removed

        commandManager = new CommandManager(this, auctionManager, guiManager); // CommandManager uses MessageManager
        if (this.getCommand("subasta") != null) {
            this.getCommand("subasta").setExecutor(commandManager);
            this.getCommand("subasta").setTabCompleter(commandManager);
            getLogger().info("Comando '/subasta' registrado.");
        } else {
            getLogger().severe("No se pudo registrar el comando '/subasta'. Verifica plugin.yml.");
        }

        // Register Listeners
        newGuiInventoryListener = new NewGUIInventoryListener(this); // Initialize NewGUIInventoryListener
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this, guiManager, auctionManager), this); // Old listener
        getServer().getPluginManager().registerEvents(newGuiInventoryListener, this); // Register NewGUIInventoryListener instance
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this); // Corrected constructor call
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this); // Register PlayerQuitListener

        getLogger().info("AetherAuctions se ha habilitado correctamente!");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.stopExpiredAuctionsTask();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        playerInputState.clear();
        playerTargetAuction.clear();
        // if (newGuiManager != null) { // Call removed
            // newGuiManager.clearAllPlayerStates();
        // }
        getLogger().info("AetherAuctions se ha deshabilitado.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault no encontrado en el servidor.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No se pudo obtener el RegisteredServiceProvider para Economy de Vault.");
            return false;
        }
        econ = rsp.getProvider();
        if (econ == null) {
            getLogger().severe("No se pudo obtener el Economy provider de Vault.");
            return false;
        }
        return true;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static AetherAuctions getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    // public NewGUIManager getNewGuiManager() { // Getter removed
    //    return newGuiManager;
    // }

    public NewGUIInventoryListener getNewGuiInventoryListener() { // Getter for NewGUIInventoryListener
        return newGuiInventoryListener;
    }

    public ConfigManager getConfigManager() { // Getter for ConfigManager
        return configManager;
    }

    public MessageManager getMessageManager() { // Getter for MessageManager
        return messageManager;
    }

    public Map<UUID, PlayerInputState> getPlayerInputState() {
        return playerInputState;
    }

    public Map<UUID, Integer> getPlayerTargetAuction() {
        return playerTargetAuction;
    }

    public void reloadPluginConfig() {
       if (configManager != null) configManager.reloadConfig();
       if (messageManager != null) messageManager.reloadMessages();

       // Optionally, notify other managers if they cache config values extensively
       // For example, if AuctionManager caches fee values:
       // if (auctionManager != null) auctionManager.onConfigReload();

       getLogger().info("Configuraciones de AetherAuctions recargadas.");
    }
}
