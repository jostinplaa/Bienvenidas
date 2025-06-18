package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigManager {

    private final AetherAuctions plugin;
    private FileConfiguration config;

    // General
    private String pluginPrefix;
    private String currencySymbol;
    private String dateFormat;
    private int maxActiveAuctionsPerPlayer;
    private String logLevel;

    // Auction
    private long defaultDurationSeconds;
    private long minDurationSeconds;
    private long maxDurationSeconds;
    private List<Map<String, Object>> availableDurations; // Stores maps like {"label": "1 Hora", "seconds": 3600}
    private boolean creationFeeEnabled;
    private double creationFeeAmount;
    private boolean commissionOnSaleEnabled;
    private double commissionOnSalePercentage;
    private double minBidIncrementAmount;
    private boolean buyNowAllowed;
    private double maxStartPrice;
    private List<String> itemBlacklist;

    // VIP
    private String vipPermission;
    private int vipMaxActiveAuctions;
    private long vipExtendedDurationSeconds;
    private boolean vipNoCreationFee;
    private boolean vipExclusiveFilters; // Added

    // Database
    private String databaseType;
    private String sqliteFileName;

    public ConfigManager(AetherAuctions plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig(); // Creates config.yml if it doesn't exist
        plugin.reloadConfig();      // Reloads the config from disk
        config = plugin.getConfig(); // Get the reloaded config

        // General
        pluginPrefix = config.getString("general.plugin_prefix", "&e&lAetherAuctions &8» ");
        currencySymbol = config.getString("general.currency_symbol", "$");
        dateFormat = config.getString("general.date_format", "dd/MM/yyyy HH:mm");
        maxActiveAuctionsPerPlayer = config.getInt("general.max_active_auctions_per_player", 5);
        logLevel = config.getString("general.log_level", "INFO").toUpperCase();

        // Auction
        defaultDurationSeconds = config.getLong("auction.default_duration_seconds", 86400L);
        minDurationSeconds = config.getLong("auction.min_duration_seconds", 3600L);
        maxDurationSeconds = config.getLong("auction.max_duration_seconds", 604800L);

        this.availableDurations = config.getList("auction.available_durations", Collections.emptyList())
            .stream()
            .map(entry -> {
                if (entry instanceof String) {
                    String[] parts = ((String) entry).split(":", 2);
                    if (parts.length == 2) {
                        try {
                            return Map.of("label", parts[0], "seconds", Long.parseLong(parts[1]));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Formato de duración inválido en config.yml: " + entry);
                            return null;
                        }
                    }
                }
                 plugin.getLogger().warning("Entrada de duración inválida en config.yml: " + entry);
                return null;
            })
            .filter(map -> map != null && map.containsKey("label") && map.containsKey("seconds"))
            .collect(Collectors.toList());
        if (this.availableDurations.isEmpty()) { // Fallback if parsing fails or empty
            this.availableDurations = List.of(Map.of("label", "1 Día", "seconds", 86400L));
             plugin.getLogger().warning("available_durations estaba vacío o mal configurado. Usando valor por defecto: 1 Día:86400");
        }


        creationFeeEnabled = config.getBoolean("auction.creation_fee.enabled", true);
        creationFeeAmount = config.getDouble("auction.creation_fee.amount", 100.0);
        commissionOnSaleEnabled = config.getBoolean("auction.commission_on_sale.enabled", true);
        commissionOnSalePercentage = config.getDouble("auction.commission_on_sale.percentage", 5.0);
        minBidIncrementAmount = config.getDouble("auction.min_bid_increment.amount", 10.0);
        buyNowAllowed = config.getBoolean("auction.buy_now_allowed", true);
        maxStartPrice = config.getDouble("auction.max_start_price", 1000000.0);
        itemBlacklist = config.getStringList("auction.item_blacklist");

        // VIP
        vipPermission = config.getString("vip.permission", "aetherauctions.vip");
        vipMaxActiveAuctions = config.getInt("vip.max_active_auctions", 15);
        vipExtendedDurationSeconds = config.getLong("vip.extended_duration_seconds", 1209600L);
        vipNoCreationFee = config.getBoolean("vip.no_creation_fee", true);
        vipExclusiveFilters = config.getBoolean("vip.exclusive_filters", true); // Added, default true as per example

        // Database
        databaseType = config.getString("database.type", "sqlite");
        sqliteFileName = config.getString("database.sqlite.file_name", "aetherauctions.db");

        plugin.getLogger().info("Configuración cargada.");
    }

    public void reloadConfig() {
        loadConfig(); // plugin.reloadConfig() is called within loadConfig()
    }

    // Getters
    public String getPluginPrefix() { return pluginPrefix; }
    public String getCurrencySymbol() { return currencySymbol; }
    public String getDateFormat() { return dateFormat; }
    public int getMaxActiveAuctionsPerPlayer() { return maxActiveAuctionsPerPlayer; }
    public String getLogLevel() { return logLevel; }

    public long getDefaultDurationSeconds() { return defaultDurationSeconds; }
    public long getMinDurationSeconds() { return minDurationSeconds; }
    public long getMaxDurationSeconds() { return maxDurationSeconds; }
    public List<Map<String, Object>> getAvailableDurations() { return availableDurations; } // Example: Map<"label", "1 Hora", "seconds", 3600L>
    public boolean isCreationFeeEnabled() { return creationFeeEnabled; }
    public double getCreationFeeAmount() { return creationFeeAmount; }
    public boolean isCommissionOnSaleEnabled() { return commissionOnSaleEnabled; }
    public double getCommissionOnSalePercentage() { return commissionOnSalePercentage; }
    public double getMinBidIncrementAmount() { return minBidIncrementAmount; }
    public boolean isBuyNowAllowed() { return buyNowAllowed; }
    public double getMaxStartPrice() { return maxStartPrice; }
    public List<String> getItemBlacklist() { return Collections.unmodifiableList(itemBlacklist); }

    public String getVipPermission() { return vipPermission; }
    public int getVipMaxActiveAuctions() { return vipMaxActiveAuctions; }
    public long getVipExtendedDurationSeconds() { return vipExtendedDurationSeconds; }
    public boolean isVipNoCreationFee() { return vipNoCreationFee; }
    public boolean isVipExclusiveFilters() { return vipExclusiveFilters; } // Added

    public String getDatabaseType() { return databaseType; }
    public String getSqliteFileName() { return sqliteFileName; }
}
