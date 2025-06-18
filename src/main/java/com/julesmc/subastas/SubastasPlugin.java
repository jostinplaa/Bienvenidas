package com.julesmc.subastas;

import com.julesmc.subastas.commands.SubastasCommand;
import com.julesmc.subastas.commands.SubastasCommand;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.commands.SubastasCommand;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.gui.GuiManager;
import com.julesmc.subastas.listeners.InventoryClickListener;
import com.julesmc.subastas.managers.ConfigManager;
import com.julesmc.subastas.managers.LocaleManager;
import com.julesmc.subastas.managers.EconomyManager;
import com.julesmc.subastas.tasks.AuctionEndTask; // Added import
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask; // Added import

import java.util.logging.Level;
// Removed static import for Logger, will use plugin's logger

public class SubastasPlugin extends JavaPlugin {

    // Removed static log, using this.getLogger()
    private ConfigManager configManager;
    private LocaleManager localeManager;
    private EconomyManager economyManager;
    private DatabaseManager databaseManager;
    private GuiManager guiManager;
    private BukkitTask auctionEndTask; // Added task reference

    @Override
    public void onEnable() {
        // Initialize ConfigManager first
        configManager = new ConfigManager(this);
        try {
            configManager.loadConfig();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading configuration, disabling plugin.", e);
            getLogger().severe(String.format("[%s] - Disabled due to a critical error loading the configuration.", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize LocaleManager
        localeManager = new LocaleManager(this, configManager);
        String lang = configManager.getString("language", "es");
        try {
            if (!localeManager.loadMessages(lang)) {
                getLogger().warning(String.format("Could not load messages for language '%s'. Using fallback messages.", lang));
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading language files, disabling plugin.", e);
            getLogger().severe(String.format("[%s] - Disabled due to a critical error loading language files.", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check for Vault first
        if (!checkVault()) {
            getLogger().severe(localeManager.getRawMessage("error.vault-not-found"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize EconomyManager
        economyManager = new EconomyManager(this, localeManager);
        if (!economyManager.setupEconomy()) {
            // Message for provider not found is handled by EconomyManager.setupEconomy()
            // If setupEconomy returns false, it means either Vault is not there (already checked)
            // or no economy provider was found. The specific message is logged by EconomyManager.
            // We might still want to disable the plugin if economy is critical,
            // or let it run with economy features disabled.
            // For now, the "error.economy-provider-not-found" is a warning,
            // and "error.vault-not-found" (which is more critical) disables the plugin.
            // If economy is absolutely essential for all plugin functions, uncomment next lines:
            // getLogger().severe(localeManager.getRawMessage("error.economy-provider-not-found"));
            // getServer().getPluginManager().disablePlugin(this);
            // return;
            getLogger().warning(localeManager.getRawMessage("error.economy-provider-not-found") + " Plugin features requiring economy will be disabled.");
        }

        // Initialize DatabaseManager
        databaseManager = new DatabaseManager(this, localeManager);
        if (!databaseManager.connect()) {
            // Error message is handled by DatabaseManager.connect()
            // Depending on how critical DB is, might disable plugin
            getLogger().severe("Failed to connect to the database. Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize GuiManager (after other managers it might depend on)
        guiManager = new GuiManager(this);

        // Register commands
        SubastasCommand subastasCommand = new SubastasCommand(this);
        if (getCommand("subastas") != null) {
            getCommand("subastas").setExecutor(subastasCommand);
            getCommand("subastas").setTabCompleter(subastasCommand);
        } else {
            getLogger().severe("Command 'subastas' not found in plugin.yml! Cannot register command.");
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this, guiManager, localeManager), this);

        // Start tasks
        long taskInterval = 20L * configManager.getInt("auction-settings.end-task-interval-seconds", 30); // Default 30 seconds
        auctionEndTask = new AuctionEndTask(this).runTaskTimer(this, 0L, taskInterval);

        String dbStatus = (databaseManager != null && databaseManager.getConnection() != null) ? "OK" : "Error";
        String econStatus = (economyManager != null && economyManager.getEconomy() != null) ? "OK" : "No Provider";
        if (economyManager != null && economyManager.getEconomy() == null && checkVault()) econStatus = "No Provider";
        else if (economyManager == null && !checkVault()) econStatus = "Vault Not Found";


        getLogger().info(localeManager.getRawMessage("logs.plugin-enabled-details",
                "version", getDescription().getVersion(),
                "db_status", dbStatus,
                "econ_status", econStatus
                ));
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (auctionEndTask != null && !auctionEndTask.isCancelled()) {
            auctionEndTask.cancel();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        if (localeManager != null) {
            getLogger().info(localeManager.getRawMessage("logs.plugin-disabled-details", "version", getDescription().getVersion()));
        } else {
            getLogger().info("SubastasPlugin v" + getDescription().getVersion() + " has been disabled!"); // Fallback if localeManager is null
        }
    }

    private boolean checkVault() {
        return getServer().getPluginManager().getPlugin("Vault") != null;
    }

    // setupEconomy() is now part of EconomyManager
    // private boolean setupEconomy() { ... }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public DatabaseManager getDatabaseManager() { // Getter for DatabaseManager
        return databaseManager;
    }

    public GuiManager getGuiManager() { // Getter for GuiManager
        return guiManager;
    }

    // Replaced by getEconomyManager().getEconomy() if direct access is needed,
    // or preferably by using methods in EconomyManager.
    // public Economy getEconomy() {
    //     return economyManager != null ? economyManager.getEconomy() : null;
    // }
}
