package com.jules.auctionmasterelite.listeners;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final AuctionMasterElite plugin;

    public PlayerJoinListener(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player preferences
        plugin.getPlayerSettingsManager().loadPlayer(player.getUniqueId());

        // Check for claims
        checkPlayerClaims(player);
    }

    private void checkPlayerClaims(Player player) {
        // Run the database check asynchronously to avoid lagging the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // This part is off the main thread
            boolean hasItemClaims = !plugin.getDatabaseManager().getPlayerClaims(player.getUniqueId()).isEmpty();
            boolean hasMoneyClaims = !plugin.getDatabaseManager().getPlayerMoneyClaims(player.getUniqueId()).isEmpty();

            if (hasItemClaims || hasMoneyClaims) {
                // Switch back to the main thread to send the message (Bukkit API is not thread-safe)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Also check if player is still online, just in case they logged out immediately
                    if (player.isOnline()) {
                        MessageUtil.sendMessage(player, "you-have-items-to-claim");
                    }
                });
            }
        });
    }
}
