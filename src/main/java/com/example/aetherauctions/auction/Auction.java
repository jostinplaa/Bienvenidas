package com.example.aetherauctions.auction;

import org.bukkit.inventory.ItemStack;

import java.util.concurrent.TimeUnit;

public class Auction {
    private int id;
    private String sellerUuid;
    private String sellerName;
    private ItemStack itemStack;
    private String itemName; // Can be derived from itemStack.getItemMeta().getDisplayName() or material name
    private double startPrice;
    private double currentBid;
    private String highestBidderUuid;
    private String highestBidderName;
    private Double buyNowPrice; // Use Double object to allow null
    private long startTime; // Unix timestamp (seconds)
    private long endTime;   // Unix timestamp (seconds)
    private AuctionStatus status;
    private boolean claimedBySeller;
    private boolean claimedByWinner;

    // Full constructor
    public Auction(int id, String sellerUuid, String sellerName, ItemStack itemStack, String itemName,
                   double startPrice, double currentBid, String highestBidderUuid, String highestBidderName,
                   Double buyNowPrice, long startTime, long endTime, AuctionStatus status,
                   boolean claimedBySeller, boolean claimedByWinner) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.itemName = itemName != null ? itemName : (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName() : (itemStack != null ? itemStack.getType().name() : "Unknown Item"));
        this.startPrice = startPrice;
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.buyNowPrice = buyNowPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.claimedBySeller = claimedBySeller;
        this.claimedByWinner = claimedByWinner;
    }

    // Constructor for creating a new auction before DB insertion (ID might not be known yet)
    public Auction(String sellerUuid, String sellerName, ItemStack itemStack, String itemName,
                   double startPrice, Double buyNowPrice, long startTime, long endTime) {
        this(-1, sellerUuid, sellerName, itemStack, itemName, startPrice, 0, null, null,
                buyNowPrice, startTime, endTime, AuctionStatus.ACTIVE, false, false);
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSellerUuid() {
        return sellerUuid;
    }

    public void setSellerUuid(String sellerUuid) {
        this.sellerUuid = sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
        if (itemStack != null) {
            this.itemName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ?
                            itemStack.getItemMeta().getDisplayName() : itemStack.getType().name();
        }
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(double startPrice) {
        this.startPrice = startPrice;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public String getHighestBidderUuid() {
        return highestBidderUuid;
    }

    public void setHighestBidderUuid(String highestBidderUuid) {
        this.highestBidderUuid = highestBidderUuid;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    public Double getBuyNowPrice() {
        return buyNowPrice;
    }

    public void setBuyNowPrice(Double buyNowPrice) {
        this.buyNowPrice = buyNowPrice;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public boolean isClaimedBySeller() {
        return claimedBySeller;
    }

    public void setClaimedBySeller(boolean claimedBySeller) {
        this.claimedBySeller = claimedBySeller;
    }

    public boolean isClaimedByWinner() {
        return claimedByWinner;
    }

    public void setClaimedByWinner(boolean claimedByWinner) {
        this.claimedByWinner = claimedByWinner;
    }

    // Utility methods
    public boolean isBuyNowAvailable() {
        return buyNowPrice != null && buyNowPrice > 0;
    }

    public boolean hasEnded() {
        return System.currentTimeMillis() / 1000L >= endTime;
    }

    public String getTimeLeft() {
        if (status != AuctionStatus.ACTIVE || hasEnded()) {
            return "Ended"; // Or specific status like "Sold", "Expired"
        }
        long remainingSeconds = endTime - (System.currentTimeMillis() / 1000L);
        if (remainingSeconds <= 0) {
            return "Ending soon";
        }

        long days = TimeUnit.SECONDS.toDays(remainingSeconds);
        remainingSeconds -= TimeUnit.DAYS.toSeconds(days);
        long hours = TimeUnit.SECONDS.toHours(remainingSeconds);
        remainingSeconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(remainingSeconds);
        remainingSeconds -= TimeUnit.MINUTES.toSeconds(minutes);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.length() == 0 || days == 0) { // Show seconds if less than a minute or no other units shown
             sb.append(remainingSeconds).append("s");
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "Auction{" +
                "id=" + id +
                ", sellerName='" + sellerName + '\'' +
                ", itemName='" + itemName + '\'' +
                ", currentBid=" + currentBid +
                ", status=" + status +
                ", endTime=" + endTime +
                '}';
    }
}
