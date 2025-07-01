package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor; // Import ChatColor

import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;

public class ConfigManager {
    private final AetherAuctions plugin;
    private FileConfiguration config;

    public ConfigManager(AetherAuctions plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.getLogger().info("Configuración cargada/recargada.");
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
        // TODO: Implementar lógica de permisos para overrides VIP si se añade
        // if (player.hasPermission("aetherauctions.vip.maxauctions_tier1")) return getVipMaxAuctionsTier1();
        return config.getInt("auction.max_active_auctions_per_player", 5);
    }

    public double getMinBidIncrement() {
        return config.getDouble("auction.min_bid_increment", 10.0);
    }

    public boolean isBuyNowAllowed() {
        return config.getBoolean("auction.allow_buy_now", true);
    }

    public double getCommissionPercentage() {
        double percentage = config.getDouble("auction.commission_fee_percentage", 5.0);
        if (percentage < 0) return 0.0;
        if (percentage > 100) return 100.0;
        return percentage;
    }

    public double getAuctionCreationFee(Player player) {
        // TODO: Implementar lógica de permisos para overrides VIP (ej. sin tarifa)
        // if (player.hasPermission("aetherauctions.vip.no_creation_fee")) return 0.0;
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
                .filter(material -> material != null)
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
        return config.getInt("pending_rewards.cleanup_days_to_keep", 30); // Default to 30 days
    }

    public String getRewardDeliveryMethod() {
        return config.getString("rewards.delivery-mode", "auto").toLowerCase();
    }

    public boolean showMessageOnJoinForGuiMode() {
        return config.getBoolean("rewards.message_on_join_for_gui_mode", true);
    }

    // History Settings
    public boolean isHistoryEnabled() {
        return config.getBoolean("history.enabled", true);
    }

    public int getHistoryRecordsPerPlayer() {
        return config.getInt("history.records_per_player", 50);
    }

    public int getDefaultHistoryDaysToShowForAdmin() {
        return config.getInt("history.default_days_to_show", 7);
    }

    // My Auctions GUI Settings
    public boolean isMyAuctionsGuiEnabled() {
        return config.getBoolean("my_auctions_gui.enabled", true);
    }
}
