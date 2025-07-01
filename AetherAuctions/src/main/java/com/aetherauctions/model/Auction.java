package com.aetherauctions.model;

import com.aetherauctions.auction.AuctionStatus;
import org.bukkit.inventory.ItemStack; // Necesario para el ítem

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Auction {
    private final UUID id;                 // Identificador único de la subasta
    private final UUID sellerId;
    private final String sellerName;
    private ItemStack itemStack;
    private final double startPrice; // Campo para el precio inicial
    private double currentBid;
    private UUID highestBidderId;
    private String highestBidderName;      // Nombre del pujador más alto
    private final double buyNowPrice;      // Precio de compra directa (-1 si no está disponible)
    private final long creationTimestamp;  // Momento de creación de la subasta
    private long expirationTimestamp;      // Momento en que la subasta expira
    private List<Bid> bidHistory;          // Historial de todas las pujas
    private AuctionStatus status;          // Estado actual de la subasta

    // Constructor principal
    public Auction(UUID id, UUID sellerId, String sellerName, ItemStack itemStack,
                   double startPrice, double buyNowPrice, long creationTimestamp, long expirationTimestamp) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.itemStack = itemStack.clone();
        this.startPrice = startPrice; // Añadir campo para precio inicial
        this.currentBid = startPrice;
        this.highestBidderId = null;
        this.highestBidderName = null;
        this.buyNowPrice = buyNowPrice;
        this.creationTimestamp = creationTimestamp;
        this.expirationTimestamp = expirationTimestamp;
        this.bidHistory = new ArrayList<>();
        this.status = AuctionStatus.ACTIVE;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItemStack() { return itemStack.clone(); }
    public double getStartPrice() { return startPrice; } // Getter para startPrice
    public double getCurrentBid() { return currentBid; }
    public UUID getHighestBidderId() { return highestBidderId; }
    public String getHighestBidderName() { return highestBidderName; }
    public double getBuyNowPrice() { return buyNowPrice; }
    public boolean hasBuyNow() { return buyNowPrice > 0; }
    public long getCreationTimestamp() { return creationTimestamp; }
    public long getExpirationTimestamp() { return expirationTimestamp; }
    public List<Bid> getBidHistory() { return new ArrayList<>(bidHistory); } // Devolver copia para inmutabilidad
    public AuctionStatus getStatus() { return status; }
    public long getRemainingTimeMillis() {
        long remaining = expirationTimestamp - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // Setters (controlados, principalmente para AuctionManager)
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
    public void setHighestBidderId(UUID highestBidderId) { this.highestBidderId = highestBidderId; }
    public void setHighestBidderName(String highestBidderName) { this.highestBidderName = highestBidderName; }
    public void setStatus(AuctionStatus status) { this.status = status; }
    public void addBidToHistory(Bid bid) {
        if (this.bidHistory == null) {
            this.bidHistory = new ArrayList<>();
        }
        this.bidHistory.add(bid);
    }

    // Setter para ItemStack, principalmente para deserialización. Usar con cuidado.
    public void setItemStack(ItemStack itemStack) { this.itemStack = itemStack; }
    public void setBidHistory(List<Bid> bidHistory) { this.bidHistory = bidHistory; }
    public void setExpirationTimestamp(long expirationTimestamp) { this.expirationTimestamp = expirationTimestamp; }


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
}
