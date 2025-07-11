package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.logging.Level;

public class ConfigManager {
    private final AetherAuctions plugin;
    private FileConfiguration mainConfig; // Para config.yml
    private FileConfiguration guiConfig;
    private FileConfiguration soundsConfig;
    private FileConfiguration commissionsConfig;
    private FileConfiguration rewardsConfig;
    private FileConfiguration historyConfig;
    private FileConfiguration limitsConfig;
    private FileConfiguration announcementsConfig;
    private FileConfiguration vipTiersConfig;

    public ConfigManager(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mainConfig = plugin.getConfig();
        plugin.getLogger().info("config.yml cargado/recargado.");

        // Cargar otros archivos de configuración basados en load_modules
        // Usar getBoolean(mainConfig, ...) ya que mainConfig es el único garantizado al inicio de este método
        if (getBoolean(mainConfig, "load_modules.gui_config", true)) {
            guiConfig = loadConfiguration("gui.yml");
        } else { plugin.getLogger().info("gui.yml no se cargará (deshabilitado en config.yml)."); }

        if (getBoolean(mainConfig, "load_modules.sounds_config", true)) {
            soundsConfig = loadConfiguration("sounds.yml");
        } else { plugin.getLogger().info("sounds.yml no se cargará."); }

        if (getBoolean(mainConfig, "load_modules.commissions_config", true)) {
            commissionsConfig = loadConfiguration("commissions.yml");
        } else { plugin.getLogger().info("commissions.yml no se cargará."); }

        if (getBoolean(mainConfig, "load_modules.rewards_config", true)) {
            rewardsConfig = loadConfiguration("rewards.yml");
        } else { plugin.getLogger().info("rewards.yml no se cargará."); }

        if (getBoolean(mainConfig, "load_modules.history_config", true)) {
            historyConfig = loadConfiguration("history.yml");
        } else { plugin.getLogger().info("history.yml no se cargará."); }

        if (getBoolean(mainConfig, "load_modules.limits_config", true)) {
            limitsConfig = loadConfiguration("limits.yml");
        } else { plugin.getLogger().info("limits.yml no se cargará."); }

        if (getBoolean(mainConfig, "load_modules.announcements_config", true)) {
            announcementsConfig = loadConfiguration("announcements.yml");
        } else { plugin.getLogger().info("announcements.yml no se cargará."); }

        if (getBoolean(mainConfig, "load_modules.vip_tiers_config", true)) {
            vipTiersConfig = loadConfiguration("vip_tiers.yml");
        } else { plugin.getLogger().info("vip_tiers.yml no se cargará."); }

        plugin.getLogger().info("Módulos de configuración adicionales procesados según config.yml.");
    }

    private FileConfiguration loadConfiguration(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
            plugin.getLogger().info(fileName + " no encontrado, creando desde defaults del JAR.");
        }

        YamlConfiguration loadedConfig = YamlConfiguration.loadConfiguration(configFile);

        try (InputStream defaultConfigStream = plugin.getResource(fileName)) {
            if (defaultConfigStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
                loadedConfig.setDefaults(defaultConfig);
                loadedConfig.options().copyDefaults(true);
                loadedConfig.save(configFile);
                plugin.getLogger().info(fileName + " cargado y defaults aplicados/verificados.");
            } else {
                plugin.getLogger().warning("Archivo de configuración por defecto '" + fileName + "' no encontrado en el JAR. No se pudieron aplicar defaults.");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo procesar/guardar " + fileName + " con valores por defecto.", e);
        }
        return loadedConfig;
    }

    public void reloadAllConfigs() {
        loadConfig();
        if (plugin.getSoundManager() != null) {
             plugin.getSoundManager().loadSounds();
        }
        if (plugin.getMessageManager() != null) {
            plugin.getMessageManager().loadMessages();
        }
        plugin.getLogger().info("Todas las configuraciones han sido recargadas.");
    }

    // --- Helpers Genéricos para acceder a FileConfigurations ---
    protected String getString(FileConfiguration fc, String path, String def) {
        return (fc != null) ? fc.getString(path, def) : def;
    }
    protected int getInt(FileConfiguration fc, String path, int def) {
        return (fc != null) ? fc.getInt(path, def) : def;
    }
    protected double getDouble(FileConfiguration fc, String path, double def) {
        return (fc != null) ? fc.getDouble(path, def) : def;
    }
    protected long getLong(FileConfiguration fc, String path, long def) {
        return (fc != null) ? fc.getLong(path, def) : def;
    }
    protected boolean getBoolean(FileConfiguration fc, String path, boolean def) {
        return (fc != null) ? fc.getBoolean(path, def) : def;
    }
    protected List<String> getStringList(FileConfiguration fc, String path) {
        return (fc != null) ? fc.getStringList(path) : Collections.emptyList();
    }
    protected ConfigurationSection getConfigurationSection(FileConfiguration fc, String path) {
        return (fc != null) ? fc.getConfigurationSection(path) : null;
    }
    protected List<?> getList(FileConfiguration fc, String path) {
        return (fc != null) ? fc.getList(path) : Collections.emptyList();
    }
    private Material getMaterial(FileConfiguration fc, String path, String defaultMaterialName) {
        String materialName = getString(fc, path, defaultMaterialName);
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido para '" + path + "' en el archivo de config correspondiente: " + materialName + ". Usando " + defaultMaterialName + ".");
            return Material.valueOf(defaultMaterialName.toUpperCase());
        }
    }

    // --- Getters para config.yml (mainConfig) ---
    public String getPluginPrefix() {
        return ChatColor.translateAlternateColorCodes('&', getString(mainConfig, "plugin_settings.prefix", "&6[&eAetherAuctions&6] &r"));
    }
    public String getLanguageFile() {
        return getString(mainConfig, "plugin_settings.language_file", "messages.yml");
    }
    public long getAuctionExpirationCheckIntervalSeconds() {
        return getLong(mainConfig, "plugin_settings.auction_expiration_check_interval_seconds", 60);
    }
    public String getDatabaseType() {
        return getString(mainConfig, "database.type", "sqlite");
    }
    public String getSQLiteFileName() {
        return getString(mainConfig, "database.sqlite_filename", "auctions_data.db");
    }
    public String getCurrencySymbol() {
        return getString(mainConfig, "economy.currency_symbol", "$");
    }
    public boolean isCommandSuggestionsEnabled() {
        return getBoolean(mainConfig, "command_suggestions.enable", true);
    }
    public double getCommandSuggestionSimilarityThreshold() {
        return getDouble(mainConfig, "command_suggestions.similarity_threshold", 0.75);
    }
    public boolean isVerboseLoggingEnabled() {
        return getBoolean(mainConfig, "logging_debug.verbose_console_logging", false);
    }
    public boolean isMyAuctionsGuiIntegrationEnabled() { // Movido a mainConfig según diseño
        return getBoolean(mainConfig, "my_auctions_gui_integration.enabled", true);
    }
    public boolean isMysteryAuctionsAllowed() { // Movido a mainConfig según diseño
        return getBoolean(mainConfig, "auctions.behavior.allow_mystery_auctions", true);
    }
     public long getDefaultDurationHours() { // Movido a mainConfig según diseño
        return getLong(mainConfig, "auctions.behavior.default_duration_hours", 24);
    }
    public boolean isBuyNowAllowed() { // Movido a mainConfig según diseño
        return getBoolean(mainConfig, "auctions.behavior.allow_buy_now", true);
    }
    public double getMinBidIncrement() { // Movido a mainConfig según diseño
        return getDouble(mainConfig, "auctions.behavior.min_bid_increment", 10.0);
    }

    // --- Getters para limits.yml (limitsConfig) ---
    public int getMaxActiveAuctionsPerPlayer() {
        return getInt(limitsConfig, "auctions.max_active_per_player", 5);
    }
    public List<Material> getItemBlacklist() {
        List<String> materialNames = getStringList(limitsConfig, "items.blacklist");
        if (materialNames.isEmpty()) return Collections.emptyList();
        return materialNames.stream()
                .map(name -> {
                    try { return Material.valueOf(name.toUpperCase()); }
                    catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Material inválido en limits.yml item_blacklist: '" + name + "'. Será ignorado.");
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
    public long getAuctionCreationCooldownSeconds() {
         return getLong(limitsConfig, "auctions.creation_cooldown_seconds", 60);
    }

    // --- Getters para commissions.yml (commissionsConfig) ---
    public double getAuctionCreationFee() {
        return getDouble(commissionsConfig, "auction_creation_fee", 0.0);
    }
    public String getCommissionType() {
        return getString(commissionsConfig, "commission_system.type", "flat_percentage").toLowerCase();
    }
    public double getDefaultCommissionPercentage() {
        double percentage = getDouble(commissionsConfig, "commission_system.default_percentage", 5.0);
        return Math.max(0, Math.min(100, percentage));
    }
    public Map<String, Double> getCategoryCommissionPercentagesMap() {
        Map<String, Double> categoryCommissions = new HashMap<>();
        ConfigurationSection cs = getConfigurationSection(commissionsConfig, "commission_system.categories");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                if (key.equalsIgnoreCase("DEFAULT_OTHER")) continue;
                double percentage = cs.getDouble(key);
                categoryCommissions.put(key.toUpperCase(), Math.max(0, Math.min(100, percentage)));
            }
        }
        return categoryCommissions;
    }
    public double getDefaultOtherCategoryCommissionPercentage() {
        return getDouble(commissionsConfig, "commission_system.categories.DEFAULT_OTHER", getDefaultCommissionPercentage());
    }
    public List<Map<String, Object>> getTieredCommissionTiers() {
        List<?> rawList = getList(commissionsConfig, "commission_system.tiers");
        List<Map<String, Object>> typedList = new ArrayList<>();
         if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rawMap = (Map<String, Object>) obj;
                        Map<String, Object> tierMap = new HashMap<>();
                        Object maxPriceObj = rawMap.get("max_price");
                        Object percentageObj = rawMap.get("percentage");

                        if (maxPriceObj instanceof Number && percentageObj instanceof Number) {
                            tierMap.put("max_price", ((Number) maxPriceObj).doubleValue());
                            tierMap.put("percentage", ((Number) percentageObj).doubleValue());
                            typedList.add(tierMap);
                        } else {
                            plugin.getLogger().warning("Omitiendo tramo inválido en commission_system.tiers (max_price o percentage no numéricos): " + obj.toString());
                        }
                    } catch (ClassCastException e){
                         plugin.getLogger().warning("Error de casteo en tramo de comisión: " + obj.toString() + " - " + e.getMessage());
                    }
                } else {
                    plugin.getLogger().warning("Omitiendo elemento no-mapa en commission_system.tiers: " + obj.toString());
                }
            }
        }
        return typedList;
    }

    // --- Getters para gui.yml (guiConfig) ---
    public int getGuiItemsPerPage() {
        return getInt(guiConfig, "general_appearance.items_per_page", 36);
    }
    public Material getMainDecorativePaneMaterial() {
        return getMaterial(guiConfig, "general_appearance.main_decorative_pane", "GRAY_STAINED_GLASS_PANE");
    }
    public Material getDetailsDecorativePaneMaterial() {
        return getMaterial(guiConfig, "general_appearance.details_decorative_pane", "BLACK_STAINED_GLASS_PANE");
    }
    public Material getNoResultsItemMaterial() {
        return getMaterial(guiConfig, "general_appearance.no_results_item", "GLASS_BOTTLE");
    }
    public int getMainAuctionHouseRefreshIntervalSeconds() {
        return getInt(guiConfig, "main_auction_house.auto_refresh_on_count_change_interval_seconds", 10);
    }
    public Material getButtonMaterial(String guiName, String buttonKey, String defaultMaterialName) {
        return getMaterial(guiConfig, guiName + ".buttons." + buttonKey + ".material", defaultMaterialName);
    }
    public int getButtonSlot(String guiName, String buttonKey, int defaultSlot) {
        return getInt(guiConfig, guiName + ".buttons." + buttonKey + ".slot", defaultSlot);
    }
    public int getMysteryLotMaxItems() { // Lee de gui.yml
        return getInt(guiConfig, "prepare_mystery_lot_gui.max_items_in_lot", 36);
    }

    // --- Getters para sounds.yml (soundsConfig) ---
    public boolean isGuiSoundsEnabled() {
        return getBoolean(soundsConfig, "enabled", true);
    }
    public ConfigurationSection getSoundsConfigSection() {
        return getConfigurationSection(soundsConfig, null);
    }

    // --- Getters para rewards.yml (rewardsConfig) ---
    public String getRewardDeliveryMethod() {
        return getString(rewardsConfig, "delivery_mode", "auto").toLowerCase();
    }
    public boolean notifyOnJoinIfGuiModeRewards() {
        return getBoolean(rewardsConfig, "notify_on_join_if_gui_mode", true);
    }
    public int getDeliveredRewardsCleanupDays() {
        return getInt(rewardsConfig, "cleanup_delivered_rewards.keep_for_days", 30);
    }
    public boolean isRewardCleanupEnabled() {
        return getBoolean(rewardsConfig, "cleanup_delivered_rewards.enabled", true);
    }

    // --- Getters para history.yml (historyConfig) ---
    public boolean isHistoryEnabled() {
        return getBoolean(historyConfig, "enabled", true);
    }
    public int getMaxHistoryRecordsPerPlayer() {
        return getInt(historyConfig, "max_records_per_player", 50);
    }
    public int getAdminHistoryDefaultDaysToShow() {
        return getInt(historyConfig, "admin_view_default_days_past", 7);
    }

    // --- Getters para Announcements (announcements.yml) ---
    public boolean isAnnouncementsEnabled() {
        return getBoolean(announcementsConfig, "enabled", false);
    }
    public int getAnnouncementCheckIntervalSeconds() {
        return getInt(announcementsConfig, "check_interval_seconds", 300);
    }
    public double getMinPriceForAnnouncement() {
        return getDouble(announcementsConfig, "min_value_for_announcement", 10000.0);
    }
    public String getAnnouncementValueTypeToCheck() {
        return getString(announcementsConfig, "value_type_to_check", "START_PRICE");
    }
    public boolean announceAllNewAuctions() {
        return getBoolean(announcementsConfig, "announce_all_new_auctions", false);
    }
    public String getAnnouncementMessageFormat() {
        return getString(announcementsConfig, "message_format", "&e[Subastas Mundiales] &6%seller_name% &eha listado &f%item_amount%x %item_name%&e! Puja inicial: &a%start_price%%currency_symbol%&e.");
    }
    public String getBuyNowNotAvailableTextForAnnouncements() {
        return getString(announcementsConfig, "buy_now_not_available_text", "N/A");
    }

    // --- Getters para VIP Tiers (vip_tiers.yml) ---
    public boolean isVipTiersEnabled() {
        return getBoolean(vipTiersConfig, "enabled", true);
    }
    public ConfigurationSection getDefaultPlayerBenefits() {
        return getConfigurationSection(vipTiersConfig, "default_player_benefits");
    }
    public List<Map<?, ?>> getVipTiers() {
        if (vipTiersConfig == null) return Collections.emptyList();
        return vipTiersConfig.getMapList("tiers");
    }
}
```
