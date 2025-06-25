package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final AetherAuctions plugin;
    private final InventoryClickListener inventoryClickListener;

    public PlayerQuitListener(AetherAuctions plugin, InventoryClickListener inventoryClickListener) {
        this.plugin = plugin;
        this.inventoryClickListener = inventoryClickListener;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Clear any pending input states (for old GUI creation if still used)
        // plugin.getPlayerInputState().remove(playerUUID); // Removed, PlayerInputState map is gone from AetherAuctions
        // plugin.getPlayerTargetAuction().remove(playerUUID); // This map was also part of old state system

        // Clear temporary auction creation data from old GUIManager (if GUIManager class still has this method)
        // com.aetherauctions.gui.GUIManager is now static utils, does not hold player specific data.
        // if (plugin.getGuiManager() != null) {  // getGuiManager was removed from AetherAuctions
            // plugin.getGuiManager().clearCreateAuctionData(playerUUID);
        // }

        // Clear states from the new consolidated InventoryClickListener
        if (inventoryClickListener != null) {
            inventoryClickListener.clearPlayerStatesOnQuit(playerUUID);
        }

        // Log if needed, e.g., if player was in a specific auction process
        // plugin.getLogger().info("Cleared pending input states for disconnected player: " + player.getName());
    }
}
