package com.example.aetherauctions.auction;

import com.example.aetherauctions.AetherAuctions;
import com.example.aetherauctions.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AuctionManager {

    private final AetherAuctions plugin;
    private final DatabaseManager dbManager;
    private final Economy economy;
    private Set<Material> itemBlacklist = new HashSet<>();
    private int defaultAuctionLimit;
    private int vipAuctionLimit;
    private double publicationTax;
    private double salesCommission;
    private double minimumBidIncrement;

    public AuctionManager(AetherAuctions plugin) {
        this.plugin = plugin;
        this.dbManager = plugin.getDatabaseManager();
        this.economy = plugin.getEconomy();
        loadConfigValues();
    }

    public void loadConfigValues() {
        plugin.reloadConfig(); // Ensure latest config is used

        // Load item blacklist
        itemBlacklist = plugin.getConfig().getStringList("item-blacklist").stream()
                .map(Material::matchMaterial)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        plugin.getLogger().info("Loaded " + itemBlacklist.size() + " blacklisted items.");

        // Load auction limits
        defaultAuctionLimit = plugin.getConfig().getInt("limits.default-player", 5);
        vipAuctionLimit = plugin.getConfig().getInt("limits.vip-player", 10);

        // Load tax and commission
        publicationTax = plugin.getConfig().getDouble("publication-tax", 0.05); // Example: 0.05 for 5%
        salesCommission = plugin.getConfig().getDouble("sales-commission", 0.02); // Example: 0.02 for 2%

        minimumBidIncrement = plugin.getConfig().getDouble("minimum-bid-increment", 10);

        plugin.getLogger().info("AuctionManager configuration loaded/reloaded.");
    }


    public Auction createAuction(Player seller, ItemStack item, double startPrice, Double buyNowPrice, long durationSeconds) {
        if (item == null || item.getType() == Material.AIR) {
            // TODO: Send message to player (item cannot be null/air)
            return null;
        }
        if (itemBlacklist.contains(item.getType())) {
            // TODO: Send message to player (item is blacklisted)
            plugin.getLogger().warning("Player " + seller.getName() + " tried to auction blacklisted item: " + item.getType());
            seller.sendMessage(plugin.getMessages().getString("item-blacklisted", "This item is blacklisted and cannot be auctioned."));
            return null;
        }

        // Check auction limits
        int currentAuctions = dbManager.getAuctionsByPlayer(seller.getUniqueId().toString(), 1, Integer.MAX_VALUE)
                                    .stream().filter(a -> a.getStatus() == AuctionStatus.ACTIVE).toList().size();
        int limit = seller.hasPermission("aetherauctions.vip") ? vipAuctionLimit : defaultAuctionLimit;
        if (currentAuctions >= limit) {
            // TODO: Send message to player (auction limit reached)
             seller.sendMessage(plugin.getMessages().getString("auction-limit-reached", "You have reached your auction limit (%limit%).").replace("%limit%", String.valueOf(limit)));
            return null;
        }

        if (economy == null) {
            plugin.getLogger().severe("Economy not found! Auction creation failed.");
            seller.sendMessage("§cEconomy features are disabled. Cannot create auction.");
            return null;
        }

        // Deduct publication tax
        if (publicationTax > 0 && ! (seller.hasPermission("aetherauctions.vip") && plugin.getConfig().getBoolean("vip.no-publication-tax", true))) {
            double taxAmount;
            if (publicationTax < 1.0) { // Percentage based
                taxAmount = startPrice * publicationTax;
            } else { // Fixed amount
                taxAmount = publicationTax;
            }
            EconomyResponse taxResponse = economy.withdrawPlayer(seller, taxAmount);
            if (!taxResponse.transactionSuccess()) {
                // TODO: Send message to player (not enough money for tax)
                seller.sendMessage(plugin.getMessages().getString("not-enough-money", "You don't have enough money for the publication tax."));
                return null;
            }
            // TODO: Log tax deduction
        }


        long startTime = System.currentTimeMillis() / 1000L;
        long endTime = startTime + durationSeconds;
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                          item.getItemMeta().getDisplayName() : item.getType().toString();

        Auction auction = new Auction(
                seller.getUniqueId().toString(), seller.getName(), item.clone(), // Store a clone
                itemName, startPrice, buyNowPrice, startTime, endTime
        );
        auction.setStatus(AuctionStatus.ACTIVE); // Explicitly set active

        Auction createdAuction = dbManager.createAuction(auction);
        if (createdAuction != null) {
            // TODO: Send success message to player
            // TODO: Broadcast message if configured
            plugin.getLogger().info("Player " + seller.getName() + " created auction ID: " + createdAuction.getId());
            seller.sendMessage(plugin.getMessages().getString("item-successfully-auctioned", "You have successfully auctioned your item!")
                .replace("%item%", itemName)
                .replace("%price%", String.valueOf(startPrice)));
        } else {
            // TODO: Send failure message to player
            plugin.getLogger().severe("Failed to create auction for " + seller.getName() + " in database.");
             seller.sendMessage("§cFailed to create auction in database. Please contact an admin.");
            // Refund tax if it was deducted
             if (publicationTax > 0 && ! (seller.hasPermission("aetherauctions.vip") && plugin.getConfig().getBoolean("vip.no-publication-tax", true))) {
                double taxAmount = publicationTax < 1.0 ? startPrice * publicationTax : publicationTax;
                economy.depositPlayer(seller, taxAmount); // Attempt to refund
            }
        }
        return createdAuction;
    }

    public boolean placeBid(Player bidder, Auction auction, double bidAmount) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            bidder.sendMessage(plugin.getMessages().getString("item-not-for-sale", "This auction is no longer active."));
            return false;
        }
        if (bidder.getUniqueId().toString().equals(auction.getSellerUuid())) {
             bidder.sendMessage(plugin.getMessages().getString("cannot-bid-on-own-auction", "You cannot bid on your own auction."));
            return false;
        }
        if (auction.getHighestBidderUuid() != null && bidder.getUniqueId().toString().equals(auction.getHighestBidderUuid())) {
            bidder.sendMessage(plugin.getMessages().getString("cannot-outbid-yourself", "You are already the highest bidder."));
            return false;
        }

        double minNextBid = auction.getCurrentBid() > 0 ? auction.getCurrentBid() + minimumBidIncrement : auction.getStartPrice();
        if (auction.getCurrentBid() == 0 && bidAmount < auction.getStartPrice()){
             minNextBid = auction.getStartPrice(); // First bid must be at least start price
        } else if (auction.getCurrentBid() > 0 && bidAmount < auction.getCurrentBid() + minimumBidIncrement){
             minNextBid = auction.getCurrentBid() + minimumBidIncrement;
        }


        if (bidAmount < minNextBid) {
            bidder.sendMessage(plugin.getMessages().getString("bid-too-low", "Your bid is too low. Minimum bid: %amount%").replace("%amount%", String.valueOf(minNextBid)));
            return false;
        }

        if (auction.isBuyNowAvailable() && bidAmount >= auction.getBuyNowPrice()) {
            // If bid is equal or higher than buy now, treat as buy now
            return buyNow(bidder, auction);
        }

        if (economy == null) {
            plugin.getLogger().severe("Economy not found! Bidding failed.");
            bidder.sendMessage("§cEconomy features are disabled. Cannot place bid.");
            return false;
        }

        EconomyResponse withdrawResp = economy.withdrawPlayer(bidder, bidAmount);
        if (!withdrawResp.transactionSuccess()) {
            bidder.sendMessage(plugin.getMessages().getString("not-enough-money", "You do not have enough money to place this bid."));
            return false;
        }

        // Refund previous highest bidder
        if (auction.getHighestBidderUuid() != null) {
            OfflinePlayer prevHighestBidder = plugin.getServer().getOfflinePlayer(UUID.fromString(auction.getHighestBidderUuid()));
            economy.depositPlayer(prevHighestBidder, auction.getCurrentBid());
            Player onlinePrevBidder = prevHighestBidder.getPlayer();
            if(onlinePrevBidder != null) {
                onlinePrevBidder.sendMessage(plugin.getMessages().getString("outbid-notification", "You have been outbid on %item%!")
                                            .replace("%item%", auction.getItemName()));
            }
        }

        auction.setCurrentBid(bidAmount);
        auction.setHighestBidderUuid(bidder.getUniqueId().toString());
        auction.setHighestBidderName(bidder.getName());

        if (dbManager.updateAuction(auction)) {
            bidder.sendMessage(plugin.getMessages().getString("bid-placed", "You successfully placed a bid of %amount% on %item%.")
                                .replace("%amount%", String.valueOf(bidAmount))
                                .replace("%item%", auction.getItemName()));
            // Notify seller if online? (Optional)
            return true;
        } else {
            // Rollback bid
            economy.depositPlayer(bidder, bidAmount);
            // TODO: Restore previous bidder's state if possible (more complex)
            bidder.sendMessage("§cAn error occurred while placing your bid. Your money has been refunded.");
            plugin.getLogger().severe("Failed to update auction " + auction.getId() + " after bid by " + bidder.getName());
            return false;
        }
    }

    public boolean buyNow(Player buyer, Auction auction) {
        if (auction.getStatus() != AuctionStatus.ACTIVE || !auction.isBuyNowAvailable()) {
            buyer.sendMessage(plugin.getMessages().getString("item-not-for-sale", "This item is not available for Buy Now or the auction ended."));
            return false;
        }
        if (buyer.getUniqueId().toString().equals(auction.getSellerUuid())) {
            buyer.sendMessage(plugin.getMessages().getString("cannot-bid-on-own-auction", "You cannot buy your own auction.")); // Using same msg for simplicity
            return false;
        }

        if (economy == null) {
            plugin.getLogger().severe("Economy not found! Buy Now failed.");
            buyer.sendMessage("§cEconomy features are disabled. Cannot use Buy Now.");
            return false;
        }

        double buyNowPrice = auction.getBuyNowPrice();
        EconomyResponse withdrawResp = economy.withdrawPlayer(buyer, buyNowPrice);
        if (!withdrawResp.transactionSuccess()) {
            buyer.sendMessage(plugin.getMessages().getString("not-enough-money", "You do not have enough money for Buy Now."));
            return false;
        }

        // Refund highest bidder if exists and is not the buyer
        if (auction.getHighestBidderUuid() != null && !auction.getHighestBidderUuid().equals(buyer.getUniqueId().toString())) {
            OfflinePlayer prevHighestBidder = plugin.getServer().getOfflinePlayer(UUID.fromString(auction.getHighestBidderUuid()));
            economy.depositPlayer(prevHighestBidder, auction.getCurrentBid());
             Player onlinePrevBidder = prevHighestBidder.getPlayer();
            if(onlinePrevBidder != null) {
                onlinePrevBidder.sendMessage(plugin.getMessages().getString("outbid-notification", "The auction for %item% was bought out. Your bid was refunded.")
                                            .replace("%item%", auction.getItemName()));
            }
        }

        OfflinePlayer seller = plugin.getServer().getOfflinePlayer(UUID.fromString(auction.getSellerUuid()));
        double earnings = buyNowPrice;
        if (salesCommission > 0 && !(seller.isOnline() && seller.getPlayer().hasPermission("aetherauctions.vip") && plugin.getConfig().getBoolean("vip.no-sales-commission", false) ) ) { // Assuming no-commission for VIP if configured
             double commissionAmount = salesCommission < 1.0 ? buyNowPrice * salesCommission : salesCommission;
             earnings -= commissionAmount;
        }
        economy.depositPlayer(seller, earnings);

        auction.setStatus(AuctionStatus.SOLD);
        auction.setHighestBidderUuid(buyer.getUniqueId().toString()); // Buyer is the "winner"
        auction.setHighestBidderName(buyer.getName());
        auction.setCurrentBid(buyNowPrice); // Record final sale price
        auction.setClaimedByWinner(false); // Mark for buyer to claim

        if (dbManager.updateAuction(auction)) {
            buyer.getInventory().addItem(auction.getItemStack().clone()); // Give item directly for now
            // TODO: Implement claim system if inventory is full
            buyer.sendMessage(plugin.getMessages().getString("item-bought", "You bought %item% for %price%.")
                                .replace("%item%", auction.getItemName())
                                .replace("%price%", String.valueOf(buyNowPrice)));

            Player onlineSeller = seller.getPlayer();
            if(onlineSeller != null){
                onlineSeller.sendMessage(plugin.getMessages().getString("auction-ended-seller-sold", "Your auction for %item% was sold for %price%!")
                                        .replace("%item%", auction.getItemName())
                                        .replace("%price%", String.valueOf(buyNowPrice)));
            }
            plugin.getLogger().info("Auction " + auction.getId() + " bought by " + buyer.getName() + " for " + buyNowPrice);
            return true;
        } else {
            economy.depositPlayer(buyer, buyNowPrice); // Rollback
            // TODO: More complex rollback if previous bidder existed
            buyer.sendMessage("§cAn error occurred during purchase. Your money has been refunded.");
            plugin.getLogger().severe("Failed to update auction " + auction.getId() + " after Buy Now by " + buyer.getName());
            return false;
        }
    }

    public boolean cancelAuction(Player player, int auctionId) {
        Auction auction = dbManager.getAuction(auctionId);
        if (auction == null) {
            player.sendMessage("§cAuction not found.");
            return false;
        }

        if (!auction.getSellerUuid().equals(player.getUniqueId().toString()) && !player.hasPermission("aetherauctions.admin")) {
            player.sendMessage(plugin.getMessages().getString("no-permission", "You do not have permission to cancel this auction."));
            return false;
        }

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            player.sendMessage("§cThis auction is not active and cannot be cancelled.");
            return false;
        }

        if (economy == null && auction.getHighestBidderUuid() != null) {
             player.sendMessage("§cEconomy features are disabled. Cannot cancel auction with bids safely.");
             plugin.getLogger().severe("Economy not found! Cancelling auction with bids failed for auction ID " + auctionId);
            return false;
        }


        // Refund highest bidder if exists
        if (auction.getHighestBidderUuid() != null) {
            OfflinePlayer highestBidder = plugin.getServer().getOfflinePlayer(UUID.fromString(auction.getHighestBidderUuid()));
            economy.depositPlayer(highestBidder, auction.getCurrentBid());
             Player onlineBidder = highestBidder.getPlayer();
            if(onlineBidder != null){
                onlineBidder.sendMessage(plugin.getMessages().getString("auction-cancelled", "Auction for %item% was cancelled. Your bid was refunded.")
                                        .replace("%item%", auction.getItemName()));
            }
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setClaimedBySeller(false); // Mark for seller to reclaim

        if (dbManager.updateAuction(auction)) {
            player.sendMessage(plugin.getMessages().getString("auction-cancelled", "You have successfully cancelled your auction for %item%.")
                                .replace("%item%", auction.getItemName()));
            plugin.getLogger().info("Auction " + auction.getId() + " cancelled by " + player.getName());
            // Item will be available for seller to claim
            return true;
        } else {
            // Attempt to rollback refund if it happened? Very complex. For now, log and error.
            player.sendMessage("§cFailed to cancel auction. Please try again or contact an admin.");
            plugin.getLogger().severe("Failed to update auction " + auction.getId() + " to CANCELLED status.");
            return false;
        }
    }

    public void processExpiredAuctions() {
        if (dbManager == null) {
            plugin.getLogger().warning("DatabaseManager is null, cannot process expired auctions.");
            return;
        }
        List<Auction> expired = dbManager.getExpiredAuctions();
        if (expired.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Processing " + expired.size() + " expired auctions...");

        for (Auction auction : expired) {
            if (auction.getStatus() != AuctionStatus.ACTIVE) continue; // Should not happen if query is correct

            OfflinePlayer seller = plugin.getServer().getOfflinePlayer(UUID.fromString(auction.getSellerUuid()));

            if (auction.getHighestBidderUuid() != null && auction.getCurrentBid() > 0) { // Sold
                auction.setStatus(AuctionStatus.SOLD);
                auction.setClaimedByWinner(false); // Winner needs to claim

                if (economy != null) {
                    double earnings = auction.getCurrentBid();
                     if (salesCommission > 0 && !(seller.isOnline() && seller.getPlayer().hasPermission("aetherauctions.vip") && plugin.getConfig().getBoolean("vip.no-sales-commission", false) ) ) {
                        double commissionAmount = salesCommission < 1.0 ? auction.getCurrentBid() * salesCommission : salesCommission;
                        earnings -= commissionAmount;
                    }
                    economy.depositPlayer(seller, earnings);

                    Player onlineSeller = seller.getPlayer();
                    if(onlineSeller != null){
                         onlineSeller.sendMessage(plugin.getMessages().getString("auction-ended-seller-sold", "Your auction for %item% sold for %price%!")
                                        .replace("%item%", auction.getItemName())
                                        .replace("%price%", String.valueOf(auction.getCurrentBid())));
                    }

                    OfflinePlayer winner = plugin.getServer().getOfflinePlayer(UUID.fromString(auction.getHighestBidderUuid()));
                    Player onlineWinner = winner.getPlayer();
                    if(onlineWinner != null){
                        onlineWinner.sendMessage(plugin.getMessages().getString("auction-ended-winner", "You won the auction for %item%!")
                                                .replace("%item%", auction.getItemName()));
                        // TODO: Implement claim system instead of direct add
                        // onlineWinner.getInventory().addItem(auction.getItemStack().clone());
                    }

                } else {
                     plugin.getLogger().severe("Economy not found! Cannot process payment for sold auction ID " + auction.getId());
                }

            } else { // Expired (no bids)
                auction.setStatus(AuctionStatus.EXPIRED);
                auction.setClaimedBySeller(false); // Seller needs to claim back
                 Player onlineSeller = seller.getPlayer();
                 if(onlineSeller != null){
                    onlineSeller.sendMessage(plugin.getMessages().getString("auction-ended-seller-not-sold", "Your auction for %item% expired without selling.")
                                            .replace("%item%", auction.getItemName()));
                 }
            }
            dbManager.updateAuction(auction);
            plugin.getLogger().info("Processed auction ID " + auction.getId() + " - New status: " + auction.getStatus());
        }
         plugin.getLogger().info("Finished processing expired auctions.");
    }
}
