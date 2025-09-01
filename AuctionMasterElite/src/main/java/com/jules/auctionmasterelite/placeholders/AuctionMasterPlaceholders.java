package com.jules.auctionmasterelite.placeholders;

import com.jules.auctionmasterelite.AuctionMasterElite;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class AuctionMasterPlaceholders extends PlaceholderExpansion {

    private final AuctionMasterElite plugin;

    public AuctionMasterPlaceholders(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "auctionmaster";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Jules";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // This is required on PAPI 2.10.0+
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("active_auctions")) {
            return String.valueOf(plugin.getAuctionManager().getActiveAuctions().size());
        }

        return null; // Placeholder is unknown
    }
}
