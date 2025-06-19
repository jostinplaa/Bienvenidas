package com.aetherauctions.auction;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.config.ConfigManager; // Import ConfigManager
import com.aetherauctions.config.MessageManager; // Import MessageManager
import com.aetherauctions.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // Will be replaced by MessageManager
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.aetherauctions.util.InventoryUtil; // Import InventoryUtil

import java.sql.SQLException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;


public class AuctionManager {

    private final AetherAuctions plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager; // Add ConfigManager
    private final MessageManager messageManager; // Add MessageManager
    private final Map<Integer, AuctionItem> activeAuctions; // Cache for active auctions
    private BukkitTask expiredAuctionCheckerTask = null;
    private long currentCheckInterval; // To store the period of the task

    public AuctionManager(AetherAuctions plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = plugin.getConfigManager(); // Get ConfigManager
        this.messageManager = plugin.getMessageManager(); // Get MessageManager
        this.activeAuctions = new HashMap<>();
    }

    public void loadActiveAuctions() {
        activeAuctions.clear();
        try {
            List<AuctionItem> auctionsFromDB = databaseManager.getActiveAuctions();
            for (AuctionItem item : auctionsFromDB) {
                activeAuctions.put(item.getId(), item);
            }
            plugin.getLogger().info(String.format("Loaded %d active auctions from the database.", activeAuctions.size()));
        } catch (SQLException | IOException e) { // IOException from ItemSerializer
            plugin.getLogger().log(Level.SEVERE, "Error loading active auctions from the database.", e);
        }
    }

    public boolean createAuction(Player seller, ItemStack item, long durationMillis, double startPrice, double buyNowPrice) {
        if (item == null || item.getType() == Material.AIR) {
            messageManager.sendMessage(seller, "item_slot_empty"); // Or a more generic "invalid item"
            return false;
        }

        boolean isVip = seller.hasPermission(configManager.getVipPermission()); // Check VIP status for duration
        long maxAllowedDurationSeconds = isVip ? configManager.getVipExtendedDurationSeconds() : configManager.getMaxDurationSeconds();

        if (durationMillis < configManager.getMinDurationSeconds() * 1000L) {
            messageManager.sendMessage(seller, "duration_too_short", "%duration%", InventoryUtil.formatTime(configManager.getMinDurationSeconds() * 1000L));
            return false;
        }
        if (durationMillis > maxAllowedDurationSeconds * 1000L) { // Use maxAllowedDurationSeconds
             messageManager.sendMessage(seller, "duration_too_long", "%duration%", InventoryUtil.formatTime(maxAllowedDurationSeconds * 1000L));
            return false;
        }
        if (startPrice <= 0) { // Min start price could be a config option too
            messageManager.sendMessage(seller, "invalid_price_format"); // Or specific "start_price_too_low_abs"
            return false;
        }
         if (startPrice > configManager.getMaxStartPrice()) {
            messageManager.sendMessage(seller, "start_price_too_high", "%max_price%", String.format("%.2f", configManager.getMaxStartPrice()), "%currency%", configManager.getCurrencySymbol());
            return false;
        }
        if (buyNowPrice > 0 && buyNowPrice <= startPrice) {
            messageManager.sendMessage(seller, "buy_now_must_be_greater");
            return false;
        }

        if (configManager.getItemBlacklist().contains(item.getType().name())) {
            messageManager.sendMessage(seller, "item_blacklisted", "%item%", item.getType().name());
            return false;
        }

        // VIP Perks Check (maxUserAuctions already checked using isVip)
        // boolean isVip = seller.hasPermission(configManager.getVipPermission()); // Already got this for duration
        int maxUserAuctions = isVip ? configManager.getVipMaxActiveAuctions() : configManager.getMaxActiveAuctionsPerPlayer();

        if (getActiveAuctionsByPlayer(seller.getUniqueId().toString()).size() >= maxUserAuctions) {
            messageManager.sendMessage(seller, "max_auctions_reached", "%limit%", String.valueOf(maxUserAuctions));
            return false;
        }

        Economy econ = AetherAuctions.getEconomy();
        boolean chargeCreationFee = configManager.isCreationFeeEnabled();
        if (isVip && configManager.isVipNoCreationFee()) {
            chargeCreationFee = false;
        }
        double listingFee = chargeCreationFee ? configManager.getCreationFeeAmount() : 0.0;

        if (listingFee > 0 && !econ.has(seller, listingFee)) {
            messageManager.sendMessage(seller, "not_enough_money_create_fee", "%amount%", String.format("%.2f", listingFee), "%currency%", configManager.getCurrencySymbol());
            return false;
        }

        AuctionItem newAuction = new AuctionItem(
                0, // ID will be generated by DB
                seller.getUniqueId().toString(),
                seller.getName(),
                item.clone(),
                System.currentTimeMillis(),
                durationMillis,
                startPrice,
                buyNowPrice > 0 && configManager.isBuyNowAllowed() ? buyNowPrice : -1,
                startPrice,
                null,
                null,
                AuctionStatus.ACTIVE
        );

        try {
            ItemStack currentItemInHand = seller.getInventory().getItemInMainHand();
            if (!item.isSimilar(currentItemInHand) || currentItemInHand.getAmount() < item.getAmount()) {
                 // This check is a bit simplistic if player is trying to auction from other inventory slots.
                 // A more robust check would be seller.getInventory().containsAtLeast(item, item.getAmount())
                 // and then seller.getInventory().removeItem(item.clone()); but this requires exact match of NBT for clone.
                 // For now, we assume the item passed IS the item from hand (or a GUI slot) and GUIManager/Command has handled getting it.
                 // The crucial part is removing the correct amount.
                 // If item passed to this method is a clone of what the player *thinks* they are selling:
                if(seller.getInventory().containsAtLeast(item, item.getAmount())){
                    seller.getInventory().removeItem(item); // This removes *matching* items up to item.getAmount()
                } else {
                    messageManager.sendMessage(seller, "not_enough_items_in_hand", "%item%", item.getType().toString()); // A new message might be needed
                    return false;
                }
            } else { // Item is from hand and amount is sufficient
                 ItemStack toRemove = item.clone(); // Ensure we remove the correct amount
                 seller.getInventory().removeItem(toRemove);
            }


            if (listingFee > 0) {
                if (!econ.withdrawPlayer(seller, listingFee).transactionSuccess()) {
                    seller.getInventory().addItem(item.clone()); // Refund item
                    messageManager.sendMessage(seller, "auction_create_fee_charged_error"); // New message
                    return false;
                }
            }

            int generatedId = databaseManager.saveAuction(newAuction);
            if (generatedId != -1) {
                newAuction.setId(generatedId);
                activeAuctions.put(newAuction.getId(), newAuction);
                messageManager.sendMessage(seller, "auction_created_success", "%id%", String.valueOf(newAuction.getId()));
                if (listingFee > 0) {
                    messageManager.sendMessage(seller, "auction_create_fee_charged", "%amount%", String.format("%.2f", listingFee), "%currency%", configManager.getCurrencySymbol());
                }
                plugin.getGuiManager().refreshOpenAuctionGuis(newAuction, false, seller);
                return true;
            } else {
                seller.getInventory().addItem(item.clone()); // Refund item
                if (listingFee > 0) econ.depositPlayer(seller, listingFee); // Refund fee
                messageManager.sendMessage(seller, "internal_error"); // Generic error
                return false;
            }
        } catch (Exception e) { // Catch broader exceptions like from inventory manipulation
            plugin.getLogger().log(Level.SEVERE, "Error creating auction for " + seller.getName(), e);
            seller.getInventory().addItem(item.clone()); // Attempt refund
            if (listingFee > 0) econ.depositPlayer(seller, listingFee);
            messageManager.sendMessage(seller, "internal_error");
            return false;
        }
    }

    public boolean placeBid(Player bidder, AuctionItem auction, double bidAmount) {
        Economy econ = AetherAuctions.getEconomy();

        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(bidder, "auction_ended_no_longer_exists");
            return false;
        }
        if (auction.getSellerUUID().equals(bidder.getUniqueId().toString())) {
            messageManager.sendMessage(bidder, "cannot_bid_on_own_auction");
            return false;
        }
        if (auction.getHighestBidderUUID() != null && auction.getHighestBidderUUID().equals(bidder.getUniqueId().toString())) {
            messageManager.sendMessage(bidder, "already_highest_bidder");
            return false;
        }

        double minIncrement = configManager.getMinBidIncrementAmount();
        double requiredBid;

        if (auction.getHighestBidderUUID() == null) { // First bid
            requiredBid = auction.getStartPrice();
            if (bidAmount < requiredBid) {
                messageManager.sendMessage(bidder, "bid_too_low_initial", "%min_bid%", String.format("%.2f", requiredBid), "%currency%", configManager.getCurrencySymbol());
                return false;
            }
        } else { // Subsequent bids
            requiredBid = auction.getCurrentBid() + minIncrement;
            if (bidAmount < requiredBid) {
                 messageManager.sendMessage(bidder, "bid_too_low_increment", "%min_bid%", String.format("%.2f", requiredBid), "%currency%", configManager.getCurrencySymbol(), "%increment%", String.format("%.2f", minIncrement));
                return false;
            }
        }

        // Check if bid meets or exceeds BuyNow price
        if (auction.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed() && bidAmount >= auction.getBuyNowPrice()) {
            messageManager.sendMessage(bidder, "bid_equals_buy_now");
            return buyNow(bidder, auction.getId(), true); // true for bid-triggered buynow
        }

        if (!econ.has(bidder, bidAmount)) {
            messageManager.sendMessage(bidder, "not_enough_money_bid");
            return false;
        }

        String oldHighestBidderUUID = auction.getHighestBidderUUID();
        String oldHighestBidderName = auction.getHighestBidderName(); // Store old name for rollback
        double oldCurrentBid = auction.getCurrentBid();

        if (!econ.withdrawPlayer(bidder, bidAmount).transactionSuccess()) {
            messageManager.sendMessage(bidder, "internal_error"); // Generic error for fund withdrawal
            return false;
        }

        if (oldHighestBidderUUID != null) {
            OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(UUID.fromString(oldHighestBidderUUID));
            econ.depositPlayer(previousBidder, oldCurrentBid);
            if (previousBidder.isOnline()) {
                 messageManager.sendMessage(previousBidder.getPlayer(), "outbid_notification",
                    "%new_bidder%", bidder.getName(),
                    "%id%", String.valueOf(auction.getId()),
                    "%item%", auction.getItemStack().getType().toString(), // Consider using display name
                    "%old_bid%", String.format("%.2f", oldCurrentBid),
                    "%currency%", configManager.getCurrencySymbol());
            }
        }

        auction.setCurrentBid(bidAmount);
        auction.setHighestBidderUUID(bidder.getUniqueId().toString());
        auction.setHighestBidderName(bidder.getName());

        try {
            databaseManager.saveAuction(auction);
            activeAuctions.put(auction.getId(), auction);

            messageManager.sendMessage(bidder, "bid_placed_success", "%bid%", String.format("%.2f", bidAmount), "%currency%", configManager.getCurrencySymbol(), "%id%", String.valueOf(auction.getId()), "%item%", auction.getItemStack().getType().toString());

            Player sellerPlayer = Bukkit.getPlayer(UUID.fromString(auction.getSellerUUID()));
            if (sellerPlayer != null && sellerPlayer.isOnline()) {
                messageManager.sendMessage(sellerPlayer, "new_bid_on_your_auction", // Assuming this new key exists
                    "%bid%", String.format("%.2f", bidAmount),
                    "%bidder%", bidder.getName(),
                    "%id%", String.valueOf(auction.getId()),
                    "%item%", auction.getItemStack().getType().toString(),
                    "%currency%", configManager.getCurrencySymbol());
            }
            plugin.getGuiManager().refreshOpenAuctionGuis(auction, false, bidder);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQL error updating bid for auction " + auction.getId(), e);
            messageManager.sendMessage(bidder, "internal_error");
            // Rollback
            econ.depositPlayer(bidder, bidAmount);
            if (oldHighestBidderUUID != null) {
                 OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(UUID.fromString(oldHighestBidderUUID));
                 econ.withdrawPlayer(previousBidder, oldCurrentBid);
            }
            auction.setCurrentBid(oldCurrentBid);
            auction.setHighestBidderUUID(oldHighestBidderUUID);
            auction.setHighestBidderName(oldHighestBidderName); // Restore old name
            return false;
        }
    }

    public boolean buyNow(Player buyer, int auctionId) {
        return buyNow(buyer, auctionId, false);
    }

    private boolean buyNow(Player buyer, int auctionId, boolean bidTriggered) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null) {
             try { auction = databaseManager.getAuction(auctionId); }
             catch (SQLException | IOException e) { plugin.getLogger().log(Level.WARNING, "Error fetching auction " + auctionId + " for BuyNow", e); }
        }

        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(buyer, "auction_ended_no_longer_exists"); // Assuming a message key
            return false;
        }
        if (auction.getBuyNowPrice() <= 0) {
            messageManager.sendMessage(buyer, "buy_now_not_available"); // Assuming a message key
            return false;
        }
        if (auction.getSellerUUID().equals(buyer.getUniqueId().toString())) {
            messageManager.sendMessage(buyer, "cannot_buy_own_auction"); // Assuming a message key
            return false;
        }

        Economy econ = AetherAuctions.getEconomy();
        if (!econ.has(buyer, auction.getBuyNowPrice())) {
            messageManager.sendMessage(buyer, "not_enough_money_buy_now", "%amount%", String.format("%.2f", auction.getBuyNowPrice()), "%currency%", configManager.getCurrencySymbol());
            return false;
        }

        if (!econ.withdrawPlayer(buyer, auction.getBuyNowPrice()).transactionSuccess()) {
            messageManager.sendMessage(buyer, "internal_error_buy_now_withdraw"); // Assuming a message key
            return false;
        }

        if (auction.getHighestBidderUUID() != null && !auction.getHighestBidderUUID().equals(buyer.getUniqueId().toString())) {
            OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(UUID.fromString(auction.getHighestBidderUUID()));
            econ.depositPlayer(previousBidder, auction.getCurrentBid());
            if (previousBidder.isOnline()) {
                messageManager.sendMessage(previousBidder.getPlayer(), "auction_bought_out_refund", "%id%", String.valueOf(auction.getId()), "%old_bid%", String.format("%.2f", auction.getCurrentBid()), "%currency%", configManager.getCurrencySymbol());
            }
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(auction.getSellerUUID()));
        double commissionRate = plugin.getConfig().getDouble("auction.commission_rate_buyout", 0.05);
        double commission = auction.getBuyNowPrice() * commissionRate;
        double amountToSeller = auction.getBuyNowPrice() - commission;

        econ.depositPlayer(seller, amountToSeller);
        String itemDisplayName = auction.getItemStack().hasItemMeta() && auction.getItemStack().getItemMeta().hasDisplayName() ? auction.getItemStack().getItemMeta().getDisplayName() : auction.getItemStack().getType().toString();

        if (buyer.getInventory().firstEmpty() == -1) {
            messageManager.sendMessage(buyer, "inventory_full_claimable", "%item%", itemDisplayName, "%id%", String.valueOf(auction.getId()));
            try {
                databaseManager.addClaimableItem(buyer.getUniqueId().toString(), auction.getItemStack().clone(), "Comprado en subasta ID: " + auction.getId());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error adding claimable item after BuyNow for auction " + auction.getId(), e);
                messageManager.sendMessage(buyer, "internal_error_claim_item"); // Consider a specific message
            }
        } else {
            buyer.getInventory().addItem(auction.getItemStack().clone());
            messageManager.sendMessage(buyer, "buy_now_success", "%item%", itemDisplayName);
        }

        auction.setStatus(AuctionStatus.SOLD_VIA_BUYOUT);
        auction.setHighestBidderUUID(buyer.getUniqueId().toString());
        auction.setHighestBidderName(buyer.getName());
        auction.setCurrentBid(auction.getBuyNowPrice());
        activeAuctions.remove(auction.getId());

        try {
            databaseManager.saveAuction(auction);

            if (seller.isOnline()) {
                 messageManager.sendMessage(seller.getPlayer(), "your_item_bought_out", "%item%", itemDisplayName, "%id%", String.valueOf(auction.getId()), "%buyer%", buyer.getName(), "%price%", String.format("%.2f", auction.getBuyNowPrice()), "%received%", String.format("%.2f", amountToSeller), "%currency%", configManager.getCurrencySymbol());
            }
            plugin.getGuiManager().refreshOpenAuctionGuis(auction, false, buyer);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error SQL al actualizar estado de subasta (BuyNow) " + auction.getId(), e);
            messageManager.sendMessage(buyer, "internal_error_buy_now_process");
            // Rollback
            econ.depositPlayer(buyer, auction.getBuyNowPrice());
            econ.withdrawPlayer(seller, amountToSeller); // Attempt to reverse seller deposit
            auction.setStatus(AuctionStatus.ACTIVE); // Revert status
            activeAuctions.put(auction.getId(), auction); // Put back in cache if removed
            return false;
        }
    }

    public boolean cancelAuction(Player player, int auctionId) {
        AuctionItem auction = activeAuctions.get(auctionId);
         if (auction == null) {
             try { auction = databaseManager.getAuction(auctionId); }
             catch (SQLException | IOException e) { plugin.getLogger().log(Level.WARNING, "Error fetching auction " + auctionId + " for cancellation", e); }
        }

        if (auction == null) {
            messageManager.sendMessage(player, "auction_not_found", "%id%", String.valueOf(auctionId));
            return false;
        }

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(player, "auction_not_active_cancel", "%id%", String.valueOf(auctionId));
            return false;
        }

        boolean isAdmin = player.hasPermission("aetherauctions.admin");
        if (!auction.getSellerUUID().equals(player.getUniqueId().toString()) && !isAdmin) {
            messageManager.sendMessage(player, "error_cannot_cancel_others_auction");
            return false;
        }
        String itemDisplayName = auction.getItemStack().hasItemMeta() && auction.getItemStack().getItemMeta().hasDisplayName() ? auction.getItemStack().getItemMeta().getDisplayName() : auction.getItemStack().getType().toString();

        OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(auction.getSellerUUID()));
        if (seller.isOnline()) {
            if(seller.getPlayer().getInventory().firstEmpty() == -1){
                 messageManager.sendMessage(seller.getPlayer(), "inventory_full_claimable_cancelled", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName);
                try {
                    databaseManager.addClaimableItem(auction.getSellerUUID(), auction.getItemStack().clone(), "Subasta ID " + auction.getId() + " ("+itemDisplayName+") cancelada.");
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error adding claimable item after cancel for auction " + auction.getId(), e);
                }
            } else {
                seller.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                messageManager.sendMessage(seller.getPlayer(), "auction_cancelled_item_returned", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName);
            }
        } else {
            try {
                databaseManager.addClaimableItem(auction.getSellerUUID(), auction.getItemStack().clone(), "Subasta ID " + auction.getId() + " ("+itemDisplayName+") cancelada.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error adding claimable item (offline seller) after cancel for auction " + auction.getId(), e);
            }
            plugin.getLogger().info("Ítem de subasta cancelada ID " + auction.getId() + " para " + seller.getName() + " enviado a ítems reclamables (offline).");
        }

        if (auction.getHighestBidderUUID() != null) {
            OfflinePlayer highestBidder = Bukkit.getOfflinePlayer(UUID.fromString(auction.getHighestBidderUUID()));
            Economy econ = AetherAuctions.getEconomy();
            econ.depositPlayer(highestBidder, auction.getCurrentBid());
            if (highestBidder.isOnline()) {
                messageManager.sendMessage(highestBidder.getPlayer(), "auction_cancelled_bid_refunded", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName, "%bid%", String.format("%.2f", auction.getCurrentBid()), "%currency%", configManager.getCurrencySymbol());
            }
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        activeAuctions.remove(auction.getId());

        try {
            databaseManager.saveAuction(auction);
            if (isAdmin && !auction.getSellerUUID().equals(player.getUniqueId().toString())) {
                 messageManager.sendMessage(player, "auction_cancelled_admin", "%id%", String.valueOf(auctionId));
            }
            plugin.getGuiManager().refreshOpenAuctionGuis(auction, true, player); // True for owner only + action taker
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error SQL al cancelar la subasta " + auctionId, e);
            messageManager.sendMessage(player, "internal_error_cancel_auction");
            // Rollback
            auction.setStatus(AuctionStatus.ACTIVE);
            activeAuctions.put(auction.getId(), auction);
            return false;
        }
    }

    public void endAuction(AuctionItem auction) {
        if (auction == null || (auction.getStatus() != AuctionStatus.ACTIVE && auction.getStatus() != AuctionStatus.EXPIRED)) {
            return;
        }
        String itemDisplayName = auction.getItemStack().hasItemMeta() && auction.getItemStack().getItemMeta().hasDisplayName() ? auction.getItemStack().getItemMeta().getDisplayName() : auction.getItemStack().getType().toString();
        plugin.getLogger().info("Finalizando subasta ID: " + auction.getId() + " (" + itemDisplayName + ")");
        Economy econ = AetherAuctions.getEconomy();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(auction.getSellerUUID()));

        if (auction.getHighestBidderUUID() != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(UUID.fromString(auction.getHighestBidderUUID()));

            double commissionRate = plugin.getConfig().getDouble("auction.commission_rate_bid", 0.05);
            double commission = auction.getCurrentBid() * commissionRate;
            double amountToSeller = auction.getCurrentBid() - commission;

            econ.depositPlayer(seller, amountToSeller);

            if (winner.isOnline()) {
                if(winner.getPlayer().getInventory().firstEmpty() == -1){
                    messageManager.sendMessage(winner.getPlayer(), "inventory_full_claimable_won", "%item%", itemDisplayName, "%id%", String.valueOf(auction.getId()));
                    try {
                        databaseManager.addClaimableItem(winner.getUniqueId().toString(), auction.getItemStack().clone(), "Ganado en subasta ID: " + auction.getId());
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Error adding claimable item after win for auction " + auction.getId(), e);
                    }
                } else {
                    winner.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                    messageManager.sendMessage(winner.getPlayer(), "auction_won_item_received", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName);
                }
            } else {
                try {
                    databaseManager.addClaimableItem(winner.getUniqueId().toString(), auction.getItemStack().clone(), "Ganado en subasta ID: " + auction.getId());
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error adding claimable item (offline winner) for auction " + auction.getId(), e);
                }
                 plugin.getLogger().info("Ítem '" + itemDisplayName + "' ganado en subasta ID " + auction.getId() + " por " + winner.getName() + " enviado a ítems reclamables (offline).");
            }

            auction.setStatus(AuctionStatus.SOLD_VIA_BID);
            if(seller.isOnline()){
                 messageManager.sendMessage(seller.getPlayer(), "your_auction_ended_sold", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName, "%winner%", auction.getHighestBidderName(), "%price%", String.format("%.2f", auction.getCurrentBid()), "%received%", String.format("%.2f", amountToSeller), "%currency%", configManager.getCurrencySymbol());
            }

        } else { // No highest bidder - auction expired without bids
            if (seller.isOnline()) {
                 if(seller.getPlayer().getInventory().firstEmpty() == -1){
                    messageManager.sendMessage(seller.getPlayer(), "inventory_full_claimable_expired_no_bids", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName);
                    try {
                        databaseManager.addClaimableItem(seller.getUniqueId().toString(), auction.getItemStack().clone(), "Subasta ID " + auction.getId() + " ("+itemDisplayName+") expirada sin pujas.");
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Error adding claimable item after expiry (no bids) for auction " + auction.getId(), e);
                    }
                 } else {
                    seller.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                    messageManager.sendMessage(seller.getPlayer(), "auction_expired_no_bids_item_returned", "%id%", String.valueOf(auction.getId()), "%item%", itemDisplayName);
                 }
            } else {
                try {
                    databaseManager.addClaimableItem(auction.getSellerUUID(), auction.getItemStack().clone(), "Subasta ID " + auction.getId() + " ("+itemDisplayName+") expirada sin pujas.");
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error adding claimable item (offline seller, no bids) for auction " + auction.getId(), e);
                }
                 plugin.getLogger().info("Ítem de subasta expirada ID " + auction.getId() + " ("+itemDisplayName+") para " + seller.getName() + " enviado a ítems reclamables (offline).");
            }
            auction.setStatus(AuctionStatus.EXPIRED);
        }

        activeAuctions.remove(auction.getId());

        try {
            databaseManager.saveAuction(auction);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error SQL al actualizar estado de subasta finalizada " + auction.getId(), e);
        }
        // Determine actionTaker for expiry. If it's a bid win, winner is actionTaker. If no bids, seller is.
        // For simplicity, passing null if no clear single "taker", GUIManager will refresh broadly.
        Player actionTaker = null;
        if (auction.getHighestBidderUUID() != null) {
            actionTaker = Bukkit.getPlayer(UUID.fromString(auction.getHighestBidderUUID()));
        } else if (auction.getSellerUUID() != null){
            actionTaker = Bukkit.getPlayer(UUID.fromString(auction.getSellerUUID()));
        }
        plugin.getGuiManager().refreshOpenAuctionGuis(auction, false, actionTaker);
    }

    public void startExpiredAuctionsTask() {
        if (expiredAuctionCheckerTask != null) {
            expiredAuctionCheckerTask.cancel();
        }
        long checkInterval = plugin.getConfig().getLong("auction.expired_check_interval_seconds", 60) * 20L;
        this.currentCheckInterval = checkInterval; // Store the interval
        expiredAuctionCheckerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredAuctions();
            }
        }.runTaskTimer(plugin, checkInterval, checkInterval);
        plugin.getLogger().info("Tarea de revisión de subastas expiradas iniciada (cada " + (checkInterval/20) + " segundos).");
    }

    public void stopExpiredAuctionsTask() {
        if (expiredAuctionCheckerTask != null) {
            expiredAuctionCheckerTask.cancel();
            expiredAuctionCheckerTask = null;
            plugin.getLogger().info("Tarea de revisión de subastas expiradas detenida.");
        }
    }

    private void checkExpiredAuctions() {
        long currentTime = System.currentTimeMillis();
        List<AuctionItem> toProcessFromCache = new ArrayList<>();

        for (AuctionItem auction : activeAuctions.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE && (auction.getStartTime() + auction.getDuration()) < currentTime) {
                toProcessFromCache.add(auction);
            }
        }

        if (!toProcessFromCache.isEmpty()) {
            plugin.getLogger().info(String.format("Encontradas %d subastas expiradas en caché para procesar.", toProcessFromCache.size()));
            for (AuctionItem auction : toProcessFromCache) {
                endAuction(auction);
            }
        }

        long dbFallbackInterval = plugin.getConfig().getLong("auction.db_fallback_expired_check_millis", 5 * 60 * 1000);
        // Use currentCheckInterval (converted to milliseconds) for comparison logic
        if (expiredAuctionCheckerTask != null && dbFallbackInterval > 0 && (System.currentTimeMillis() % dbFallbackInterval < (this.currentCheckInterval / 20 * 1000))) {
            try {
                List<AuctionItem> dbExpired = databaseManager.getExpiredAuctions();
                if (!dbExpired.isEmpty()) {
                    int processedFromDb = 0;
                    for (AuctionItem auction : dbExpired) {
                        if (!activeAuctions.containsKey(auction.getId())) {
                            plugin.getLogger().info("Procesando subasta expirada ID " + auction.getId() + " desde la base de datos (no en caché).");
                            endAuction(auction);
                            processedFromDb++;
                        }
                    }
                    if (processedFromDb > 0) {
                         plugin.getLogger().info(String.format("Procesadas %d subastas expiradas directamente desde la BD.", processedFromDb));
                    }
                }
            } catch (SQLException | IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error al buscar subastas expiradas directamente en la BD.", e);
            }
        }
    }

    public Map<Integer, AuctionItem> getActiveAuctionsMap() {
        return activeAuctions;
    }

    public AuctionItem getAuction(int id) {
        AuctionItem item = activeAuctions.get(id);
        return item;
    }

    public List<AuctionItem> getActiveAuctionsByPlayer(String playerUUID) {
        return activeAuctions.values().stream()
                .filter(auc -> auc.getSellerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }
}
