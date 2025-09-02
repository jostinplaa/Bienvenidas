package com.jules.auctionmasterelite;

import com.jules.auctionmasterelite.managers.AuctionManager;
import com.jules.auctionmasterelite.managers.DatabaseManager;
import com.jules.auctionmasterelite.managers.DiscordManager;
import com.jules.auctionmasterelite.managers.EconomyManager;
import com.jules.auctionmasterelite.managers.ConfigManager;
import com.jules.auctionmasterelite.gui.claims.ClaimMenu;
import com.jules.auctionmasterelite.managers.PlayerInputManager;
import com.jules.auctionmasterelite.util.MessageUtil;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.logging.Level;

public final class AuctionMasterElite extends JavaPlugin {

    private AuctionManager auctionManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private PlayerInputManager playerInputManager;
    private LuckPerms luckPerms;
    private DiscordManager discordManager;
    private ConfigManager configManager;
    private final Map<UUID, ClaimMenu> claimMenus = new HashMap<>();

    @Override
    public void onEnable() {
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.auctionManager = new AuctionManager(this);
        this.economyManager = new EconomyManager();
        this.playerInputManager = new com.jules.auctionmasterelite.managers.PlayerInputManager();
        if (!economyManager.setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault not found! Disabling economy features.");
            // We can choose to disable the plugin or just run without economy features.
            // For now, we'll just log the error.
        }

        // Connect to the database
        try {
            databaseManager.connect();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to connect to the database! Disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Start the auction ticker
        startAuctionTicker();

        // Register commands
        getCommand("auction").setExecutor(new com.jules.auctionmasterelite.commands.AuctionCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new com.jules.auctionmasterelite.gui.GUIListener(), this);
        getServer().getPluginManager().registerEvents(new com.jules.auctionmasterelite.listeners.PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.jules.auctionmasterelite.listeners.PlayerJoinListener(this), this);

        // Register PlaceholderAPI expansion
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.jules.auctionmasterelite.placeholders.AuctionMasterPlaceholders(this).register();
            getLogger().info("Successfully registered PlaceholderAPI expansion.");
        }

        // Setup LuckPerms
        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
            getLogger().info("Successfully hooked into LuckPerms.");
        }

        // Setup DiscordSRV
        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            this.discordManager = new com.jules.auctionmasterelite.managers.DiscordManager(this);
            getLogger().info("Successfully hooked into DiscordSRV.");
        }

        // Load messages
        MessageUtil.load(this);

        getLogger().info("AuctionMasterElite has been enabled!");
    }

    @Override
    public void onDisable() {
        // Disconnect from the database
        databaseManager.disconnect();

        getLogger().info("AuctionMasterElite has been disabled!");
    }

    private void startAuctionTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                auctionManager.tick();
            }
        }.runTaskTimer(this, 0L, 20L); // Run every second (20 ticks)
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PlayerInputManager getPlayerInputManager() {
        return playerInputManager;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Map<UUID, ClaimMenu> getClaimMenus() {
        return claimMenus;
    }
}
