package dev.jules.proauction;

import dev.jules.proauction.model.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class AuctionManager {

    private final ProAuction plugin;

    public AuctionManager(ProAuction plugin) {
        this.plugin = plugin;
    }

    public void checkActiveAuctions() {
        // Iterate over a copy to avoid ConcurrentModificationException
        for (Auction auction : new ArrayList<>(plugin.getActiveAuctions().values())) {
            if (auction.isActive() && System.currentTimeMillis() >= auction.getEndTimeMillis()) {
                processAuctionEnd(auction);
            }
        }
    }

    private void processAuctionEnd(Auction auction) {
        auction.setActive(false); // Mark as inactive first
        plugin.updateAuctionInStorage(auction); // Save inactive state

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
        OfflinePlayer winner = null;
        if (auction.getHighestBidderUuid() != null) {
            winner = Bukkit.getOfflinePlayer(auction.getHighestBidderUuid());
        }

        plugin.logInfo("Processing end of auction ID: " + auction.getAuctionId().toString().substring(0,8) + " for item: " + getItemName(auction.getItem()));

        if (winner != null) {
            Player onlineWinner = winner.getPlayer(); // Check if winner is online

            if (onlineWinner == null) {
                plugin.logInfo("Auction " + auction.getAuctionId() + " winner " + winner.getName() + " is offline. Item returning to seller.");
                returnItemToSeller(seller, auction, "Winner was offline. Auction voided.");
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // V1.1 Logic:
            // 1. Winner online (checked above)
            // 2. Check money
            if (!ProAuction.hasEnough(winner, auction.getCurrentBid())) {
                plugin.logInfo("Auction " + auction.getAuctionId() + " winner " + winner.getName() + " does not have enough funds (" + ProAuction.format(auction.getCurrentBid()) + "). Item returning to seller.");
                notifyPlayer(onlineWinner, ChatColor.RED + "You won the auction for " + getItemName(auction.getItem()) + " but could not afford " + ProAuction.format(auction.getCurrentBid()) + ".");
                returnItemToSeller(seller, auction, "Winner " + winner.getName() + " could not afford the final bid. Auction voided.");
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // 3. Seller payment (Vault handles offline)
            // 4. Withdraw from winner
            if (!ProAuction.withdrawMoney(winner, auction.getCurrentBid())) {
                plugin.logSevere("CRITICAL: Failed to withdraw " + ProAuction.format(auction.getCurrentBid()) + " from winner " + winner.getName() + " for auction " + auction.getAuctionId() + " even after hasEnough check passed. Item returning to seller.");
                notifyPlayer(onlineWinner, ChatColor.RED + "Failed to withdraw funds for auction " + getItemName(auction.getItem()) + ". Please contact an admin.");
                returnItemToSeller(seller, auction, "Failed to withdraw funds from winner " + winner.getName() + ". Auction voided.");
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // 5. Deposit to seller
            if (!ProAuction.depositMoney(seller, auction.getCurrentBid())) {
                plugin.logSevere("CRITICAL: Failed to deposit " + ProAuction.format(auction.getCurrentBid()) + " to seller " + seller.getName() + " for auction " + auction.getAuctionId() + ". Attempting to refund winner.");
                if (!ProAuction.depositMoney(winner, auction.getCurrentBid())) { // Attempt to refund winner
                    plugin.logSevere("CRITICAL: FAILED TO REFUND WINNER " + winner.getName() + " for auction " + auction.getAuctionId() + ". THIS IS A DUPE/LOSS SCENARIO. Manual intervention required.");
                }
                notifyPlayer(onlineWinner, ChatColor.RED + "There was an error paying the seller for auction " + getItemName(auction.getItem()) + ". Your bid has been refunded. Please contact an admin.");
                returnItemToSeller(seller, auction, "Failed to deposit funds to you for auction " + getItemName(auction.getItem()) + ". Winner was refunded. Auction voided.");
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // 6. Give item to online winner
            if (onlineWinner.getInventory().addItem(auction.getItem().clone()).isEmpty()) { // Success
                plugin.logInfo("Auction " + auction.getAuctionId() + " for " + getItemName(auction.getItem()) + " sold to " + winner.getName() + " for " + ProAuction.format(auction.getCurrentBid()));
                notifyPlayer(seller, ChatColor.GREEN + "Your auction for " + getItemName(auction.getItem()) + " sold to " + winner.getName() + " for " + ProAuction.format(auction.getCurrentBid()) + ".");
                notifyPlayer(onlineWinner, ChatColor.GREEN + "You won the auction for " + getItemName(auction.getItem()) + " for " + ProAuction.format(auction.getCurrentBid()) + "! The item has been added to your inventory.");
                plugin.broadcastMessage(ChatColor.YELLOW + getItemName(auction.getItem()) + " was sold to " + winner.getName() + " for " + ProAuction.format(auction.getCurrentBid()) + "!");
            } else { // Inventory full
                plugin.logInfo("Auction " + auction.getAuctionId() + " winner " + winner.getName() + "'s inventory was full. Rolling back transaction.");
                notifyPlayer(onlineWinner, ChatColor.RED + "You won the auction for " + getItemName(auction.getItem()) + ", but your inventory is full! The transaction has been voided.");

                // Rollback: refund winner, take back from seller (if possible, though Vault doesn't have "take" from offline easily)
                if (!ProAuction.depositMoney(winner, auction.getCurrentBid())) {
                     plugin.logSevere("CRITICAL: FAILED TO REFUND WINNER " + winner.getName() + " during inventory full rollback for auction " + auction.getAuctionId());
                }
                if(!ProAuction.withdrawMoney(seller, auction.getCurrentBid())) {
                    plugin.logSevere("CRITICAL: FAILED TO RECLAIM FUNDS FROM SELLER " + seller.getName() + " during inventory full rollback for auction " + auction.getAuctionId());
                }
                returnItemToSeller(seller, auction, "Winner " + winner.getName() + "'s inventory was full. Auction voided, funds reversed.");
            }
        } else { // No winner
            plugin.logInfo("Auction " + auction.getAuctionId() + " for " + getItemName(auction.getItem()) + " by " + seller.getName() + " ended without bids.");
            returnItemToSeller(seller, auction, "Your auction ended without bids.");
        }
        plugin.removeAuction(auction.getAuctionId());
    }

    private void returnItemToSeller(OfflinePlayer seller, Auction auction, String reason) {
        Player onlineSeller = seller.getPlayer();
        ItemStack itemToReturn = auction.getItem().clone();
        if (onlineSeller != null && onlineSeller.isOnline()) {
            if (onlineSeller.getInventory().addItem(itemToReturn).isEmpty()) {
                notifyPlayer(onlineSeller, ChatColor.YELLOW + reason + " The item " + getItemName(itemToReturn) + " has been returned to your inventory.");
            } else {
                onlineSeller.getWorld().dropItemNaturally(onlineSeller.getLocation(), itemToReturn);
                notifyPlayer(onlineSeller, ChatColor.YELLOW + reason + " Your inventory was full, so the item " + getItemName(itemToReturn) + " was dropped at your feet.");
                plugin.logInfo("Returned item " + getItemName(itemToReturn) + " to seller " + seller.getName() + " by dropping (inventory full). Auction: " + auction.getAuctionId());
            }
        } else {
            // Offline seller item return is complex. For V1, we might log and "hold" it conceptually.
            // This requires a system to store items for offline players, which is V2.
            plugin.logWarning("Seller " + seller.getName() + " is offline. Item " + getItemName(itemToReturn) + " from auction " + auction.getAuctionId() + " could not be returned directly. Needs offline handling system.");
            notifyPlayer(seller, ChatColor.YELLOW + reason + " The item " + getItemName(itemToReturn) + " could not be returned as you are offline. It will require admin intervention or a future claim system.");
            // For now, the item might be "lost" or held in the void if not handled by a more robust system.
            // A possible temporary measure: log it and don't remove from active auctions or mark for special handling.
            // However, the current design removes it. This is a known V1 limitation.
        }
    }

    private void notifyPlayer(OfflinePlayer player, String message) {
        if (player.isOnline() && player.getPlayer() != null) {
            plugin.sendMessage(player.getPlayer(), message); // Use plugin.sendMessage
        }
        // Could add offline message queuing here in the future
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName() + ChatColor.RESET; // Reset color after custom name
        }
        // Simple name for default items
        String typeName = item.getType().toString().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
    }
}
