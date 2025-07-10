package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.ChatColor;

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
        // loadConfig() es llamado por AetherAuctions después de que esta instancia es creada.
    }

    public void loadConfig() {
        plugin.saveDefaultConfig(); // Asegura que config.yml exista en la carpeta del plugin
        plugin.reloadConfig();    // Recarga la configuración desde el disco
        config = plugin.getConfig(); // Obtiene la instancia de FileConfiguration cargada
        plugin.getLogger().info("Configuración cargada/recargada.");
    }

    public void reloadConfig() {
        loadConfig();
    }

    // --- General Plugin Settings ---
    public String getPluginPrefix() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("general.plugin_prefix", "&6[&eAetherAuctions&6] &r"));
    }

    public String getCurrencySymbol() {
        return config.getString("general.currency_symbol", "$");
    }

    public String getLanguageFile() {
        return config.getString("general.language_file", "messages.yml");
    }

    // --- Database Configuration ---
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getSQLiteFileName() {
        return config.getString("database.sqlite_filename", "auctions_data.db");
    }

    // --- Auction Core Mechanics ---
    public long getDefaultDurationHours() {
        return config.getLong("auctions.behavior.default_duration_hours", 24);
    }

    public boolean isBuyNowAllowed() {
        return config.getBoolean("auctions.behavior.allow_buy_now", true);
    }

    public boolean isMysteryAuctionsAllowed() {
        return config.getBoolean("auctions.behavior.allow_mystery_auctions", true);
    }

    public double getMinBidIncrement() {
        return config.getDouble("auctions.behavior.min_bid_increment", 10.0);
    }

    public long getExpirationCheckIntervalSeconds() {
        return config.getLong("auctions.behavior.expired_check_interval_seconds", 60);
    }

    // --- Auction Limits and Restrictions ---
    public int getMaxActiveAuctionsPerPlayer() {
        // Futura mejora: Límites basados en permisos. Por ahora, es global.
        return config.getInt("auctions.limits_and_restrictions.max_active_per_player", 5);
    }

    public List<Material> getItemBlacklist() {
        List<String> materialNames = config.getStringList("auctions.limits_and_restrictions.item_blacklist");
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
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --- Auction Fees and Commissions ---
    public double getAuctionCreationFee() {
        return config.getDouble("auctions.fees_and_commissions.creation_fee", 0.0);
    }

    public String getCommissionType() {
        return config.getString("auctions.fees_and_commissions.commission.type", "flat_percentage").toLowerCase();
    }

    public double getDefaultCommissionPercentage() {
        double percentage = config.getDouble("auctions.fees_and_commissions.commission.default_percentage", 5.0);
        return Math.max(0, Math.min(100, percentage)); // Clamp entre 0 y 100
    }

    public Map<String, Double> getCategoryCommissionPercentagesMap() {
        Map<String, Double> categoryCommissions = new HashMap<>();
        ConfigurationSection categorySection = config.getConfigurationSection("auctions.fees_and_commissions.commission.categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                if (key.equalsIgnoreCase("DEFAULT_OTHER")) continue;
                double percentage = categorySection.getDouble(key);
                categoryCommissions.put(key.toUpperCase(), Math.max(0, Math.min(100, percentage)));
            }
        }
        return categoryCommissions;
    }

    public double getDefaultOtherCategoryCommissionPercentage() {
        return config.getDouble("auctions.fees_and_commissions.commission.categories.DEFAULT_OTHER", getDefaultCommissionPercentage());
    }

    public List<Map<String, Object>> getTieredCommissionTiers() {
        List<?> rawList = config.getList("auctions.fees_and_commissions.commission.tiers");
        List<Map<String, Object>> typedList = new ArrayList<>();
        if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked") // Bukkit API for getList returns List<?>
                        Map<String, Object> rawMap = (Map<String, Object>) obj;
                        Map<String, Object> tierMap = new HashMap<>();
                        Object maxPriceObj = rawMap.get("max_price");
                        Object percentageObj = rawMap.get("percentage");

                        if (maxPriceObj instanceof Number && percentageObj instanceof Number) {
                            tierMap.put("max_price", ((Number) maxPriceObj).doubleValue());
                            tierMap.put("percentage", ((Number) percentageObj).doubleValue());
                            typedList.add(tierMap);
                        } else {
                            plugin.getLogger().warning("Omitiendo tramo inválido en auction.commission.tiers (max_price o percentage no numéricos): " + obj.toString());
                        }
                    } catch (ClassCastException e){
                        plugin.getLogger().warning("Error de casteo en tramo de comisión: " + obj.toString() + " - " + e.getMessage());
                    }
                } else {
                    plugin.getLogger().warning("Omitiendo elemento no-mapa en auction.commission.tiers: " + obj.toString());
                }
            }
        }
        return typedList;
    }

    // --- GUI Settings ---
    public int getGuiItemsPerPage() {
        return config.getInt("gui.general_appearance.items_per_page", 36);
    }

    public Material getMainDecorativePaneMaterial() {
        return getMaterial("gui.general_appearance.main_decorative_pane", "GRAY_STAINED_GLASS_PANE");
    }

    public Material getDetailsDecorativePaneMaterial() {
        return getMaterial("gui.general_appearance.details_decorative_pane", "BLACK_STAINED_GLASS_PANE");
    }

    public Material getNoResultsItemMaterial() {
        return getMaterial("gui.general_appearance.no_results_item", "GLASS_BOTTLE");
    }

    public int getMainAuctionHouseRefreshIntervalSeconds() {
        return config.getInt("gui.main_auction_house.refresh_interval_seconds", 10);
    }

    // Helper para obtener material con fallback
    private Material getMaterial(String path, String defaultMaterialName) {
        String materialName = config.getString(path, defaultMaterialName);
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido para '" + path + "': " + materialName + ". Usando " + defaultMaterialName + ".");
            return Material.valueOf(defaultMaterialName.toUpperCase());
        }
    }

    // Helper para obtener material de botón
    public Material getButtonMaterial(String guiName, String buttonKey, String defaultMaterial) {
        return getMaterial("gui." + guiName + ".buttons." + buttonKey + ".material", defaultMaterial);
    }

    // Helper para obtener slot de botón
    public int getButtonSlot(String guiName, String buttonKey, int defaultSlot) {
        return config.getInt("gui." + guiName + ".buttons." + buttonKey + ".slot", defaultSlot);
    }

    // --- GUI Sounds ---
    public boolean isGuiSoundsEnabled() {
        return config.getBoolean("sounds.enabled", true);
    }
    // SoundManager leerá la sub-sección 'sounds' directamente.

    // --- Rewards Handling ---
    public String getRewardDeliveryMethod() {
        return config.getString("rewards.delivery_mode", "auto").toLowerCase();
    }

    public boolean notifyOnJoinIfGuiModeRewards() {
        return config.getBoolean("rewards.notify_on_join_if_gui_mode", true);
    }

    public int getDeliveredRewardsCleanupDays() {
        return config.getInt("rewards.cleanup_delivered_rewards_after_days", 30);
    }

    // --- Auction History ---
    public boolean isHistoryEnabled() {
        return config.getBoolean("history.enabled", true);
    }

    public int getMaxHistoryRecordsPerPlayer() {
        return config.getInt("history.max_records_per_player", 50);
    }

    public int getAdminHistoryDefaultDaysToShow() {
        return config.getInt("history.admin_view_default_days_past", 7);
    }

    // --- My Auctions GUI ---
    public boolean isMyAuctionsGuiIntegrationEnabled() {
        return config.getBoolean("my_auctions_gui_integration.enabled", true);
    }

    // --- Logging & Debugging ---
    public boolean isVerboseLoggingEnabled() {
        return config.getBoolean("logging_debug.verbose_console_logging", false);
    }

    // --- PrepareMysteryLotGUI specific ---
    public int getMysteryLotMaxItems() {
        return config.getInt("gui.prepare_mystery_lot_gui.max_items", 36);
    }
}
