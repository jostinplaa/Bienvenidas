package com.jules.auctionmasterelite.data;

/**
 * Represents the different types of auctions available.
 */
public enum AuctionType {
    /**
     * A public auction visible and accessible to everyone.
     */
    PUBLIC,

    /**
     * A private auction accessible only to invited players.
     */
    PRIVATE,

    /**
     * A fast-paced, short-duration public auction.
     */
    FLASH,

    /**
     * An auction scheduled to start at a specific time in the future.
     */
    SCHEDULED
}
