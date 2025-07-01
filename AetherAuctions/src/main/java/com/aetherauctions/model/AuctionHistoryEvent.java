package com.aetherauctions.model;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class AuctionHistoryEvent {
    private int historyId; // Auto-incremented by DB
    private final UUID playerUuid;
    private final UUID auctionId; // Can be null if not directly tied to a specific auction (e.g. a system message)
    private final String itemName;
    private final String itemMaterial;
    private final String itemSnapshot; // Could be Base64 of ItemStack, or just key details
    private final HistoryEventType eventType;
    private final double price;
    private final String counterpartyName; // Name of buyer/seller/bidder
    private final UUID counterpartyUuid; // UUID of buyer/seller/bidder
    private final long timestamp;

    public enum HistoryEventType {
        AUCTION_CREATED, // When a player lists an item
        AUCTION_SOLD_TO_BUYER, // Event for the buyer
        AUCTION_SOLD_FOR_SELLER, // Event for the seller
        AUCTION_WON_BY_BIDDER, // Event for the winning bidder
        AUCTION_EXPIRED_RETURNED, // Item returned to seller
        AUCTION_CANCELLED_BY_SELLER, // Cancelled by seller, item returned
        AUCTION_CANCELLED_BY_ADMIN, // Cancelled by admin, item returned
        BID_PLACED, // Player placed a bid (might be too verbose, consider only highest bids)
        BID_OUTBID, // Player was outbid
        BID_REFUNDED_AUCTION_CANCELLED, // Player's bid refunded due to cancellation
        BID_REFUNDED_AUCTION_SOLD_BUYNOW, // Player's bid refunded due to buy now by another
        // Could add more specific types like REWARD_MONEY_CLAIMED, REWARD_ITEM_CLAIMED if needed
    }

    // Constructor
    public AuctionHistoryEvent(UUID playerUuid, UUID auctionId, String itemName, String itemMaterial, String itemSnapshot,
                               HistoryEventType eventType, double price, String counterpartyName, UUID counterpartyUuid, long timestamp) {
        this.playerUuid = playerUuid;
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.itemMaterial = itemMaterial;
        this.itemSnapshot = itemSnapshot;
        this.eventType = eventType;
        this.price = price;
        this.counterpartyName = counterpartyName;
        this.counterpartyUuid = counterpartyUuid;
        this.timestamp = timestamp;
    }

    // Constructor including historyId (when loading from DB)
    public AuctionHistoryEvent(int historyId, UUID playerUuid, UUID auctionId, String itemName, String itemMaterial, String itemSnapshot,
                               HistoryEventType eventType, double price, String counterpartyName, UUID counterpartyUuid, long timestamp) {
        this(playerUuid, auctionId, itemName, itemMaterial, itemSnapshot, eventType, price, counterpartyName, counterpartyUuid, timestamp);
        this.historyId = historyId;
    }


    // Getters
    public int getHistoryId() { return historyId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public UUID getAuctionId() { return auctionId; }
    public String getItemName() { return itemName; }
    public String getItemMaterial() { return itemMaterial; }
    public String getItemSnapshot() { return itemSnapshot; }
    public HistoryEventType getEventType() { return eventType; }
    public double getPrice() { return price; }
    public String getCounterpartyName() { return counterpartyName; }
    public UUID getCounterpartyUuid() { return counterpartyUuid; }
    public long getTimestamp() { return timestamp; }

    // Setter for historyId as it's set by DB
    public void setHistoryId(int historyId) { this.historyId = historyId; }
}
