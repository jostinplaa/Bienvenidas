package com.jules.auctionmasterelite.data;

import java.util.UUID;

/**
 * A data class to hold a player's personal settings for the auction plugin.
 */
public class PlayerPreferences {

    private final UUID playerId;
    private boolean outbidNotification;
    private boolean bidNotification;

    /**
     * Constructor for new players with default settings.
     * @param playerId The player's UUID.
     */
    public PlayerPreferences(UUID playerId) {
        this.playerId = playerId;
        this.outbidNotification = true; // Default to true
        this.bidNotification = true;    // Default to true
    }

    /**
     * Constructor for loading existing preferences from the database.
     * @param playerId The player's UUID.
     * @param outbidNotification Preference for outbid notifications.
     * @param bidNotification Preference for notifications on their own auctions.
     */
    public PlayerPreferences(UUID playerId, boolean outbidNotification, boolean bidNotification) {
        this.playerId = playerId;
        this.outbidNotification = outbidNotification;
        this.bidNotification = bidNotification;
    }

    // Getters
    public UUID getPlayerId() {
        return playerId;
    }

    public boolean hasOutbidNotification() {
        return outbidNotification;
    }

    public boolean hasBidNotification() {
        return bidNotification;
    }

    // Setters
    public void setOutbidNotification(boolean outbidNotification) {
        this.outbidNotification = outbidNotification;
    }

    public void setBidNotification(boolean bidNotification) {
        this.bidNotification = bidNotification;
    }
}
