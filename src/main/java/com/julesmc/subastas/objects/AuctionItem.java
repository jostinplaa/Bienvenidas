package com.julesmc.subastas.objects;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class AuctionItem {

    private int id;
    private UUID sellerUuid;
    private String sellerName;
    private transient ItemStack itemStack; // Actual ItemStack, not stored directly in DB as is
    private String itemSerialized; // Base64 or YAML string
    private String itemName; // Common name like DIAMOND_SWORD
    private double initialPrice;
    private Double buyNowPrice; // Nullable
    private Double currentBid; // Nullable
    private UUID highestBidderUuid; // Nullable
    private String highestBidderName; // Nullable
    private long startTime; // Milliseconds
    private int durationSeconds;
    private long endTime; // Milliseconds (startTime + durationSeconds * 1000)
    private String status; // e.g., "ACTIVE", "SOLD", "EXPIRED", "CANCELLED"
    private boolean claimedSeller;
    private boolean claimedBuyer;

    // Constructor for creating a new auction (before saving to DB, ID might not be known)
    public AuctionItem(UUID sellerUuid, String sellerName, ItemStack itemStack, String itemSerialized, String itemName,
                       double initialPrice, Double buyNowPrice, long startTime, int durationSeconds, String status) {
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.itemSerialized = itemSerialized;
        this.itemName = itemName;
        this.initialPrice = initialPrice;
        this.buyNowPrice = buyNowPrice;
        this.currentBid = null; // Initially no bid
        this.highestBidderUuid = null;
        this.highestBidderName = null;
        this.startTime = startTime;
        this.durationSeconds = durationSeconds;
        this.endTime = startTime + (durationSeconds * 1000L);
        this.status = status;
        this.claimedSeller = false;
        this.claimedBuyer = false;
    }

    // Constructor for loading from DB (includes ID and all fields)
    public AuctionItem(int id, UUID sellerUuid, String sellerName, String itemSerialized, String itemName,
                       double initialPrice, Double buyNowPrice, Double currentBid, UUID highestBidderUuid,
                       String highestBidderName, long startTime, int durationSeconds, long endTime, String status,
                       boolean claimedSeller, boolean claimedBuyer) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemSerialized = itemSerialized;
        //this.itemStack = null; // Needs deserialization after loading
        this.itemName = itemName;
        this.initialPrice = initialPrice;
        this.buyNowPrice = buyNowPrice;
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.startTime = startTime;
        this.durationSeconds = durationSeconds;
        this.endTime = endTime;
        this.status = status;
        this.claimedSeller = claimedSeller;
        this.claimedBuyer = claimedBuyer;
    }

    // Getters
    public int getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItemStack() { return itemStack; }
    public String getItemSerialized() { return itemSerialized; }
    public String getItemName() { return itemName; }
    public double getInitialPrice() { return initialPrice; }
    public Double getBuyNowPrice() { return buyNowPrice; }
    public Double getCurrentBid() { return currentBid; }
    public UUID getHighestBidderUuid() { return highestBidderUuid; }
    public String getHighestBidderName() { return highestBidderName; }
    public long getStartTime() { return startTime; }
    public int getDurationSeconds() { return durationSeconds; }
    public long getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public boolean isClaimedSeller() { return claimedSeller; }
    public boolean isClaimedBuyer() { return claimedBuyer; }

    // Setters
    public void setId(int id) { this.id = id; } // Usually set by DB
    public void setSellerUuid(UUID sellerUuid) { this.sellerUuid = sellerUuid; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public void setItemStack(ItemStack itemStack) { this.itemStack = itemStack; } // After deserialization
    public void setItemSerialized(String itemSerialized) { this.itemSerialized = itemSerialized; } // After serialization
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setInitialPrice(double initialPrice) { this.initialPrice = initialPrice; }
    public void setBuyNowPrice(Double buyNowPrice) { this.buyNowPrice = buyNowPrice; }
    public void setCurrentBid(Double currentBid) { this.currentBid = currentBid; }
    public void setHighestBidderUuid(UUID highestBidderUuid) { this.highestBidderUuid = highestBidderUuid; }
    public void setHighestBidderName(String highestBidderName) { this.highestBidderName = highestBidderName; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setStatus(String status) { this.status = status; }
    public void setClaimedSeller(boolean claimedSeller) { this.claimedSeller = claimedSeller; }
    public void setClaimedBuyer(boolean claimedBuyer) { this.claimedBuyer = claimedBuyer; }

    // Call this after loading from DB and deserializing itemSerialized
    public void associateItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }
}
