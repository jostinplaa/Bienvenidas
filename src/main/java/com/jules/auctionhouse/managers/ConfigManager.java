package com.jules.auctionhouse.managers;

import com.jules.auctionhouse.AuctionHouse;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final AuctionHouse plugin;
    private FileConfiguration config;

    public ConfigManager(AuctionHouse plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.config = plugin.getConfig();
    }

    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("general.prefix", "&6[&e⚡ Subasta&6]&r"));
    }

    public double getMinimumBidIncrement() {
        return config.getDouble("auctions.minimum-bid-increment", 0.05);
    }

    public long getDefaultDuration() {
        return config.getLong("auctions.default-duration", 300);
    }

    public List<String> getBlacklistedItems() {
        return config.getStringList("security.blacklist");
    }

    public double getSellerCommission() {
        // Lógica de VIP se añadirá después
        return config.getDouble("economy.seller-commission", 0.05);
    }
}