package com.julesmc.subastas.database;

import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.objects.AuctionItem;
import com.julesmc.subastas.managers.LocaleManager;
import com.julesmc.subastas.utils.SerializationUtil; // Assuming this is where it is

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final SubastasPlugin plugin;
    private final LocaleManager localeManager;
    private Connection connection;
    private final String dbName;

    public DatabaseManager(SubastasPlugin plugin, LocaleManager localeManager) {
        this.plugin = plugin;
        this.localeManager = localeManager;
        this.dbName = plugin.getConfigManager().getString("database.sqlite.file", "subastas.db");
    }

    public synchronized boolean connect() {
        if (connection != null) {
            return true; // Already connected
        }
        File dataFolder = new File(plugin.getDataFolder(), dbName);
        if (!dataFolder.exists()) {
            try {
                if (!dataFolder.getParentFile().exists()) {
                    dataFolder.getParentFile().mkdirs();
                }
                dataFolder.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-connect-error", "error", e.getMessage()), e);
                return false;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            plugin.getLogger().info("SQLite connection established.");
            createAuctionsTable();
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-connect-error", "error", e.getMessage()), e);
            return false;
        }
    }

    public synchronized void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("SQLite connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error closing SQLite connection: " + e.getMessage(), e);
            }
        }
    }

    public Connection getConnection() {
        // Optionally check if connection is valid and reconnect if necessary
        try {
            if (connection == null || connection.isClosed()) {
                plugin.getLogger().info("Database connection was closed or null, attempting to reconnect...");
                connect();
            }
        } catch (SQLException e) {
             plugin.getLogger().log(Level.WARNING, "Error checking database connection status: " + e.getMessage());
        }
        return connection;
    }

    private void createAuctionsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS auctions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "seller_uuid VARCHAR(36) NOT NULL,"
                + "seller_name VARCHAR(16) NOT NULL,"
                + "item_serialized TEXT NOT NULL,"
                + "item_name VARCHAR(255),"
                + "initial_price DOUBLE NOT NULL,"
                + "buy_now_price DOUBLE,"
                + "current_bid DOUBLE,"
                + "highest_bidder_uuid VARCHAR(36),"
                + "highest_bidder_name VARCHAR(16),"
                + "start_time BIGINT NOT NULL,"
                + "duration_seconds INT NOT NULL,"
                + "end_time BIGINT NOT NULL,"
                + "status VARCHAR(20) NOT NULL,"
                + "claimed_seller BOOLEAN NOT NULL DEFAULT 0,"
                + "claimed_buyer BOOLEAN NOT NULL DEFAULT 0"
                + ");";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create auctions table: " + e.getMessage(), e);
        }
    }

    public int createAuction(AuctionItem auction) {
        String sql = "INSERT INTO auctions(seller_uuid, seller_name, item_serialized, item_name, initial_price, buy_now_price, start_time, duration_seconds, end_time, status) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, auction.getSellerUuid().toString());
            pstmt.setString(2, auction.getSellerName());
            pstmt.setString(3, auction.getItemSerialized());
            pstmt.setString(4, auction.getItemName());
            pstmt.setDouble(5, auction.getInitialPrice());
            if (auction.getBuyNowPrice() != null) {
                pstmt.setDouble(6, auction.getBuyNowPrice());
            } else {
                pstmt.setNull(6, Types.DOUBLE);
            }
            pstmt.setLong(7, auction.getStartTime());
            pstmt.setInt(8, auction.getDurationSeconds());
            pstmt.setLong(9, auction.getEndTime());
            pstmt.setString(10, auction.getStatus());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
        }
        return -1; // Indicate failure
    }

    public AuctionItem getAuctionById(int id) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToAuctionItem(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
        }
        return null;
    }

    public List<AuctionItem> getActiveAuctions() {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE' ORDER BY end_time ASC"; // Example ordering
        try (Statement stmt = getConnection().createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                auctions.add(mapResultSetToAuctionItem(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
        }
        return auctions;
    }

    public List<AuctionItem> getAuctionsBySeller(UUID sellerUuid) {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE seller_uuid = ? ORDER BY start_time DESC";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, sellerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                auctions.add(mapResultSetToAuctionItem(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
        }
        return auctions;
    }

    public List<AuctionItem> getAuctionsByBidder(UUID bidderUuid) {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE highest_bidder_uuid = ? AND status = 'ACTIVE' ORDER BY end_time ASC";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, bidderUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                auctions.add(mapResultSetToAuctionItem(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
        }
        return auctions;
    }


    public boolean updateAuctionStatus(int id, String status) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
            return false;
        }
    }

    public boolean updateAuctionBid(int id, double bid, UUID bidderUuid, String bidderName) {
        String sql = "UPDATE auctions SET current_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ? WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setDouble(1, bid);
            pstmt.setString(2, bidderUuid.toString());
            pstmt.setString(3, bidderName);
            pstmt.setInt(4, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
            return false;
        }
    }

    public boolean updateAuctionClaimStatus(int id, boolean isSellerClaim, boolean claimedStatus) {
        String columnToUpdate = isSellerClaim ? "claimed_seller" : "claimed_buyer";
        String sql = "UPDATE auctions SET " + columnToUpdate + " = ? WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setBoolean(1, claimedStatus);
            pstmt.setInt(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.database-query-error", "error", e.getMessage()), e);
            return false;
        }
    }


    private AuctionItem mapResultSetToAuctionItem(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
        String sellerName = rs.getString("seller_name");
        String itemSerialized = rs.getString("item_serialized");
        String itemName = rs.getString("item_name");
        double initialPrice = rs.getDouble("initial_price");
        Double buyNowPrice = rs.getObject("buy_now_price") != null ? rs.getDouble("buy_now_price") : null;
        Double currentBid = rs.getObject("current_bid") != null ? rs.getDouble("current_bid") : null;
        UUID highestBidderUuid = rs.getString("highest_bidder_uuid") != null ? UUID.fromString(rs.getString("highest_bidder_uuid")) : null;
        String highestBidderName = rs.getString("highest_bidder_name");
        long startTime = rs.getLong("start_time");
        int durationSeconds = rs.getInt("duration_seconds");
        long endTime = rs.getLong("end_time");
        String status = rs.getString("status");
        boolean claimedSeller = rs.getBoolean("claimed_seller");
        boolean claimedBuyer = rs.getBoolean("claimed_buyer");

        AuctionItem item = new AuctionItem(id, sellerUuid, sellerName, itemSerialized, itemName, initialPrice, buyNowPrice,
                currentBid, highestBidderUuid, highestBidderName, startTime, durationSeconds, endTime, status,
                claimedSeller, claimedBuyer);

        // Deserialize ItemStack - this can be done here or lazily when getItemStack() is called
        try {
            item.associateItemStack(SerializationUtil.itemStackFromBase64(itemSerialized));
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, localeManager.getRawMessage("error.item-serialization-error", "error", "deserializing item ID " + id + ": " + e.getMessage()), e);
            // Depending on policy, might want to mark this auction as invalid or handle it
        }
        return item;
    }
}
