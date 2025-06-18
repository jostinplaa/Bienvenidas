package dev.jules.proauction;

import dev.jules.proauction.model.Auction;
import dev.jules.proauction.util.TaxFeeCalculator;
import dev.jules.proauction.util.TaxFeeCalculator.TaxCalculationResult;
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // May become unused
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap; // Added for placeholders
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
                // Reason key for returnItemToSeller
                returnItemToSeller(seller, auction, "auction.end.winneroffline", new HashMap<>());
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // V1.1 Logic:
            // 1. Winner online (checked above)
            // 2. Check money
            if (!ProAuction.hasEnough(winner, auction.getCurrentBid())) {
                plugin.logInfo("Auction " + auction.getAuctionId() + " winner " + winner.getName() + " does not have enough funds (" + ProAuction.format(auction.getCurrentBid()) + "). Item returning to seller.");
                Map<String, String> notifyPlaceholders = new HashMap<>();
                notifyPlaceholders.put("item_name", getItemName(auction.getItem()));
                notifyPlaceholders.put("price", ProAuction.format(auction.getCurrentBid()));
                notifyPlayer(onlineWinner, "auction.end.winnernotenoughfunds.notify", notifyPlaceholders);

                Map<String, String> reasonPlaceholders = new HashMap<>();
                reasonPlaceholders.put("winner_name", winner.getName());
                returnItemToSeller(seller, auction, "auction.end.winnernotenoughfunds", reasonPlaceholders);
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // 3. Seller payment (Vault handles offline)
            // 4. Withdraw from winner
            if (!ProAuction.withdrawMoney(winner, auction.getCurrentBid())) {
                plugin.logSevere("CRITICAL: Failed to withdraw " + ProAuction.format(auction.getCurrentBid()) + " from winner " + winner.getName() + " for auction " + auction.getAuctionId().toString().substring(0,8) + " even after hasEnough check passed. Item returning to seller.");
                notifyPlayer(onlineWinner, "auction.end.withdrawfailed.notify", "item_name", getItemName(auction.getItem()));
                Map<String, String> reasonPlaceholders = new HashMap<>();
                reasonPlaceholders.put("winner_name", winner.getName());
                returnItemToSeller(seller, auction, "auction.end.withdrawfailed", reasonPlaceholders);
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
                notifyPlayer(onlineWinner, "error.paytochestfailed", "item_name", getItemName(auction.getItem()));
                Map<String, String> reasonPlaceholders = new HashMap<>();
                reasonPlaceholders.put("item_name", getItemName(auction.getItem()));
                returnItemToSeller(seller, auction, "auction.end.depositfailed.seller", reasonPlaceholders);
                plugin.removeAuction(auction.getAuctionId());
                return;
            }

            // 6. Give item to online winner
            if (onlineWinner.getInventory().addItem(auction.getItem().clone()).isEmpty()) { // Success
                plugin.logInfo("Auction " + auction.getAuctionId().toString().substring(0,8) + " for " + getItemName(auction.getItem()) + " sold to " + winner.getName() + " for " + ProAuction.format(originalBidAmount) + ". Seller received " + ProAuction.format(amountForSeller) + " after tax of " + ProAuction.format(taxAmount));

                Map<String, String> sellerMessagePlaceholders = new HashMap<>();
                sellerMessagePlaceholders.put("item_name", getItemName(auction.getItem()));
                sellerMessagePlaceholders.put("winner_name", winner.getName());
                sellerMessagePlaceholders.put("price", ProAuction.format(originalBidAmount));
                if (taxAmount > 0) {
                    sellerMessagePlaceholders.put("tax_percentage", String.valueOf(salesTaxPercentage));
                    sellerMessagePlaceholders.put("tax_amount", ProAuction.format(taxAmount));
                    sellerMessagePlaceholders.put("net_amount", ProAuction.format(amountForSeller));
                    notifyPlayer(seller, "auction.end.sold.seller.withtax", sellerMessagePlaceholders);
                } else {
                    sellerMessagePlaceholders.put("net_amount", ProAuction.format(amountForSeller)); // For consistency if key uses it
                    notifyPlayer(seller, "auction.end.sold.seller", sellerMessagePlaceholders);
                }

                Map<String, String> winnerMessagePlaceholders = new HashMap<>();
                winnerMessagePlaceholders.put("item_name", getItemName(auction.getItem()));
                winnerMessagePlaceholders.put("price", ProAuction.format(originalBidAmount));
                notifyPlayer(onlineWinner, "auction.end.sold.winner", winnerMessagePlaceholders);

                Map<String, String> broadcastPlaceholders = new HashMap<>();
                broadcastPlaceholders.put("item_name", getItemName(auction.getItem()));
                broadcastPlaceholders.put("winner_name", winner.getName());
                broadcastPlaceholders.put("price", ProAuction.format(originalBidAmount));
                plugin.broadcastMessage("auction.end.sold.broadcast", broadcastPlaceholders);
            } else { // Inventory full
                plugin.logInfo("Auction " + auction.getAuctionId().toString().substring(0,8) + " winner " + winner.getName() + "'s inventory was full. Rolling back transaction.");
                notifyPlayer(onlineWinner, "auction.end.inventoryfull.winner", "item_name", getItemName(auction.getItem()));

                if (!ProAuction.depositMoney(winner, originalBidAmount)) {
                     plugin.logSevere("CRITICAL: FAILED TO REFUND WINNER " + winner.getName() + " during inventory full rollback for auction " + auction.getAuctionId().toString().substring(0,8));
                }
                if(!ProAuction.withdrawMoney(seller, amountForSeller)) {
                    plugin.logSevere("CRITICAL: FAILED TO RECLAIM FUNDS (" + ProAuction.format(amountForSeller) + ") FROM SELLER " + seller.getName() + " during inventory full rollback for auction " + auction.getAuctionId().toString().substring(0,8) + ". Tax might still be on server account.");
                }
                Map<String, String> reasonPlaceholders = new HashMap<>();
                reasonPlaceholders.put("winner_name", winner.getName());
                returnItemToSeller(seller, auction, "auction.end.inventoryfull.sellerreason", reasonPlaceholders);
            }
        } else { // No winner
            plugin.logInfo("Auction " + auction.getAuctionId() + " for " + getItemName(auction.getItem()) + " by " + seller.getName() + " ended without bids.");
            returnItemToSeller(seller, auction, "auction.end.nobids", new HashMap<>());
        }
        plugin.removeAuction(auction.getAuctionId());
    }

    private void returnItemToSeller(OfflinePlayer seller, Auction auction, String reasonKey, Map<String, String> reasonPlaceholders) {
        Player onlineSeller = seller.getPlayer();
        ItemStack itemToReturn = auction.getItem().clone();
        String reasonMessage = plugin.getLanguageManager().getMessage(reasonKey, reasonPlaceholders);

        Map<String, String> notifyPlaceholders = new HashMap<>();
        notifyPlaceholders.put("reason", reasonMessage);
        notifyPlaceholders.put("item_name", getItemName(itemToReturn));

        if (onlineSeller != null && onlineSeller.isOnline()) {
            if (onlineSeller.getInventory().addItem(itemToReturn).isEmpty()) {
                notifyPlayer(onlineSeller, "auction.end.itemreturned.seller", notifyPlaceholders);
            } else {
                onlineSeller.getWorld().dropItemNaturally(onlineSeller.getLocation(), itemToReturn);
                notifyPlayer(onlineSeller, "auction.end.itemreturned.seller.dropped", notifyPlaceholders);
                plugin.logInfo("Returned item " + getItemName(itemToReturn) + " to seller " + seller.getName() + " by dropping (inventory full). Auction: " + auction.getAuctionId());
            }
        } else {
            plugin.logWarning("Seller " + seller.getName() + " is offline. Item " + getItemName(itemToReturn) + " from auction " + auction.getAuctionId() + " could not be returned directly. Needs offline handling system.");
            notifyPlayer(seller, "auction.end.itemreturned.seller.offline", notifyPlaceholders);
        }
    }

    private void notifyPlayer(OfflinePlayer player, String messageKey, Map<String, String> placeholders) {
        if (player.isOnline() && player.getPlayer() != null) {
            plugin.sendMessage(player.getPlayer(), messageKey, placeholders);
        }
        // Could add offline message queuing here in the future (e.g., save to a file to be shown on next login)
    }

    private void notifyPlayer(OfflinePlayer player, String messageKey) {
        notifyPlayer(player, messageKey, new HashMap<>());
    }

    private void notifyPlayer(OfflinePlayer player, String messageKey, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        notifyPlayer(player, messageKey, placeholders);
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName() + org.bukkit.ChatColor.RESET; // Ensure color reset after custom name
        }
        String typeName = item.getType().toString().toLowerCase().replace('_', ' '); // Declare typeName
        return Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
    }
}
