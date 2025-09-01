package com.jules.auctionmasterelite.data;

/**
 * Represents the status of an auction.
 */
public enum AuctionStatus {
    /**
     * The auction is currently running and accepting bids.
     */
    ACTIVE,

    /**
     * The auction has finished, and a winner has been determined.
     */
    FINISHED,

    /**
     * The auction was cancelled before it could finish.
     */
    CANCELLED,

    /**
     * The auction is scheduled but has not started yet.
     */
    SCHEDULED
}
