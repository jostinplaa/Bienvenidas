package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class ConfigManager {
    private final AetherAuctions plugin;
    private FileConfiguration config;

    public ConfigManager(AetherAuctions plugin) {
        this.plugin = plugin;
        // loadConfig is called by AetherAuctions main class after this instance is created.
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig(); // Ensure latest changes from disk are loaded
        config = plugin.getConfig();
        plugin.getLogger().info("Configuración cargada/recargada.");
    }

    public void reloadConfig() { // Public method for /subasta admin reload
        loadConfig();
    }


    public String getPluginPrefix() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("plugin_prefix", "&6[&eAetherAuctions&6] &r"));
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getCurrencySymbol() {
        return config.getString("general.currency_symbol", "$");
    }

    public long getDefaultDurationHours() {
        return config.getLong("auction.default_duration_hours", 24);
    }

    public int getMaxActiveAuctionsPerPlayer(Player player) {
        // Placeholder for permission-based limits if needed in future
        return config.getInt("auction.max_active_auctions_per_player", 5);
    }

    public double getMinBidIncrement() {
        return config.getDouble("auction.min_bid_increment", 10.0);
    }

    public boolean isBuyNowAllowed() {
        return config.getBoolean("auction.allow_buy_now", true);
    }

    public double getAuctionCreationFee(Player player) {
        // Placeholder for permission-based fees
        return config.getDouble("auction.creation_fee", 0.0);
    }

    public long getExpirationCheckIntervalSeconds() {
        return config.getLong("auction.expired_check_interval_seconds", 60);
    }

    public List<Material> getItemBlacklist() {
        List<String> materialNames = config.getStringList("auction.item_blacklist");
        if (materialNames == null || materialNames.isEmpty()) {
            return Collections.emptyList();
        }
        return materialNames.stream()
                .map(name -> {
                    try {
                        return Material.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Material inválido en item_blacklist: '" + name + "'. Será ignorado.");
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull) // Ensure no nulls from invalid names
                .collect(Collectors.toList());
    }

    public Material getMainDecorativePaneMaterial() {
        String materialName = config.getString("gui.main_decorative_pane_material", "GRAY_STAINED_GLASS_PANE");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido para 'gui.main_decorative_pane_material': " + materialName + ". Usando GRAY_STAINED_GLASS_PANE.");
            return Material.GRAY_STAINED_GLASS_PANE;
        }
    }

    public Material getDetailsDecorativePaneMaterial() {
        String materialName = config.getString("gui.details_decorative_pane_material", "BLACK_STAINED_GLASS_PANE");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido para 'gui.details_decorative_pane_material': " + materialName + ". Usando BLACK_STAINED_GLASS_PANE.");
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    public int getGuiItemsPerPage() {
        return config.getInt("gui.items_per_page", 36);
    }

    public String getMessagesMissingKeyFormat() {
        return config.getString("messages.missing_key_format", "&cError: Clave '%key%' no encontrada.");
    }

    public int getDeliveredRewardsKeptDays() {
        return config.getInt("pending_rewards.cleanup_days_to_keep", 30);
    }

    public String getRewardDeliveryMethod() {
        return config.getString("rewards.delivery-mode", "auto").toLowerCase();
    }

    public boolean showMessageOnJoinForGuiMode() {
        return config.getBoolean("rewards.message_on_join_for_gui_mode", true);
    }

    public boolean isHistoryEnabled() {
        return config.getBoolean("history.enabled", true);
    }

    public int getHistoryRecordsPerPlayer() {
        return config.getInt("history.records_per_player", 50);
    }

    public int getDefaultHistoryDaysToShowForAdmin() {
        return config.getInt("history.default_days_to_show", 7);
    }

    public boolean isMyAuctionsGuiEnabled() {
        return config.getBoolean("my_auctions_gui.enabled", true);
    }

    public boolean isGuiSoundsEnabled() {
        return config.getBoolean("gui.sounds.enabled", true);
    }

    public boolean isMysteryAuctionsAllowed() {
        return config.getBoolean("auction.allow_mystery_auctions", true);
    }

    // Commission Settings
    public String getCommissionType() {
        return config.getString("auction.commission.type", "flat_percentage").toLowerCase();
    }

    public double getDefaultCommissionPercentage() {
        double percentage = config.getDouble("auction.commission.default_percentage", 5.0);
        return Math.max(0, Math.min(100, percentage));
    }

    public Map<String, Double> getCategoryCommissionPercentagesMap() { // Renamed for clarity, returns Map<String, Double>
        Map<String, Double> categoryCommissions = new HashMap<>();
        ConfigurationSection categorySection = config.getConfigurationSection("auction.commission.categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                if (key.equalsIgnoreCase("DEFAULT_OTHER")) continue;
                // Key is already a string (Material name)
                double percentage = categorySection.getDouble(key);
                categoryCommissions.put(key.toUpperCase(), Math.max(0, Math.min(100, percentage)));
            }
        }
        return categoryCommissions;
    }

    public double getDefaultOtherCategoryCommissionPercentage() {
        return config.getDouble("auction.commission.categories.DEFAULT_OTHER", getDefaultCommissionPercentage());
    }

    public List<Map<String, Object>> getTieredCommissionTiers() {
        List<?> rawList = config.getList("auction.commission.tiers");
        List<Map<String, Object>> typedList = new ArrayList<>();
        if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof Map) {
                    try {
                        Map<?, ?> rawMap = (Map<?, ?>) obj;
                        Map<String, Object> tierMap = new HashMap<>();
                        Object maxPriceObj = rawMap.get("max_price");
                        Object percentageObj = rawMap.get("percentage");

                        if (maxPriceObj instanceof Number && percentageObj instanceof Number) {
                            tierMap.put("max_price", ((Number) maxPriceObj).doubleValue());
                            tierMap.put("percentage", ((Number) percentageObj).doubleValue());
                            typedList.add(tierMap);
                        } else {
                            plugin.getLogger().warning("Skipping invalid tier in auction.commission.tiers (non-numeric max_price or percentage): " + obj.toString());
                        }
                    } catch (Exception e) {
                         plugin.getLogger().warning("Skipping invalid tier structure in auction.commission.tiers: " + obj.toString() + " - Error: " + e.getMessage());
                    }
                } else {
                    plugin.getLogger().warning("Skipping non-map element in auction.commission.tiers: " + obj.toString());
                }
            }
        }
        return typedList;
    }

    // MainAuctionGUI button configurations
    public String getMyActiveAuctionsButtonMaterial(String defaultMaterial) {
        return config.getString("gui.buttons.my_active_auctions.material", defaultMaterial);
    }
    public int getMyActiveAuctionsButtonSlot() {
        return config.getInt("gui.buttons.my_active_auctions.slot", 47);
    }

    public String getPlayerHistoryButtonMaterial(String defaultMaterial) {
        return config.getString("gui.buttons.player_history.material", defaultMaterial);
    }
    public int getPlayerHistoryButtonSlot() {
        return config.getInt("gui.buttons.player_history.slot", 51);
    }

    public String getRewardsButtonMaterial(String defaultMaterial) {
        return config.getString("gui.buttons.rewards.material", defaultMaterial);
    }
    public int getRewardsButtonSlot() {
        return config.getInt("gui.buttons.rewards.slot", 52);
    }
}
