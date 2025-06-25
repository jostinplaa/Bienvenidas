package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final AetherAuctions plugin;

    public PlayerQuitListener(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Clear any pending input states
        plugin.getPlayerInputState().remove(playerUUID);
        plugin.getPlayerTargetAuction().remove(playerUUID);

        // Clear temporary auction creation data
        if (plugin.getGuiManager() != null) { // GUIManager might not be initialized if plugin is disabling
            plugin.getGuiManager().clearCreateAuctionData(playerUUID);
        }

        // Clear states from NewGUIManager // References to NewGUIManager removed
        // if (plugin.getNewGuiManager() != null) {
            // plugin.getNewGuiManager().removePlayerPageState(playerUUID);
        // }

        // Log if needed, e.g., if player was in a specific auction process
        // plugin.getLogger().info("Cleared pending input states for disconnected player: " + player.getName());
    }
}
