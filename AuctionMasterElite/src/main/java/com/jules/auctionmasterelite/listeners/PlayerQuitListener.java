package com.jules.auctionmasterelite.listeners;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to handle player quit events for resource cleanup.
 */
public class PlayerQuitListener implements Listener {

    private final AuctionMasterElite plugin;

    public PlayerQuitListener(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Unload player preferences from the cache to save memory and persist any changes.
        plugin.getPlayerSettingsManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}
