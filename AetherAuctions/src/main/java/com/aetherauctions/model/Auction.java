package com.aetherauctions.model; // Changed

// import com.aetherauctions.auction.AuctionStatus; // Will be in same package
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.aetherauctions.auction.AuctionStatus;

public class Auction {
    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private ItemStack itemStack;
    private final double startPrice;
    private double currentBid;
    private UUID highestBidderUUID;
    private String highestBidderName;
    private final double buyNowPrice;
    private final long creationTimestamp;
    private long endTimeMillis;
    private List<Bid> bidHistory;
    private AuctionStatus status;
    private boolean isMystery;
    private String mysteryDescription;

    // Main constructor (intended for loading from DB or full instantiation)
    public Auction(UUID id, UUID sellerId, String sellerName, ItemStack itemStack,
                   double startPrice, double buyNowPrice, long creationTimestamp, long endTimeMillis,
                   AuctionStatus status, UUID highestBidderUUID, String highestBidderName, double currentBid,
                   List<Bid> bidHistory, boolean isMystery, String mysteryDescription) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.itemStack = (itemStack != null ? itemStack.clone() : null);
        this.startPrice = startPrice;
        this.buyNowPrice = buyNowPrice;
        this.creationTimestamp = creationTimestamp;
        this.endTimeMillis = endTimeMillis;
        this.status = status;
        this.highestBidderUUID = highestBidderUUID;
        this.highestBidderName = highestBidderName;
        this.currentBid = currentBid;
        this.bidHistory = bidHistory != null ? new ArrayList<>(bidHistory) : new ArrayList<>();
        this.isMystery = isMystery;
        this.mysteryDescription = (isMystery ? mysteryDescription : null); // Ensure description is null if not mystery
    }

    // Simplified constructor for new auction creation (non-mystery)
    public Auction(UUID auctionId, UUID sellerId, String sellerName, ItemStack itemStack, double startPrice, double buyNowPrice, long creationTime, long expirationTime) {
        this(auctionId, sellerId, sellerName, itemStack, startPrice, buyNowPrice, creationTime, expirationTime,
             AuctionStatus.ACTIVE, null, null, startPrice, new ArrayList<>(), false, null);
    }

    // Constructor for new mystery auctions (simplified for creation)
    public Auction(UUID auctionId, UUID sellerId, String sellerName, ItemStack itemStack, // itemStack can be null for mystery
                   double startPrice, double buyNowPrice, long creationTime, long expirationTime,
                   boolean isMystery, String mysteryDescription) {
        this(auctionId, sellerId, sellerName, itemStack, startPrice, buyNowPrice, creationTime, expirationTime,
             AuctionStatus.ACTIVE, null, null, startPrice, new ArrayList<>(), isMystery,
             isMystery ? mysteryDescription : null); // Only set description if it's a mystery auction
    }


    // Getters
    public UUID getAuctionId() { return id; }
    public UUID getSellerUUID() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItemStack() { return itemStack != null ? itemStack.clone() : null; }
    public double getStartPrice() { return startPrice; }
    public double getCurrentBid() { return currentBid; }
    public UUID getHighestBidderUUID() { return highestBidderUUID; }
    public String getHighestBidderName() { return highestBidderName; }
    public double getBuyoutPrice() { return buyNowPrice; }
    public long getTimeCreated() { return creationTimestamp; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public List<Bid> getBidHistory() { return new ArrayList<>(bidHistory); }
    public AuctionStatus getStatus() { return status; }
    public boolean isMystery() { return isMystery; }
    public String getMysteryDescription() { return mysteryDescription; }
    public long getRemainingTimeMillis() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // Setters
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
    public void setHighestBidderUUID(UUID highestBidderUUID) { this.highestBidderUUID = highestBidderUUID; }
    public void setHighestBidderName(String highestBidderName) { this.highestBidderName = highestBidderName; }
    public void setStatus(AuctionStatus status) { this.status = status; }
    public void addBidToHistory(Bid bid) {
        if (this.bidHistory == null) {
            this.bidHistory = new ArrayList<>();
        }
        this.bidHistory.add(bid);
    }
    public void setItemStack(ItemStack itemStack) { this.itemStack = itemStack; }
    public void setBidHistory(List<Bid> bidHistory) { this.bidHistory = bidHistory; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }
    public void setMystery(boolean isMystery) { this.isMystery = isMystery; }
    public void setMysteryDescription(String mysteryDescription) { this.mysteryDescription = (isMystery ? mysteryDescription : null); }


    @Override
    public String toString() {
        return "Auction{" +
               "id=" + id +
               ", sellerName='" + sellerName + '\'' +
               ", item=" + (itemStack != null ? itemStack.getType() : "null") +
               ", currentBid=" + currentBid +
               ", status=" + status +
               '}';
    }

    public boolean hasBuyNow() {
        return buyNowPrice > 0;
    }

    public double getBuyNowPrice() {
        return buyNowPrice;
    }

    public String getId() {
        return id.toString();
    }
}
