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

        // Use a slight delay to ensure everything is loaded and message is not lost
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getDatabaseManager().getPlayerClaims(player.getUniqueId()).isEmpty()) {
                MessageUtil.sendMessage(player, "you-have-items-to-claim");
            }
        }, 20L); // 1 second delay
    }
}
