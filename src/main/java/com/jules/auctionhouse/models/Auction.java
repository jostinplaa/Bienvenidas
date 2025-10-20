package com.jules.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Auction {

    private final UUID auctionId;
    private final UUID sellerUuid;
    private final ItemStack item;
    private final long startTime;
    private long endTime;
    private double startingPrice;
    private double currentPrice;
    private UUID currentWinner;
    private AuctionStatus status;
    private AuctionType type;
    private double buyNowPrice;

    // Constructor para nuevas subastas
    public Auction(UUID sellerUuid, ItemStack item, long durationSeconds, double startingPrice, double buyNowPrice) {
        this(UUID.randomUUID(), sellerUuid, item, System.currentTimeMillis() + (durationSeconds * 1000), startingPrice, startingPrice, buyNowPrice, null, AuctionStatus.ACTIVE, (buyNowPrice > 0) ? AuctionType.BUY_NOW : AuctionType.CLASSIC);
    }

    // Constructor para cargar desde la BD
    public Auction(UUID auctionId, UUID sellerUuid, ItemStack item, long endTime, double startingPrice, double currentPrice, double buyNowPrice, UUID currentWinner, AuctionStatus status, AuctionType type) {
        this.auctionId = auctionId;
        this.sellerUuid = sellerUuid;
        this.item = item;
        this.startTime = 0; // No es crucial para subastas cargadas
        this.endTime = endTime;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.buyNowPrice = buyNowPrice;
        this.currentWinner = currentWinner;
        this.status = status;
        this.type = type;
    }

    public Auction(UUID sellerUuid, ItemStack item, long durationSeconds, double startingPrice) {
        this(sellerUuid, item, durationSeconds, startingPrice, -1);
    }

    // Getters y Setters
    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public ItemStack getItem() {
        return item;
    }

    public long getEndTime() {
        return endTime;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public UUID getCurrentWinner() {
        return currentWinner;
    }

    public void setCurrentWinner(UUID currentWinner) {
        this.currentWinner = currentWinner;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setBuyNowPrice(double buyNowPrice) {
        this.buyNowPrice = buyNowPrice;
    }

    public double getBuyNowPrice() {
        return buyNowPrice;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public AuctionType getType() {
        return type;
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() >= endTime;
    }

    public enum AuctionStatus {
        ACTIVE,
        EXPIRED,
        SOLD,
        CANCELLED
    }

    public enum AuctionType {
        CLASSIC,
        BUY_NOW,
        INVERSE,
        SILENT
    }
}