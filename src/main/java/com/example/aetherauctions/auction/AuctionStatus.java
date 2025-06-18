package com.example.aetherauctions.auction;

public enum AuctionStatus {
    ACTIVE,
    SOLD,
    EXPIRED,
    CANCELLED;

    public static AuctionStatus fromString(String statusStr) {
        if (statusStr == null) return null;
        try {
            return AuctionStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // Or throw an exception, or return a default
        }
    }
}
