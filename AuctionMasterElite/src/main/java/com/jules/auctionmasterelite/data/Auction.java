package com.jules.auctionmasterelite.data;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Represents a single auction instance.
 */
public class Auction {

    private final UUID auctionId;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final long startTime;
    private long endTime;
    private final double startingBid;
    private final AuctionType type;
    private final Set<UUID> invitedPlayers;

    private AuctionStatus status;
    private double currentBid;
    private UUID topBidderId;
    private String topBidderName;
    private final List<Bid> bids;

    public Auction(UUID sellerId, String sellerName, ItemStack item, long startTime, long endTime, double startingBid, AuctionType type) {
        this(UUID.randomUUID(), sellerId, sellerName, item, startTime, endTime, startingBid, startingBid, null, null, type, AuctionStatus.ACTIVE, new CopyOnWriteArrayList<>(), new CopyOnWriteArraySet<>());
    }

    // Constructor for loading from database
    public Auction(UUID auctionId, UUID sellerId, String sellerName, ItemStack item, long startTime, long endTime, double startingBid, double currentBid, UUID topBidderId, String topBidderName, AuctionType type, AuctionStatus status, List<Bid> bids, Set<UUID> invitedPlayers) {
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startingBid = startingBid;
        this.currentBid = currentBid;
        this.topBidderId = topBidderId;
        this.topBidderName = topBidderName;
        this.type = type;
        this.status = status;
        this.bids = new CopyOnWriteArrayList<>(bids);
        this.invitedPlayers = new CopyOnWriteArraySet<>(invitedPlayers);
    }

    // Getters
    public UUID getAuctionId() { return auctionId; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getStartingBid() { return startingBid; }
    public AuctionType getType() { return type; }
    public AuctionStatus getStatus() { return status; }
    public double getCurrentBid() { return currentBid; }
    public UUID getTopBidderId() { return topBidderId; }
    public String getTopBidderName() { return topBidderName; }
    public List<Bid> getBids() { return bids; }
    public Set<UUID> getInvitedPlayers() { return invitedPlayers; }


    // Setters / Modifiers
    public void setStatus(AuctionStatus status) { this.status = status; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public void invitePlayer(UUID playerId) {
        if (this.type == AuctionType.PRIVATE) {
            this.invitedPlayers.add(playerId);
        }
    }

    /**
     * Adds a new bid to the auction.
     * @param bid The bid to add.
     */
    public synchronized void addBid(Bid bid) {
        if (bid.getAmount() > this.currentBid) {
            this.bids.add(bid);
            this.currentBid = bid.getAmount();
            this.topBidderId = bid.getBidderId();
            this.topBidderName = bid.getBidderName();
        }
    }
}
