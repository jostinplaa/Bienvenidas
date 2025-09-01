package com.jules.auctionmasterelite.data;

import java.util.UUID;

/**
 * Represents a single bid on an auction.
 */
public class Bid {

    private final UUID bidderId;
    private final String bidderName;
    private final double amount;
    private final long timestamp;

    public Bid(UUID bidderId, String bidderName, double amount, long timestamp) {
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.amount = amount;
        this.timestamp = timestamp;
    }

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
}
