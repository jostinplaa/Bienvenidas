package com.aetherauctions.auction;

import org.bukkit.inventory.ItemStack;

public class AuctionItem {

    private int id;
    private String sellerUUID;
    private String sellerName;
    private ItemStack itemStack;
    private long startTime;
    private long duration;
    private double startPrice;
    private double buyNowPrice;
    private double currentBid;
    private String highestBidderUUID;
    private String highestBidderName;
    private AuctionStatus status;

    public AuctionItem(int id, String sellerUUID, String sellerName, ItemStack itemStack, long startTime, long duration,
                       double startPrice, double buyNowPrice, double currentBid, String highestBidderUUID,
                       String highestBidderName, AuctionStatus status) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.startTime = startTime;
        this.duration = duration;
        this.startPrice = startPrice;
        this.buyNowPrice = buyNowPrice;
        this.currentBid = currentBid;
        this.highestBidderUUID = highestBidderUUID;
        this.highestBidderName = highestBidderName;
        this.status = status;
    }

    // Getters
    public int getId() { return id; }
    public String getSellerUUID() { return sellerUUID; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItemStack() { return itemStack; }
    public long getStartTime() { return startTime; }
    public long getDuration() { return duration; }
    public double getStartPrice() { return startPrice; }
    public double getBuyNowPrice() { return buyNowPrice; }
    public double getCurrentBid() { return currentBid; }
    public String getHighestBidderUUID() { return highestBidderUUID; }
    public String getHighestBidderName() { return highestBidderName; }
    public AuctionStatus getStatus() { return status; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setSellerUUID(String sellerUUID) { this.sellerUUID = sellerUUID; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public void setItemStack(ItemStack itemStack) { this.itemStack = itemStack; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setDuration(long duration) { this.duration = duration; }
    public void setStartPrice(double startPrice) { this.startPrice = startPrice; }
    public void setBuyNowPrice(double buyNowPrice) { this.buyNowPrice = buyNowPrice; }
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
    public void setHighestBidderUUID(String highestBidderUUID) { this.highestBidderUUID = highestBidderUUID; }
    public void setHighestBidderName(String highestBidderName) { this.highestBidderName = highestBidderName; }
    public void setStatus(AuctionStatus status) { this.status = status; }
}
