package dev.jules.proauction.storage;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.model.Auction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack; // Required for Auction.fromMap if it uses ItemStack directly

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class AuctionStorage {

    private final ProAuction plugin;
    private File auctionFile;
    private FileConfiguration auctionConfig;

    public AuctionStorage(ProAuction plugin) {
        this.plugin = plugin;
        this.auctionFile = new File(plugin.getDataFolder(), "auctions.yml");
        loadAuctionsConfig(); // Load or create
    }

    public void loadAuctionsConfig() {
        if (!auctionFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs(); // Ensure directory exists
                auctionFile.createNewFile();
                plugin.getLogger().info("Created auctions.yml");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create auctions.yml!", e);
            }
        }
        auctionConfig = YamlConfiguration.loadConfiguration(auctionFile);
    }

    public void saveAuctionsConfig() {
        try {
            auctionConfig.save(auctionFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save auctions.yml!", e);
        }
    }

    @SuppressWarnings("unchecked") // For casting map from config
    public Map<UUID, Auction> loadActiveAuctions() {
        loadAuctionsConfig(); // Ensure latest is loaded
        Map<UUID, Auction> loadedAuctions = new HashMap<>();
        ConfigurationSection auctionsSection = auctionConfig.getConfigurationSection("auctions");
        if (auctionsSection != null) {
            for (String key : auctionsSection.getKeys(false)) {
                try {
                    // Correct way to get the map for a sub-section
                    ConfigurationSection individualAuctionSection = auctionsSection.getConfigurationSection(key);
                    if (individualAuctionSection == null) continue; // Should not happen if getKeys(false) is used correctly
                    Map<String, Object> auctionData = individualAuctionSection.getValues(false);

                    // Ensure the item stack is properly deserialized if it's a sub-map itself
                    if (auctionData.get("item") instanceof Map) {
                        // It should already be a Map<String, Object> from getValues(false) if Bukkit serialized it that way
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) auctionData.get("item");
                        auctionData.put("item", ItemStack.deserialize(itemMap)); // Deserialize it properly before passing to Auction.fromMap
                    } else if (auctionData.get("item") instanceof ItemStack) {
                        // Already an ItemStack, no action needed, but good to be aware
                    } else {
                         plugin.getLogger().warning("Item data for auction " + key + " is not in a recognizable map format. Skipping item deserialization for this auction or it might fail in Auction.fromMap.");
                    }

                    Auction auction = Auction.fromMap(auctionData);
                    if (auction.isActive()) { // Only load active auctions
                        loadedAuctions.put(auction.getAuctionId(), auction);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load auction with ID: " + key, e);
                }
            }
        }
        plugin.getLogger().info("Loaded " + loadedAuctions.size() + " active auctions from auctions.yml");
        return loadedAuctions;
    }

    public void saveAuction(Auction auction) {
        if (auction == null) return;
        auctionConfig.set("auctions." + auction.getAuctionId().toString(), auction.toMap());
        saveAuctionsConfig();
    }

    public void deleteAuction(UUID auctionId) {
        if (auctionId == null) return;
        auctionConfig.set("auctions." + auctionId.toString(), null);
        saveAuctionsConfig();
    }

    public void saveAllAuctions(Map<UUID, Auction> activeAuctionsMap) {
        // Create a new configuration or clear the old one to avoid stale entries
        auctionConfig = new YamlConfiguration(); // Clears previous in-memory config
        if (activeAuctionsMap != null) {
            for (Auction auction : activeAuctionsMap.values()) {
                // Only save if it's still considered active by the plugin logic,
                // or if we want to save ended auctions for history (not current requirement)
                if (auction.isActive()) {
                    auctionConfig.set("auctions." + auction.getAuctionId().toString(), auction.toMap());
                }
            }
        }
        saveAuctionsConfig();
        plugin.getLogger().info("Saved all active auctions to auctions.yml");
    }
}
