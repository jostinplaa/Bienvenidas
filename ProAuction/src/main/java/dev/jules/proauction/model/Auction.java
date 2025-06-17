package dev.jules.proauction.model;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Auction {

    private UUID auctionId;
    private ItemStack item;
    private UUID sellerUuid;
    private String sellerName;
    private double startingPrice;
    private double currentBid;
    private double minIncrement;
    private UUID highestBidderUuid;
    private String highestBidderName;
    private long endTimeMillis;
    private boolean isActive;
    private double buyNowPrice; // New field

    public Auction(UUID auctionId, ItemStack item, UUID sellerUuid, String sellerName, double startingPrice, double minIncrement, long endTimeMillis, double buyNowPrice) {
        this.auctionId = auctionId;
        this.item = item.clone(); // Clone to prevent modifications to the original stack
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.startingPrice = startingPrice;
        this.currentBid = startingPrice; // Initial bid is the starting price
        this.minIncrement = minIncrement;
        this.highestBidderUuid = null;
        this.highestBidderName = null;
        this.endTimeMillis = endTimeMillis;
        this.isActive = true;
        this.buyNowPrice = buyNowPrice > 0 ? buyNowPrice : -1; // Ensure buyNow is -1 if not applicable or invalid
    }

    // Constructor for full state restoration
    public Auction(UUID auctionId, ItemStack item, UUID sellerUuid, String sellerName,
                   double startingPrice, double currentBid, double minIncrement,
                   UUID highestBidderUuid, String highestBidderName,
                   long endTimeMillis, boolean isActive, double buyNowPrice) { // Added buyNowPrice
        this.auctionId = auctionId;
        this.item = item; // Assume item is already cloned if necessary before this point
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.startingPrice = startingPrice;
        this.currentBid = currentBid;
        this.minIncrement = minIncrement;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.endTimeMillis = endTimeMillis;
        this.isActive = isActive;
        this.buyNowPrice = buyNowPrice;
    }


    public UUID getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(UUID auctionId) {
        this.auctionId = auctionId;
    }

    public ItemStack getItem() {
        return item.clone(); // Return a clone to prevent external modification
    }

    public void setItem(ItemStack item) {
        this.item = item.clone(); // Clone to ensure internal state isn't affected by external changes
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public void setSellerUuid(UUID sellerUuid) {
        this.sellerUuid = sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public double getMinIncrement() {
        return minIncrement;
    }

    public void setMinIncrement(double minIncrement) {
        this.minIncrement = minIncrement;
    }

    public UUID getHighestBidderUuid() {
        return highestBidderUuid;
    }

    public void setHighestBidderUuid(UUID highestBidderUuid) {
        this.highestBidderUuid = highestBidderUuid;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public void setEndTimeMillis(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    /**
     * Sets the highest bid and updates bidder information.
     * @param currentBid The new highest bid amount.
     * @param highestBidderUuid The UUID of the new highest bidder.
     * @param highestBidderName The name of the new highest bidder.
     */
    public void setHighestBid(double currentBid, UUID highestBidderUuid, String highestBidderName) {
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
    }

    public double getBuyNowPrice() {
        return buyNowPrice;
    }

    public void setBuyNowPrice(double buyNowPrice) {
        this.buyNowPrice = buyNowPrice > 0 ? buyNowPrice : -1;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("auctionId", this.auctionId.toString());
        map.put("item", this.item.serialize()); // ItemStack serialization
        map.put("sellerUuid", this.sellerUuid.toString());
        map.put("sellerName", this.sellerName);
        map.put("startingPrice", this.startingPrice);
        map.put("currentBid", this.currentBid);
        map.put("minIncrement", this.minIncrement);
        if (this.highestBidderUuid != null) {
            map.put("highestBidderUuid", this.highestBidderUuid.toString());
            map.put("highestBidderName", this.highestBidderName);
        }
        map.put("endTimeMillis", this.endTimeMillis);
        map.put("isActive", this.isActive);
        map.put("buyNowPrice", this.buyNowPrice); // Added buyNowPrice
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Auction fromMap(Map<String, Object> map) {
        UUID auctionId = UUID.fromString((String) map.get("auctionId"));

        Object itemObject = map.get("item");
        ItemStack item;
        if (itemObject instanceof ItemStack) { // If Bukkit already deserialized it
            item = (ItemStack) itemObject;
        } else if (itemObject instanceof Map) { // If it's still a Map
            item = ItemStack.deserialize((Map<String, Object>) itemObject);
        } else {
            throw new IllegalArgumentException("Item data in auction map is not in a recognizable format: " + (itemObject != null ? itemObject.getClass().getName() : "null"));
        }

        UUID sellerUuid = UUID.fromString((String) map.get("sellerUuid"));
        String sellerName = (String) map.get("sellerName");
        double startingPrice = ((Number) map.get("startingPrice")).doubleValue();
        double currentBid = ((Number) map.get("currentBid")).doubleValue();
        double minIncrement = ((Number) map.get("minIncrement")).doubleValue();
        UUID highestBidderUuid = map.containsKey("highestBidderUuid") ? UUID.fromString((String) map.get("highestBidderUuid")) : null;
        String highestBidderName = map.containsKey("highestBidderName") ? (String) map.get("highestBidderName") : null;
        long endTimeMillis = ((Number) map.get("endTimeMillis")).longValue();
        boolean isActive = (boolean) map.get("isActive");
        double buyNowPrice = map.containsKey("buyNowPrice") ? ((Number) map.get("buyNowPrice")).doubleValue() : -1.0; // Load buyNowPrice, default to -1 if not present

        return new Auction(auctionId, item, sellerUuid, sellerName, startingPrice, currentBid, minIncrement,
                highestBidderUuid, highestBidderName, endTimeMillis, isActive, buyNowPrice); // Added buyNowPrice
    }
}
