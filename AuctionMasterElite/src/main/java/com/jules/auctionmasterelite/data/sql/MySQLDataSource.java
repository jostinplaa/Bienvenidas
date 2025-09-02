package com.jules.auctionmasterelite.data.sql;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.data.Bid;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MySQLDataSource implements DataSource {

    private final AuctionMasterElite plugin;
    private HikariDataSource hikari;

    public MySQLDataSource(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException {
        HikariConfig config = new HikariConfig();
        // Configuration will be loaded from config.yml
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port");
        String database = plugin.getConfig().getString("database.mysql.database");
        String username = plugin.getConfig().getString("database.mysql.username");
        String password = plugin.getConfig().getString("database.mysql.password");

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.hikari = new HikariDataSource(config);
        plugin.getLogger().info("Successfully connected to MySQL database.");
    }

    @Override
    public void disconnect() {
        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
    }

    @Override
    public void initializeDatabase() {
        String auctionsTableSql = "CREATE TABLE IF NOT EXISTS auctions (" +
                "auction_id VARCHAR(36) PRIMARY KEY," +
                "seller_id VARCHAR(36) NOT NULL," +
                "seller_name VARCHAR(16) NOT NULL," +
                "item_data LONGBLOB NOT NULL," +
                "start_time BIGINT NOT NULL," +
                "end_time BIGINT NOT NULL," +
                "starting_bid DOUBLE NOT NULL," +
                "current_bid DOUBLE NOT NULL," +
                "top_bidder_id VARCHAR(36)," +
                "top_bidder_name VARCHAR(16)," +
                "auction_type VARCHAR(32) NOT NULL," +
                "auction_status VARCHAR(32) NOT NULL" +
                ");";

        String bidsTableSql = "CREATE TABLE IF NOT EXISTS bids (" +
                "bid_id INT AUTO_INCREMENT PRIMARY KEY," +
                "auction_id VARCHAR(36) NOT NULL," +
                "bidder_id VARCHAR(36) NOT NULL," +
                "bidder_name VARCHAR(16) NOT NULL," +
                "amount DOUBLE NOT NULL," +
                "timestamp BIGINT NOT NULL," +
                "FOREIGN KEY(auction_id) REFERENCES auctions(auction_id)" +
                ");";

        String invitesTableSql = "CREATE TABLE IF NOT EXISTS auction_invites (" +
                "auction_id VARCHAR(36) NOT NULL," +
                "player_id VARCHAR(36) NOT NULL," +
                "PRIMARY KEY(auction_id, player_id)," +
                "FOREIGN KEY(auction_id) REFERENCES auctions(auction_id)" +
                ");";

        String claimsTableSql = "CREATE TABLE IF NOT EXISTS item_claims (" +
                "claim_id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_id VARCHAR(36) NOT NULL," +
                "item_data LONGBLOB NOT NULL," +
                "reason VARCHAR(255) NOT NULL," +
                "timestamp BIGINT NOT NULL" +
                ");";

        try (Connection conn = getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(auctionsTableSql);
            stmt.execute(bidsTableSql);
            stmt.execute(invitesTableSql);
            stmt.execute(claimsTableSql);
            plugin.getLogger().info("MySQL database tables initialized successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not initialize MySQL database tables.", e);
        }
    }

    @Override
    public void saveAuction(Auction auction) {
        String sql = "INSERT INTO auctions(auction_id, seller_id, seller_name, item_data, start_time, end_time, " +
                "starting_bid, current_bid, top_bidder_id, top_bidder_name, auction_type, auction_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auction.getAuctionId().toString());
            pstmt.setString(2, auction.getSellerId().toString());
            pstmt.setString(3, auction.getSellerName());
            pstmt.setBytes(4, com.jules.auctionmasterelite.util.SerializationUtil.serializeItemStack(auction.getItem()));
            pstmt.setLong(5, auction.getStartTime());
            pstmt.setLong(6, auction.getEndTime());
            pstmt.setDouble(7, auction.getStartingBid());
            pstmt.setDouble(8, auction.getCurrentBid());
            pstmt.setString(9, auction.getTopBidderId() != null ? auction.getTopBidderId().toString() : null);
            pstmt.setString(10, auction.getTopBidderName());
            pstmt.setString(11, auction.getType().toString());
            pstmt.setString(12, auction.getStatus().toString());
            pstmt.executeUpdate();
        } catch (SQLException | java.io.IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save auction " + auction.getAuctionId(), e);
        }
    }

    @Override
    public Map<UUID, Auction> loadAuctions() {
        Map<UUID, Auction> auctions = new java.util.HashMap<>();
        String sql = "SELECT * FROM auctions WHERE auction_status = 'ACTIVE' OR auction_status = 'SCHEDULED'";

        try (Connection conn = getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID auctionId = UUID.fromString(rs.getString("auction_id"));
                List<Bid> bids = loadBidsForAuction(auctionId);
                java.util.Set<UUID> invitedPlayers = loadInvitedPlayers(auctionId);
                Auction auction = new Auction(
                        auctionId,
                        UUID.fromString(rs.getString("seller_id")),
                        rs.getString("seller_name"),
                        com.jules.auctionmasterelite.util.SerializationUtil.deserializeItemStack(rs.getBytes("item_data")),
                        rs.getLong("start_time"),
                        rs.getLong("end_time"),
                        rs.getDouble("starting_bid"),
                        rs.getDouble("current_bid"),
                        rs.getString("top_bidder_id") != null ? UUID.fromString(rs.getString("top_bidder_id")) : null,
                        rs.getString("top_bidder_name"),
                        com.jules.auctionmasterelite.data.AuctionType.valueOf(rs.getString("auction_type")),
                        com.jules.auctionmasterelite.data.AuctionStatus.valueOf(rs.getString("auction_status")),
                        bids,
                        invitedPlayers
                );
                auctions.put(auctionId, auction);
            }
            plugin.getLogger().info("Loaded " + auctions.size() + " active auctions from the database.");
        } catch (SQLException | java.io.IOException | ClassNotFoundException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load auctions from the database.", e);
        }
        return auctions;
    }

    private List<Bid> loadBidsForAuction(UUID auctionId) throws SQLException {
        List<Bid> bids = new java.util.ArrayList<>();
        String sql = "SELECT * FROM bids WHERE auction_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
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
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            pstmt.setString(2, bid.getBidderId().toString());
            pstmt.setString(3, bid.getBidderName());
            pstmt.setDouble(4, bid.getAmount());
            pstmt.setLong(5, bid.getTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save bid for auction " + auctionId, e);
        }
    }

    @Override
    public void updateAuctionBid(Auction auction) {
        String sql = "UPDATE auctions SET current_bid = ?, top_bidder_id = ?, top_bidder_name = ? WHERE auction_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, auction.getCurrentBid());
            pstmt.setString(2, auction.getTopBidderId() != null ? auction.getTopBidderId().toString() : null);
            pstmt.setString(3, auction.getTopBidderName());
            pstmt.setString(4, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not update auction bid for " + auction.getAuctionId(), e);
        }
    }

    @Override
    public void updateAuctionStatus(Auction auction) {
        String sql = "UPDATE auctions SET auction_status = ? WHERE auction_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auction.getStatus().toString());
            pstmt.setString(2, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not update auction status for " + auction.getAuctionId(), e);
        }
    }

    @Override
    public void updateAuctionEndTime(Auction auction) {
        String sql = "UPDATE auctions SET end_time = ? WHERE auction_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, auction.getEndTime());
            pstmt.setString(2, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not update auction end time for " + auction.getAuctionId(), e);
        }
    }

    @Override
    public List<Auction> loadPlayerHistory(UUID playerId) {
        List<Auction> history = new java.util.ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE auction_status = 'FINISHED' AND (seller_id = ? OR top_bidder_id = ?)";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, playerId.toString());
            java.sql.ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                UUID auctionId = UUID.fromString(rs.getString("auction_id"));
                List<Bid> bids = loadBidsForAuction(auctionId);
                java.util.Set<UUID> invitedPlayers = loadInvitedPlayers(auctionId);
                Auction auction = new Auction(
                        auctionId,
                        UUID.fromString(rs.getString("seller_id")),
                        rs.getString("seller_name"),
                        com.jules.auctionmasterelite.util.SerializationUtil.deserializeItemStack(rs.getBytes("item_data")),
                        rs.getLong("start_time"),
                        rs.getLong("end_time"),
                        rs.getDouble("starting_bid"),
                        rs.getDouble("current_bid"),
                        rs.getString("top_bidder_id") != null ? UUID.fromString(rs.getString("top_bidder_id")) : null,
                        rs.getString("top_bidder_name"),
                        com.jules.auctionmasterelite.data.AuctionType.valueOf(rs.getString("auction_type")),
                        com.jules.auctionmasterelite.data.AuctionStatus.valueOf(rs.getString("auction_status")),
                        bids,
                        invitedPlayers
                );
                history.add(auction);
            }
        } catch (SQLException | java.io.IOException | ClassNotFoundException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load history for player " + playerId, e);
        }
        return history;
    }

    @Override
    public void saveInvitedPlayer(UUID auctionId, UUID playerId) {
        String sql = "INSERT IGNORE INTO auction_invites(auction_id, player_id) VALUES(?,?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save invitation for auction " + auctionId, e);
        }
    }

    private java.util.Set<UUID> loadInvitedPlayers(UUID auctionId) throws SQLException {
        java.util.Set<UUID> invited = new java.util.HashSet<>();
        String sql = "SELECT player_id FROM auction_invites WHERE auction_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
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
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setBytes(2, com.jules.auctionmasterelite.util.SerializationUtil.serializeItemStack(item));
            pstmt.setString(3, reason);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException | java.io.IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save item claim for player " + playerId, e);
        }
    }

    @Override
    public Map<Integer, ItemStack> getPlayerClaims(UUID playerId) {
        Map<Integer, ItemStack> claims = new java.util.HashMap<>();
        String sql = "SELECT claim_id, item_data FROM item_claims WHERE player_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int claimId = rs.getInt("claim_id");
                    ItemStack item = com.jules.auctionmasterelite.util.SerializationUtil.deserializeItemStack(rs.getBytes("item_data"));
                    claims.put(claimId, item);
                }
            }
        } catch (SQLException | java.io.IOException | ClassNotFoundException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load claims for player " + playerId, e);
        }
        return claims;
    }

    @Override
    public void deleteClaim(int claimId) {
        String sql = "DELETE FROM item_claims WHERE claim_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, claimId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not delete claim " + claimId, e);
        }
    }

    public Connection getConnection() throws SQLException {
        return hikari.getConnection();
    }
}
