package com.jules.auctionmasterelite.data.sql;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.data.AuctionStatus;
import com.jules.auctionmasterelite.data.AuctionType;
import com.jules.auctionmasterelite.data.Bid;
import com.jules.auctionmasterelite.util.SerializationUtil;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteDataSource implements DataSource {

    private final AuctionMasterElite plugin;
    private Connection connection;
    private File dbFile;

    public SQLiteDataSource(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    @Override
    public synchronized void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        if (dbFile == null) {
            String fileName = plugin.getConfig().getString("database.sqlite.file", "auctions.db");
            this.dbFile = new File(plugin.getDataFolder(), fileName);
        }

        if (!dbFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dbFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create database file!", e);
                return;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("Successfully connected to SQLite database.");
            initializeDatabase();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
        }
    }

    @Override
    public synchronized void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Disconnected from SQLite database.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error while disconnecting from the database.", e);
            }
        }
    }

    @Override
    public void initializeDatabase() throws SQLException {
        try (java.sql.Statement statement = connection.createStatement()) {
            String auctionsTableSql = "CREATE TABLE IF NOT EXISTS auctions (" +
                    "auction_id TEXT PRIMARY KEY," +
                    "seller_id TEXT NOT NULL," +
                    "seller_name TEXT NOT NULL," +
                    "item_data BLOB NOT NULL," +
                    "start_time INTEGER NOT NULL," +
                    "end_time INTEGER NOT NULL," +
                    "starting_bid REAL NOT NULL," +
                    "current_bid REAL NOT NULL," +
                    "top_bidder_id TEXT," +
                    "top_bidder_name TEXT," +
                    "auction_type TEXT NOT NULL," +
                    "auction_status TEXT NOT NULL" +
                    ");";
            statement.execute(auctionsTableSql);

            String bidsTableSql = "CREATE TABLE IF NOT EXISTS bids (" +
                    "bid_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "auction_id TEXT NOT NULL," +
                    "bidder_id TEXT NOT NULL," +
                    "bidder_name TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "timestamp INTEGER NOT NULL," +
                    "FOREIGN KEY(auction_id) REFERENCES auctions(auction_id)" +
                    ");";
            statement.execute(bidsTableSql);

            // Invited Players Table
            String invitesTableSql = "CREATE TABLE IF NOT EXISTS auction_invites (" +
                    "auction_id TEXT NOT NULL," +
                    "player_id TEXT NOT NULL," +
                    "PRIMARY KEY(auction_id, player_id)," +
                    "FOREIGN KEY(auction_id) REFERENCES auctions(auction_id)" +
                    ");";
            statement.execute(invitesTableSql);

            // Item Claims table for offline players
            String claimsTableSql = "CREATE TABLE IF NOT EXISTS item_claims (" +
                    "claim_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_id TEXT NOT NULL," +
                    "item_data BLOB NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "timestamp INTEGER NOT NULL" +
                    ");";
            statement.execute(claimsTableSql);


            plugin.getLogger().info("Database tables initialized successfully.");
        }
    }

    @Override
    public void saveAuction(Auction auction) {
        String sql = "INSERT INTO auctions(auction_id, seller_id, seller_name, item_data, start_time, end_time, " +
                "starting_bid, current_bid, top_bidder_id, top_bidder_name, auction_type, auction_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auction.getAuctionId().toString());
            pstmt.setString(2, auction.getSellerId().toString());
            pstmt.setString(3, auction.getSellerName());
            pstmt.setBytes(4, SerializationUtil.serializeItemStack(auction.getItem()));
            pstmt.setLong(5, auction.getStartTime());
            pstmt.setLong(6, auction.getEndTime());
            pstmt.setDouble(7, auction.getStartingBid());
            pstmt.setDouble(8, auction.getCurrentBid());
            pstmt.setString(9, auction.getTopBidderId() != null ? auction.getTopBidderId().toString() : null);
            pstmt.setString(10, auction.getTopBidderName());
            pstmt.setString(11, auction.getType().toString());
            pstmt.setString(12, auction.getStatus().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save auction " + auction.getAuctionId(), e);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not serialize item for auction " + auction.getAuctionId(), e);
        }
    }

    @Override
    public Map<UUID, Auction> loadAuctions() {
        Map<UUID, Auction> auctions = new HashMap<>();
        String sql = "SELECT * FROM auctions WHERE auction_status = 'ACTIVE' OR auction_status = 'SCHEDULED'";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID auctionId = UUID.fromString(rs.getString("auction_id"));
                List<Bid> bids = loadBidsForAuction(auctionId);
                Set<UUID> invitedPlayers = loadInvitedPlayers(auctionId);
                Auction auction = new Auction(
                        auctionId,
                        UUID.fromString(rs.getString("seller_id")),
                        rs.getString("seller_name"),
                        SerializationUtil.deserializeItemStack(rs.getBytes("item_data")),
                        rs.getLong("start_time"),
                        rs.getLong("end_time"),
                        rs.getDouble("starting_bid"),
                        rs.getDouble("current_bid"),
                        rs.getString("top_bidder_id") != null ? UUID.fromString(rs.getString("top_bidder_id")) : null,
                        rs.getString("top_bidder_name"),
                        AuctionType.valueOf(rs.getString("auction_type")),
                        AuctionStatus.valueOf(rs.getString("auction_status")),
                        bids,
                        invitedPlayers
                );
                auctions.put(auctionId, auction);
            }
            plugin.getLogger().info("Loaded " + auctions.size() + " active auctions from the database.");
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load auctions from the database.", e);
        }
        return auctions;
    }

    private List<Bid> loadBidsForAuction(UUID auctionId) throws SQLException {
        List<Bid> bids = new ArrayList<>();
        String sql = "SELECT * FROM bids WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Bid bid = new Bid(
                            UUID.fromString(rs.getString("bidder_id")),
                            rs.getString("bidder_name"),
                            rs.getDouble("amount"),
                            rs.getLong("timestamp")
                    );
                    bids.add(bid);
                }
            }
        }
        return bids;
    }

    @Override
    public void saveBid(Bid bid, UUID auctionId) {
        String sql = "INSERT INTO bids(auction_id, bidder_id, bidder_name, amount, timestamp) VALUES(?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            pstmt.setString(2, bid.getBidderId().toString());
            pstmt.setString(3, bid.getBidderName());
            pstmt.setDouble(4, bid.getAmount());
            pstmt.setLong(5, bid.getTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save bid for auction " + auctionId, e);
        }
    }

    @Override
    public void updateAuctionBid(Auction auction) {
        String sql = "UPDATE auctions SET current_bid = ?, top_bidder_id = ?, top_bidder_name = ? WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, auction.getCurrentBid());
            pstmt.setString(2, auction.getTopBidderId() != null ? auction.getTopBidderId().toString() : null);
            pstmt.setString(3, auction.getTopBidderName());
            pstmt.setString(4, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update auction bid for " + auction.getAuctionId(), e);
        }
    }

    @Override
    public void updateAuctionStatus(Auction auction) {
        String sql = "UPDATE auctions SET auction_status = ? WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auction.getStatus().toString());
            pstmt.setString(2, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update auction status for " + auction.getAuctionId(), e);
        }
    }

    @Override
    public void updateAuctionEndTime(Auction auction) {
        String sql = "UPDATE auctions SET end_time = ? WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, auction.getEndTime());
            pstmt.setString(2, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update auction end time for " + auction.getAuctionId(), e);
        }
    }

    @Override
    public List<Auction> loadPlayerHistory(UUID playerId) {
        List<Auction> history = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE auction_status = 'FINISHED' AND (seller_id = ? OR top_bidder_id = ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                UUID auctionId = UUID.fromString(rs.getString("auction_id"));
                List<Bid> bids = loadBidsForAuction(auctionId);
                Set<UUID> invitedPlayers = loadInvitedPlayers(auctionId);
                Auction auction = new Auction(
                        auctionId,
                        UUID.fromString(rs.getString("seller_id")),
                        rs.getString("seller_name"),
                        SerializationUtil.deserializeItemStack(rs.getBytes("item_data")),
                        rs.getLong("start_time"),
                        rs.getLong("end_time"),
                        rs.getDouble("starting_bid"),
                        rs.getDouble("current_bid"),
                        rs.getString("top_bidder_id") != null ? UUID.fromString(rs.getString("top_bidder_id")) : null,
                        rs.getString("top_bidder_name"),
                        AuctionType.valueOf(rs.getString("auction_type")),
                        AuctionStatus.valueOf(rs.getString("auction_status")),
                        bids,
                        invitedPlayers
                );
                history.add(auction);
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load history for player " + playerId, e);
        }
        return history;
    }

    @Override
    public void saveInvitedPlayer(UUID auctionId, UUID playerId) {
        String sql = "INSERT OR IGNORE INTO auction_invites(auction_id, player_id) VALUES(?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save invitation for auction " + auctionId, e);
        }
    }

    private Set<UUID> loadInvitedPlayers(UUID auctionId) throws SQLException {
        Set<UUID> invited = new HashSet<>();
        String sql = "SELECT player_id FROM auction_invites WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    invited.add(UUID.fromString(rs.getString("player_id")));
                }
            }
        }
        return invited;
    }

    @Override
    public void saveClaim(UUID playerId, ItemStack item, String reason) {
        String sql = "INSERT INTO item_claims(player_id, item_data, reason, timestamp) VALUES(?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setBytes(2, SerializationUtil.serializeItemStack(item));
            pstmt.setString(3, reason);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save item claim for player " + playerId, e);
        }
    }

    @Override
    public Map<Integer, ItemStack> getPlayerClaims(UUID playerId) {
        Map<Integer, ItemStack> claims = new HashMap<>();
        String sql = "SELECT claim_id, item_data FROM item_claims WHERE player_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int claimId = rs.getInt("claim_id");
                    ItemStack item = SerializationUtil.deserializeItemStack(rs.getBytes("item_data"));
                    claims.put(claimId, item);
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load claims for player " + playerId, e);
        }
        return claims;
    }

    @Override
    public void deleteClaim(int claimId) {
        String sql = "DELETE FROM item_claims WHERE claim_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, claimId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete claim " + claimId, e);
        }
    }
}
