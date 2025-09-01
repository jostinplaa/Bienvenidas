package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.data.Auction;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages all active auctions in memory.
 */
import com.jules.auctionmasterelite.AuctionMasterElite;

public class AuctionManager {

    private final AuctionMasterElite plugin;
    private final Map<UUID, Auction> activeAuctions;

    public AuctionManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
        this.activeAuctions = new ConcurrentHashMap<>(plugin.getDatabaseManager().loadAuctions());
    }

    /**
     * Creates a new auction and adds it to the manager.
     *
     * @param auction The auction to create.
     */
    public void createAuction(Auction auction) {
        activeAuctions.put(auction.getAuctionId(), auction);

        // Send Discord notification if manager is available
        if (plugin.getDiscordManager() != null) {
            plugin.getDiscordManager().sendNewAuctionNotification(auction);
        }
    }

    /**
     * Retrieves an auction by its ID.
     *
     * @param auctionId The UUID of the auction.
     * @return The Auction object, or null if not found.
     */
    public Auction getAuction(UUID auctionId) {
        return activeAuctions.get(auctionId);
    }

    /**
     * Removes an auction from the manager.
     *
     * @param auctionId The UUID of the auction to remove.
     */
    public void removeAuction(UUID auctionId) {
        activeAuctions.remove(auctionId);
    }

    /**
     * Gets a map of all active auctions.
     *
     * @return A map of active auctions.
     */
    public Map<UUID, Auction> getActiveAuctions() {
        return activeAuctions.entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Periodically called to check for and end expired auctions.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Auction auction : activeAuctions.values()) {
            if (auction.getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE && now >= auction.getEndTime()) {
                endAuction(auction);
            }
        }
    }

    private void endAuction(Auction auction) {
        auction.setStatus(com.jules.auctionmasterelite.data.AuctionStatus.FINISHED);
        // TODO: Handle auction end logic (notify winner, transfer item/money)
        System.out.println("Auction " + auction.getAuctionId() + " has ended.");
    }
}
