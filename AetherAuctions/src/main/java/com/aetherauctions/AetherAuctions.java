package com.aetherauctions;

import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.command.CommandManager;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.listener.InventoryClickListener;
import com.aetherauctions.listener.PlayerChatListener;
import com.aetherauctions.listener.PlayerQuitListener;
import com.aetherauctions.listener.PlayerJoinListener;
import com.aetherauctions.storage.AuctionStorage;
import com.aetherauctions.managers.RewardManager; // Corrected package
// No longer need com.aetherauctions.gui.GUIManager instance if it's all static
// No longer need com.aetherauctions.gui.rework.NewGUIInventoryListener

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class AetherAuctions extends JavaPlugin {

    private static AetherAuctions instance;
    private static Economy econ = null;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private AuctionStorage auctionStorage;
    private AuctionManager auctionManager;
    private CommandManager commandManager;
    private InventoryClickListener inventoryClickListener; // The main/consolidated listener
    private RewardManager rewardManager; // Add RewardManager field

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault no encontrado o no se pudo hookear Economy! Desactivando AetherAuctions.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault Economy hookeado exitosamente.");

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        getLogger().info("ConfigManager y MessageManager inicializados.");

        auctionStorage = new AuctionStorage(this);
        try {
            auctionStorage.initDatabase();
            getLogger().info("AuctionStorage (Database) inicializado.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error inicializando la base de datos! Desactivando AetherAuctions.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auctionManager = new AuctionManager(this, auctionStorage);
        auctionManager.loadAuctions();
        getLogger().info("AuctionManager inicializado y subastas cargadas.");

        // Initialize RewardManager with all its dependencies
        rewardManager = new RewardManager(this, auctionStorage, configManager, messageManager, econ);
        rewardManager.scheduleOldDeliveredRewardCleanup(); // Schedule cleanup task
        getLogger().info("RewardManager inicializado y limpieza programada.");

        // CommandManager constructor remains CommandManager(this)
        // It will access RewardManager via plugin.getRewardManager()
        commandManager = new CommandManager(this);
        if (getCommand("subasta") != null) {
            getCommand("subasta").setExecutor(commandManager);
            getCommand("subasta").setTabCompleter(commandManager);
            getLogger().info("Comando '/subasta' registrado.");
        } else {
            getLogger().severe("Error: El comando 'subasta' no está definido en plugin.yml!");
        }

        // Register Listeners
        inventoryClickListener = new InventoryClickListener(this);
        PlayerChatListener chatListener = new PlayerChatListener(this, this.inventoryClickListener);
        PlayerQuitListener quitListener = new PlayerQuitListener(this, inventoryClickListener);
        PlayerJoinListener joinListener = new PlayerJoinListener(this, rewardManager); // Pass initialized rewardManager

        getServer().getPluginManager().registerEvents(inventoryClickListener, this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(quitListener, this);
        getServer().getPluginManager().registerEvents(joinListener, this);
        getLogger().info("Listeners registrados.");

        getLogger().info("AetherAuctions v" + getDescription().getVersion() + " habilitado exitosamente!");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.stopScheduledTasks();
        }
        // auctionStorage.closeDatabase(); // Ya no es necesario llamar explícitamente aquí si se maneja en onDisable de AuctionStorage
        getLogger().info("AetherAuctions v" + getDescription().getVersion() + " deshabilitado.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault no encontrado. Deshabilitando AetherAuctions.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No se encontró un proveedor de economía compatible con Vault. Deshabilitando AetherAuctions.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        econ = rsp.getProvider();
        if (econ == null) {
            getLogger().severe("El proveedor de economía de Vault es nulo. Deshabilitando AetherAuctions.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        return true;
    }

    public static AetherAuctions getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public AuctionStorage getAuctionStorage() {
        return auctionStorage;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public InventoryClickListener getInventoryClickListener() {
        return inventoryClickListener;
    }

    public RewardManager getRewardManager() { // Getter for RewardManager
        return rewardManager;
    }
}
