package com.jules.auctionhouse;

import com.jules.auctionhouse.commands.AuctionCommand;
import com.jules.auctionhouse.db.DatabaseManager;
import com.jules.auctionhouse.listeners.ActiveAuctionsListener;
import com.jules.auctionhouse.listeners.MainMenuListener;
import com.jules.auctionhouse.managers.AuctionManager;
import com.jules.auctionhouse.tasks.AuctionEndTask;
import com.jules.auctionhouse.managers.EconomyManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private AuctionManager auctionManager;
    private EconomyManager economyManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        // Setup Managers
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        auctionManager = new AuctionManager(this);
        economyManager = new EconomyManager();
        if (!economyManager.setupEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Registrar comandos
        getCommand("auction").setExecutor(new AuctionCommand(this));
        // getCommand("auctionadmin").setExecutor(new AdminCommand()); // Se añadirá en el futuro

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new MainMenuListener(), this);
        getServer().getPluginManager().registerEvents(new ActiveAuctionsListener(), this);
        getServer().getPluginManager().registerEvents(new AuctionDetailsListener(), this);
        getServer().getPluginManager().registerEvents(new HistoryGUIListener(), this);

        // Iniciar tareas
        new AuctionEndTask(this).runTaskTimer(this, 20L, 20L); // Cada segundo

        getLogger().info("AuctionHouse se ha habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        databaseManager.close();
        getLogger().info("AuctionHouse se ha deshabilitado.");
    }

    public static AuctionHouse getInstance() {
        return instance;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}