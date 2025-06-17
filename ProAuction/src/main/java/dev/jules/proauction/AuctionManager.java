package dev.jules.proauction;

import dev.jules.proauction.model.Auction;
import dev.jules.proauction.util.TaxFeeCalculator;
import dev.jules.proauction.util.TaxFeeCalculator.TaxCalculationResult;
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
                plugin.logSevere("CRITICAL: Failed to withdraw " + ProAuction.format(auction.getCurrentBid()) + " from winner " + winner.getName() + " for auction " + auction.getAuctionId().toString().substring(0,8) + " even after hasEnough check passed. Item returning to seller.");
                notifyPlayer(onlineWinner, ChatColor.RED + "Failed to withdraw funds for auction " + getItemName(auction.getItem()) + ". Please contact an admin.");
                returnItemToSeller(seller, auction, "Failed to withdraw funds from winner " + winner.getName() + ". Auction voided.");
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // Sales Tax Logic
            double salesTaxPercentage = plugin.getSalesTaxPercentage();
            String serverAccountName = plugin.getServerAccountName(); // Retain for deposit logic below
            double originalBidAmount = auction.getCurrentBid();

            TaxCalculationResult taxResult = TaxFeeCalculator.calculateSalesTax(originalBidAmount, salesTaxPercentage);
            double taxAmount = taxResult.taxAmount;
            double amountForSeller = taxResult.netAmountForSeller;

            if (taxAmount > 0) { // Only log and attempt deposit if tax was actually calculated and applied
                plugin.logInfo("Sales tax for auction " + auction.getAuctionId().toString().substring(0,8) + ": " + ProAuction.format(taxAmount) + " (" + salesTaxPercentage + "% of " + ProAuction.format(originalBidAmount) + "). Seller receives " + ProAuction.format(amountForSeller));
                if (serverAccountName != null && !serverAccountName.isEmpty()) {
                    OfflinePlayer serverAccount = plugin.getServer().getOfflinePlayer(serverAccountName);
                    boolean accountExistsOrCanBeCreated = ProAuction.getEconomy().hasAccount(serverAccountName) || serverAccount.hasPlayedBefore();

                    if (accountExistsOrCanBeCreated) {
                        if (ProAuction.depositMoney(serverAccount, taxAmount)) {
                            plugin.logInfo("Deposited sales tax of " + ProAuction.format(taxAmount) + " to server account " + serverAccountName + " for auction " + auction.getAuctionId().toString().substring(0,8));
                        } else {
                            plugin.logWarning("Failed to deposit sales tax of " + ProAuction.format(taxAmount) + " to server account " + serverAccountName + " for auction " + auction.getAuctionId().toString().substring(0,8) + ". Tax was still deducted from seller.");
                        }
                    } else {
                        plugin.logWarning("Server account '" + serverAccountName + "' for sales tax not found/accessible. Tax of " + ProAuction.format(taxAmount) + " deducted from seller but not deposited to server account. Auction: " + auction.getAuctionId().toString().substring(0,8));
                    }
                }
            }
            // If taxAmount is 0, amountForSeller is originalBidAmount, and no specific tax logging or deposit is needed here.

            // 5. Deposit to seller (now with amountForSeller)
            if (!ProAuction.depositMoney(seller, amountForSeller)) {
                plugin.logSevere("CRITICAL: Failed to deposit " + ProAuction.format(amountForSeller) + " (after tax) to seller " + seller.getName() + " for auction " + auction.getAuctionId().toString().substring(0,8) + ". Original bid: " + ProAuction.format(originalBidAmount) + ". Attempting to refund winner the original bid amount.");
                // Refund the original full amount to the winner as the transaction with seller failed.
                if (!ProAuction.depositMoney(winner, originalBidAmount)) {
                    plugin.logSevere("CRITICAL: FAILED TO REFUND WINNER " + winner.getName() + " for auction " + auction.getAuctionId().toString().substring(0,8) + ". THIS IS A DUPE/LOSS SCENARIO. Manual intervention required.");
                }
                notifyPlayer(onlineWinner, ChatColor.RED + "There was an error paying the seller for auction " + getItemName(auction.getItem()) + ". Your bid has been refunded. Please contact an admin.");
                returnItemToSeller(seller, auction, "Failed to deposit funds to you (after tax) for auction " + getItemName(auction.getItem()) + ". Winner was refunded their original bid. Auction voided.");
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // 6. Give item to online winner
            if (onlineWinner.getInventory().addItem(auction.getItem().clone()).isEmpty()) { // Success
                plugin.logInfo("Auction " + auction.getAuctionId().toString().substring(0,8) + " for " + getItemName(auction.getItem()) + " sold to " + winner.getName() + " for " + ProAuction.format(originalBidAmount) + ". Seller received " + ProAuction.format(amountForSeller) + " after tax of " + ProAuction.format(taxAmount));

                String sellerMessage = ChatColor.GREEN + "Your auction for " + getItemName(auction.getItem()) + " sold to " + winner.getName() + " for " + ProAuction.format(originalBidAmount) + ".";
                if (taxAmount > 0) {
                    sellerMessage += " After a " + salesTaxPercentage + "% sales tax (" + ProAuction.format(taxAmount) + "), you received " + ProAuction.format(amountForSeller) + ".";
                } else {
                    sellerMessage += " You received " + ProAuction.format(amountForSeller) + ".";
                }
                notifyPlayer(seller, sellerMessage);

                notifyPlayer(onlineWinner, ChatColor.GREEN + "You won the auction for " + getItemName(auction.getItem()) + " for " + ProAuction.format(originalBidAmount) + "! The item has been added to your inventory.");
                plugin.broadcastMessage(ChatColor.YELLOW + getItemName(auction.getItem()) + " was sold to " + winner.getName() + " for " + ProAuction.format(originalBidAmount) + "!");
            } else { // Inventory full
                plugin.logInfo("Auction " + auction.getAuctionId().toString().substring(0,8) + " winner " + winner.getName() + "'s inventory was full. Rolling back transaction.");
                notifyPlayer(onlineWinner, ChatColor.RED + "You won the auction for " + getItemName(auction.getItem()) + ", but your inventory is full! The transaction has been voided.");

                // Rollback: refund winner the original amount, and attempt to take back amountForSeller from seller.
                if (!ProAuction.depositMoney(winner, originalBidAmount)) {
                     plugin.logSevere("CRITICAL: FAILED TO REFUND WINNER " + winner.getName() + " during inventory full rollback for auction " + auction.getAuctionId().toString().substring(0,8));
                }
                // Try to take back what was given to seller. Tax to server account is not rolled back here.
                if(!ProAuction.withdrawMoney(seller, amountForSeller)) {
                    plugin.logSevere("CRITICAL: FAILED TO RECLAIM FUNDS (" + ProAuction.format(amountForSeller) + ") FROM SELLER " + seller.getName() + " during inventory full rollback for auction " + auction.getAuctionId().toString().substring(0,8) + ". Tax might still be on server account.");
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
