package com.aetherauctions.model;

import java.util.UUID;

public class Bid {
    private final UUID bidderId;
    private final String bidderName; // Nombre del jugador que hizo la puja
    private final double amount;
    private final long timestamp; // Momento en que se realizó la puja (System.currentTimeMillis())

    public Bid(UUID bidderId, String bidderName, double amount, long timestamp) {
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    // Getters
    public UUID getBidderId() {
        return bidderId;
    }

    public String getBidderName() {
        return bidderName;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Podría incluirse un método toString() para logging o debug
    @Override
    public String toString() {
        return "Bid{" +
               "bidderId=" + bidderId +
               ", bidderName='" + bidderName + '\'' +
               ", amount=" + amount +
               ", timestamp=" + timestamp +
               '}';
    }
}
