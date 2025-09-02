package com.jules.auctionmasterelite.data.sql;

import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.data.Bid;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DataSource {

    void connect() throws SQLException;

    void disconnect();

    void initializeDatabase() throws SQLException;

    void saveAuction(Auction auction);

    Map<UUID, Auction> loadAuctions();

    void saveBid(Bid bid, UUID auctionId);

    void updateAuctionBid(Auction auction);

    void updateAuctionStatus(Auction auction);

    void updateAuctionEndTime(Auction auction);

    List<Auction> loadPlayerHistory(UUID playerId);

    void saveInvitedPlayer(UUID auctionId, UUID playerId);

    void saveClaim(UUID playerId, ItemStack item, String reason);

    Map<Integer, ItemStack> getPlayerClaims(UUID playerId);

    void deleteClaim(int claimId);
}
