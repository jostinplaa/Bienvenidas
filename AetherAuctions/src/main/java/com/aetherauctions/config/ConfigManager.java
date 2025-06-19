package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList; // Added for mutable list

public class ConfigManager {

    private final AetherAuctions plugin;
    private FileConfiguration config;

    // Helper pattern for duration parsing
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])");

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

        List<String> durationStrings = config.getStringList("auction.available_durations");
        if (durationStrings == null || durationStrings.isEmpty()) {
            plugin.getLogger().warning("auction.available_durations no encontrado o vacío en config.yml. Usando valores por defecto.");
            this.availableDurations = new ArrayList<>(); // Ensure it's mutable for the default
            this.availableDurations.add(new java.util.HashMap<>(Map.of("label", "1 Día", "seconds", 86400L)));
        } else {
            this.availableDurations = durationStrings.stream()
                .map(this::parseDurationConfigEntry)
                .filter(map -> map != null && map.containsKey("label") && map.containsKey("seconds"))
                .collect(Collectors.toList());
        }

        if (this.availableDurations.isEmpty()) { // Fallback if all parsing fails
            plugin.getLogger().warning("Todas las entradas de available_durations eran inválidas. Usando valor por defecto: 1 Día:86400");
            this.availableDurations.add(new java.util.HashMap<>(Map.of("label", "1 Día", "seconds", 86400L)));
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

    private Map<String, Object> parseDurationConfigEntry(String durationString) {
        if (durationString == null || durationString.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(durationString.trim().toLowerCase());
        if (matcher.matches()) {
            try {
                long value = Long.parseLong(matcher.group(1));
                char unit = matcher.group(2).charAt(0);
                long seconds;
                switch (unit) {
                    case 's':
                        seconds = value;
                        break;
                    case 'm':
                        seconds = value * 60;
                        break;
                    case 'h':
                        seconds = value * 3600;
                        break;
                    case 'd':
                        seconds = value * 86400;
                        break;
                    default:
                        plugin.getLogger().warning("Unidad de duración desconocida '" + unit + "' en la entrada: " + durationString);
                        return null;
                }
                // Using the original string as label for simplicity, can be enhanced
                return new java.util.HashMap<>(Map.of("label", durationString, "seconds", seconds));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Número inválido en la entrada de duración: " + durationString);
                return null;
            }
        } else {
            plugin.getLogger().warning("Formato de duración inválido en la entrada: " + durationString + ". Use formato como '5m', '1h', '3d'.");
            return null;
        }
    }

    public long getDefaultDurationSeconds() { return defaultDurationSeconds; }
    public long getMinDurationSeconds() { return minDurationSeconds; }
    public long getMaxDurationSeconds() { return maxDurationSeconds; }
    public List<Map<String, Object>> getAvailableDurations() { return Collections.unmodifiableList(availableDurations); }
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
