package com.example.aetherauctions.database;

import com.example.aetherauctions.AetherAuctions;
import com.example.aetherauctions.auction.Auction;
import com.example.aetherauctions.auction.AuctionStatus;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

public class DatabaseManager {

    private final AetherAuctions plugin;
    private Connection connection;
    private final String dbName;

    public DatabaseManager(AetherAuctions plugin) {
        this.plugin = plugin;
        this.dbName = plugin.getConfig().getString("database.file", "auctions.db");
    }

    public synchronized void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                plugin.getLogger().severe("Could not create plugin data folder!");
                throw new SQLException("Could not create plugin data folder.");
            }
        }
        File dbFile = new File(dataFolder, dbName);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        plugin.getLogger().info("Successfully connected to SQLite database: " + dbName);
    }

    public synchronized void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("Disconnected from SQLite database.");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error disconnecting from SQLite database", e);
            }
        }
    }

    public void initializeDatabase() {
        try {
            if (connection == null || connection.isClosed()) {
                 connect();
            }
            String sql = "CREATE TABLE IF NOT EXISTS auctions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "seller_uuid TEXT NOT NULL," +
                    "seller_name TEXT NOT NULL," +
                    "item_serialized TEXT NOT NULL," +
                    "item_name TEXT," +
                    "start_price REAL NOT NULL," +
                    "current_bid REAL DEFAULT 0," +
                    "highest_bidder_uuid TEXT," +
                    "highest_bidder_name TEXT," +
                    "buy_now_price REAL," +
                    "start_time INTEGER NOT NULL," +
                    "end_time INTEGER NOT NULL," +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                    "claimed_by_seller BOOLEAN DEFAULT FALSE," +
                    "claimed_by_winner BOOLEAN DEFAULT FALSE" +
                    ");";
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
                plugin.getLogger().info("Auctions table initialized successfully.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database", e);
        }
    }

    public Auction createAuction(Auction auction) {
        String sql = "INSERT INTO auctions(seller_uuid, seller_name, item_serialized, item_name, start_price, " +
                     "buy_now_price, start_time, end_time, status, current_bid, highest_bidder_uuid, highest_bidder_name, " +
                     "claimed_by_seller, claimed_by_winner) " +
                     "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, auction.getSellerUuid());
            pstmt.setString(2, auction.getSellerName());
            pstmt.setString(3, serializeItemStack(auction.getItemStack()));
            pstmt.setString(4, auction.getItemName());
            pstmt.setDouble(5, auction.getStartPrice());
            if (auction.getBuyNowPrice() != null) {
                pstmt.setDouble(6, auction.getBuyNowPrice());
            } else {
                pstmt.setNull(6, Types.REAL);
            }
            pstmt.setLong(7, auction.getStartTime());
            pstmt.setLong(8, auction.getEndTime());
            pstmt.setString(9, auction.getStatus().name());
            pstmt.setDouble(10, auction.getCurrentBid());
            pstmt.setString(11, auction.getHighestBidderUuid());
            pstmt.setString(12, auction.getHighestBidderName());
            pstmt.setBoolean(13, auction.isClaimedBySeller());
            pstmt.setBoolean(14, auction.isClaimedByWinner());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        auction.setId(generatedKeys.getInt(1));
                        return auction;
                    }
                }
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create auction in database", e);
        }
        return null;
    }

    public Auction getAuction(int auctionId) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapRowToAuction(rs);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve auction " + auctionId, e);
        }
        return null;
    }

    private Auction mapRowToAuction(ResultSet rs) throws SQLException, IOException {
        int id = rs.getInt("id");
        String sellerUuid = rs.getString("seller_uuid");
        String sellerName = rs.getString("seller_name");
        String itemSerialized = rs.getString("item_serialized");
        String itemName = rs.getString("item_name");
        double startPrice = rs.getDouble("start_price");
        double currentBid = rs.getDouble("current_bid");
        String highestBidderUuid = rs.getString("highest_bidder_uuid");
        String highestBidderName = rs.getString("highest_bidder_name");
        Double buyNowPrice = rs.getDouble("buy_now_price");
        if (rs.wasNull()) buyNowPrice = null;
        long startTime = rs.getLong("start_time");
        long endTime = rs.getLong("end_time");
        AuctionStatus status = AuctionStatus.fromString(rs.getString("status"));
        boolean claimedBySeller = rs.getBoolean("claimed_by_seller");
        boolean claimedByWinner = rs.getBoolean("claimed_by_winner");

        ItemStack itemStack = deserializeItemStack(itemSerialized);
        if (itemStack == null) {
            plugin.getLogger().warning("Failed to deserialize ItemStack for auction ID: " + id);
            // Potentially handle this error more gracefully, e.g. by setting a placeholder item or skipping the auction
        }

        return new Auction(id, sellerUuid, sellerName, itemStack, itemName, startPrice, currentBid,
                highestBidderUuid, highestBidderName, buyNowPrice, startTime, endTime, status,
                claimedBySeller, claimedByWinner);
    }

    public List<Auction> getActiveAuctions(int page, int pageSize) {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE' ORDER BY end_time ASC LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, pageSize);
            pstmt.setInt(2, (page - 1) * pageSize);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Auction auction = mapRowToAuction(rs);
                if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve active auctions", e);
        }
        return auctions;
    }

    public List<Auction> getAllActiveAuctions() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE' ORDER BY end_time ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Auction auction = mapRowToAuction(rs);
                 if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve all active auctions", e);
        }
        return auctions;
    }


    public List<Auction> getAuctionsByPlayer(String playerUuid, int page, int pageSize) {
        List<Auction> auctions = new ArrayList<>();
        // Fetches all auctions created by player, regardless of status, useful for "My Auctions" GUI
        String sql = "SELECT * FROM auctions WHERE seller_uuid = ? ORDER BY start_time DESC LIMIT ? OFFSET ?";
         try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid);
            pstmt.setInt(2, pageSize);
            pstmt.setInt(3, (page - 1) * pageSize);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Auction auction = mapRowToAuction(rs);
                if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve auctions for player " + playerUuid, e);
        }
        return auctions;
    }

    public List<Auction> getPlayerAuctionHistory(String playerUuid, int page, int pageSize) {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE seller_uuid = ? AND status != 'ACTIVE' ORDER BY end_time DESC LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid);
            pstmt.setInt(2, pageSize);
            pstmt.setInt(3, (page - 1) * pageSize);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                 Auction auction = mapRowToAuction(rs);
                 if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve auction history for player " + playerUuid, e);
        }
        return auctions;
    }

    public boolean updateAuction(Auction auction) {
        String sql = "UPDATE auctions SET seller_uuid = ?, seller_name = ?, item_serialized = ?, item_name = ?, " +
                     "start_price = ?, current_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ?, " +
                     "buy_now_price = ?, start_time = ?, end_time = ?, status = ?, claimed_by_seller = ?, " +
                     "claimed_by_winner = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auction.getSellerUuid());
            pstmt.setString(2, auction.getSellerName());
            pstmt.setString(3, serializeItemStack(auction.getItemStack()));
            pstmt.setString(4, auction.getItemName());
            pstmt.setDouble(5, auction.getStartPrice());
            pstmt.setDouble(6, auction.getCurrentBid());
            pstmt.setString(7, auction.getHighestBidderUuid());
            pstmt.setString(8, auction.getHighestBidderName());
            if (auction.getBuyNowPrice() != null) {
                pstmt.setDouble(9, auction.getBuyNowPrice());
            } else {
                pstmt.setNull(9, Types.REAL);
            }
            pstmt.setLong(10, auction.getStartTime());
            pstmt.setLong(11, auction.getEndTime());
            pstmt.setString(12, auction.getStatus().name());
            pstmt.setBoolean(13, auction.isClaimedBySeller());
            pstmt.setBoolean(14, auction.isClaimedByWinner());
            pstmt.setInt(15, auction.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update auction " + auction.getId(), e);
            return false;
        }
    }

    public boolean deleteAuction(int auctionId) {
        String sql = "DELETE FROM auctions WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete auction " + auctionId, e);
            return false;
        }
    }

    public List<Auction> getExpiredAuctions() {
        List<Auction> auctions = new ArrayList<>();
        long currentTime = System.currentTimeMillis() / 1000L;
        // Select auctions that are active and whose end time has passed
        String sql = "SELECT * FROM auctions WHERE end_time < ? AND status = 'ACTIVE'";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, currentTime);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Auction auction = mapRowToAuction(rs);
                if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve expired auctions", e);
        }
        return auctions;
    }

    public List<Auction> getUnclaimedItemsForSeller(String sellerUuid) {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE seller_uuid = ? AND (status = 'EXPIRED' OR status = 'CANCELLED') AND claimed_by_seller = FALSE";
         try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sellerUuid);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Auction auction = mapRowToAuction(rs);
                if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve unclaimed items for seller " + sellerUuid, e);
        }
        return auctions;
    }

    public List<Auction> getUnclaimedItemsForWinner(String winnerUuid) {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE highest_bidder_uuid = ? AND status = 'SOLD' AND claimed_by_winner = FALSE";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, winnerUuid);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Auction auction = mapRowToAuction(rs);
                if (auction != null && auction.getItemStack() != null) auctions.add(auction);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve unclaimed items for winner " + winnerUuid, e);
        }
        return auctions;
    }

    public String serializeItemStack(ItemStack item) throws IOException {
        if (item == null) return null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) { // Catch a broader exception for security manager issues or other problems
            plugin.getLogger().log(Level.SEVERE, "Could not serialize ItemStack: " + item.getType(), e);
            throw new IOException("Serialization failed for " + item.getType(), e);
        }
    }

    public ItemStack deserializeItemStack(String data) throws IOException {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object object = dataInput.readObject();
            if (object instanceof ItemStack) {
                return (ItemStack) object;
            } else {
                // This case should ideally not happen if serialization is consistent
                plugin.getLogger().severe("Deserialized object is not an ItemStack: " + (object != null ? object.getClass().getName() : "null"));
                return null;
            }
        } catch (ClassNotFoundException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not deserialize ItemStack", e);
            throw new IOException("Deserialization failed", e);
        }
    }
}
