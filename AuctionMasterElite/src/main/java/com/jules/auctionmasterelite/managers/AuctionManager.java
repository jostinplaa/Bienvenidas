package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all active auctions in memory.
 */
public class AuctionManager {

    private final AuctionMasterElite plugin;
    private final Map<UUID, Auction> activeAuctions;

    public AuctionManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
        this.activeAuctions = new ConcurrentHashMap<>(plugin.getDatabaseManager().getDataSource().loadAuctions());
    }

    /**
     * Creates a new auction and adds it to the manager.
     *
     * @param auction The auction to create.
     */
    public void createAuction(Auction auction) {
        activeAuctions.put(auction.getAuctionId(), auction);

        // Send Discord notification if manager is available
        if (plugin.getDiscordManager() != null) {
            plugin.getDiscordManager().sendNewAuctionNotification(auction);
        }

        // Announce Flash auctions globally
        if (auction.getType() == com.jules.auctionmasterelite.data.AuctionType.FLASH) {
            String itemName = auction.getItem().hasItemMeta() && auction.getItem().getItemMeta().hasDisplayName()
                    ? auction.getItem().getItemMeta().getDisplayName()
                    : auction.getItem().getType().toString().replace("_", " ").toLowerCase();
            MessageUtil.broadcastMessage("flash-auction-broadcast", "player", auction.getSellerName(), "item", itemName);
        }
    }

    public void placeBid(Player player, UUID auctionId, double amount) {
        Auction auction = getAuction(auctionId);

        // --- Validation Checks ---
        if (auction == null || auction.getStatus() != com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE) {
            MessageUtil.sendMessage(player, "auction-not-active");
            return;
        }
        if (auction.getSellerId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "cannot-bid-on-own");
            return;
        }
        if (auction.getType() == com.jules.auctionmasterelite.data.AuctionType.PRIVATE && !auction.getInvitedPlayers().contains(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "private-auction-no-invite");
            return;
        }
        if (amount <= auction.getCurrentBid()) {
            MessageUtil.sendMessage(player, "bid-too-low", "amount", String.format("%.2f", auction.getCurrentBid()));
            return;
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        if (!economyManager.hasEnough(player, amount)) {
            MessageUtil.sendMessage(player, "not-enough-funds");
            return;
        }

        // --- Logic ---
        OfflinePlayer previousTopBidder = auction.getTopBidderId() != null ? Bukkit.getOfflinePlayer(auction.getTopBidderId()) : null;

        // Withdraw from new bidder
        economyManager.withdraw(player, amount);

        // Refund previous bidder
        if (previousTopBidder != null) {
            economyManager.deposit(previousTopBidder, auction.getCurrentBid());
            if (previousTopBidder.isOnline()) {
                MessageUtil.sendMessage(previousTopBidder.getPlayer(), "outbid-notification", "amount", String.format("%.2f", auction.getCurrentBid()), "item", auction.getItem().getType().toString());
            }
        }

        // Update auction object
        com.jules.auctionmasterelite.data.Bid newBid = new com.jules.auctionmasterelite.data.Bid(player.getUniqueId(), player.getName(), amount, System.currentTimeMillis());
        auction.addBid(newBid);

        // Update database
        plugin.getDatabaseManager().getDataSource().saveBid(newBid, auctionId);
        plugin.getDatabaseManager().getDataSource().updateAuctionBid(auction);

        // --- Notifications ---
        MessageUtil.sendMessage(player, "bid-success", "amount", String.format("%.2f", amount));

        // Notify seller
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
        if (seller.isOnline()) {
            MessageUtil.sendMessage(seller.getPlayer(), "seller-bid-notification", "amount", String.format("%.2f", amount), "item", auction.getItem().getType().toString());
        }

        // --- Anti-Sniping Logic ---
        if (plugin.getConfig().getBoolean("settings.anti-sniping.enabled", true)) {
            long remainingTime = auction.getEndTime() - System.currentTimeMillis();
            long threshold = plugin.getConfig().getLong("settings.anti-sniping.threshold-seconds", 30) * 1000;

            if (remainingTime <= threshold) {
                long extendDuration = plugin.getConfig().getLong("settings.anti-sniping.extend-duration-seconds", 15) * 1000;
                auction.setEndTime(auction.getEndTime() + extendDuration);
                plugin.getDatabaseManager().getDataSource().updateAuctionEndTime(auction);
                // Optionally, notify the bidder that the time was extended.
                MessageUtil.sendMessage(player, "auction-time-extended", "seconds", String.valueOf(extendDuration / 1000));
            }
        }
    }

    /**
     * Retrieves an auction by its ID.
     *
     * @param auctionId The UUID of the auction.
     * @return The Auction object, or null if not found.
     */
    public Auction getAuction(UUID auctionId) {
        return activeAuctions.get(auctionId);
    }

    /**
     * Removes an auction from the manager.
     *
     * @param auctionId The UUID of the auction to remove.
     */
    public void removeAuction(UUID auctionId) {
        activeAuctions.remove(auctionId);
    }

    /**
     * Gets a map of all active auctions.
     *
     * @return A map of active auctions.
     */
    public Map<UUID, Auction> getActiveAuctions() {
        return activeAuctions.entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<Auction> getAuctionsBySeller(UUID sellerId) {
        return activeAuctions.values().stream()
                .filter(auction -> auction.getSellerId().equals(sellerId))
                .collect(Collectors.toList());
    }

    public List<Auction> getAuctionsByBidder(UUID bidderId) {
        return activeAuctions.values().stream()
                .filter(auction -> bidderId.equals(auction.getTopBidderId()))
                .collect(Collectors.toList());
    }

    /**
     * Periodically called to check for and end expired auctions.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Auction auction : activeAuctions.values()) {
            if (auction.getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE && now >= auction.getEndTime()) {
                endAuction(auction);
            }
        }
    }

    private void endAuction(Auction auction) {
        auction.setStatus(com.jules.auctionmasterelite.data.AuctionStatus.FINISHED);
        plugin.getDatabaseManager().getDataSource().updateAuctionStatus(auction);
        System.out.println("Auction " + auction.getAuctionId() + " has ended.");

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());

        // Case 1: There was a winner
        if (auction.getTopBidderId() != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getTopBidderId());

            // Pay the seller, taking commission
            double commissionRate = plugin.getConfigManager().getConfig().getDouble("settings.commission-fee-percent", 0.0) / 100.0;
            double finalPrice = auction.getCurrentBid();
            double commission = finalPrice * commissionRate;
            double amountToSeller = finalPrice - commission;

            plugin.getEconomyManager().deposit(seller, amountToSeller);
            if (seller.isOnline()) {
                MessageUtil.sendMessage(seller.getPlayer(), "auction-won-seller", "item", auction.getItem().getType().toString(), "amount", String.format("%.2f", amountToSeller), "commission", String.format("%.2f", commission));
            }

            // Give item to winner
            Player winnerPlayer = winner.isOnline() ? winner.getPlayer() : null;
            if (winnerPlayer != null && winnerPlayer.getInventory().firstEmpty() != -1) {
                winnerPlayer.getInventory().addItem(auction.getItem());
                MessageUtil.sendMessage(winnerPlayer, "auction-won-item-received", "item", auction.getItem().getType().toString());
            } else {
                // Player is offline or inventory is full, save to claims
                plugin.getDatabaseManager().getDataSource().saveClaim(winner.getUniqueId(), auction.getItem(), "Auction Won");
                if (winnerPlayer != null) {
                    MessageUtil.sendMessage(winnerPlayer, "auction-won-inventory-full-claim");
                }
                plugin.getLogger().info("Item for auction " + auction.getAuctionId() + " sent to " + winner.getName() + "'s claims.");
            }

        } else { // Case 2: No bids
            Player sellerPlayer = seller.isOnline() ? seller.getPlayer() : null;
            if (sellerPlayer != null && sellerPlayer.getInventory().firstEmpty() != -1) {
                sellerPlayer.getInventory().addItem(auction.getItem());
                MessageUtil.sendMessage(sellerPlayer, "auction-ended-no-bids-item-returned", "item", auction.getItem().getType().toString());
            } else {
                // Player is offline or inventory is full, save to claims
                plugin.getDatabaseManager().getDataSource().saveClaim(seller.getUniqueId(), auction.getItem(), "Auction Expired");
                 if (sellerPlayer != null) {
                    MessageUtil.sendMessage(sellerPlayer, "auction-ended-no-bids-inv-full-claim");
                }
                plugin.getLogger().info("Expired auction item " + auction.getAuctionId() + " sent to " + seller.getName() + "'s claims.");
            }
        }
    }
}
