package com.aetherauctions.listeners;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.model.Auction;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.listener.InventoryClickListener; // Ensure this is the correct listener path

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.Bukkit;

import java.util.UUID;

public class PlayerChatListener implements Listener {
    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final MessageManager msgManager;
    private final InventoryClickListener inventoryClickListener;

    public PlayerChatListener(AetherAuctions plugin, InventoryClickListener invListener) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager();
        this.msgManager = plugin.getMessageManager();
        this.inventoryClickListener = invListener;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (inventoryClickListener.isPlayerPendingBid(playerId)) {
            event.setCancelled(true);
            UUID auctionId = inventoryClickListener.getAndRemovePlayerPendingBidAuctionId(playerId);

            if (auctionId == null) {
                plugin.getLogger().warning("Player " + player.getName() + " was pending bid input (NewGUI), but no auction ID was found.");
                msgManager.sendMessage(player, "internal_error");
                return;
            }

            String message = event.getMessage();
            try {
                double bidAmount = Double.parseDouble(message);
                final UUID finalAuctionId = auctionId;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Auction auction = auctionManager.getAuctionById(finalAuctionId);

                    if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
                        msgManager.sendMessage(player, "auction_bid_error_not_active");
                        return;
                    }

                    // AuctionManager.placeBid will handle all internal validations and messaging
                    if (!auctionManager.placeBid(player, finalAuctionId, bidAmount)) {
                        // Optional: Could re-open GUI on failure if desired, but AuctionManager should send feedback.
                        // Auction failedAuction = auctionManager.getAuctionById(finalAuctionId);
                        // if (failedAuction != null && inventoryClickListener != null) {
                        //    com.aetherauctions.gui.GUIManager.openAuctionInfoGui(player, failedAuction, inventoryClickListener.getPlayerReturnPageMap().getOrDefault(playerId, 0));
                        // }
                    }
                });
            } catch (NumberFormatException e) {
                msgManager.sendMessage(player, "chat_bid_error_invalid_number", "%input%", message);
            }
        }
        // No other chat input states are handled by this listener anymore.
        // Old GUIManager states (AWAITING_DURATION, etc.) were removed.
    }
}
