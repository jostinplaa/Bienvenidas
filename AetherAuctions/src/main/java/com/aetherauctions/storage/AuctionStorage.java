package com.aetherauctions.storage;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.model.Bid; // Now needed for List<Bid>
import com.aetherauctions.auction.AuctionStatus; // Import AuctionStatus
import com.aetherauctions.util.SerializationUtil;
import org.bukkit.inventory.ItemStack; // For casting and placeholder
import org.bukkit.Material; // For placeholder ItemStack

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet; // Import ResultSet
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList; // Import ArrayList
import java.util.List;    // Import List
import java.util.UUID;    // Import UUID
import java.util.logging.Level;

public class AuctionStorage {
    private final AetherAuctions plugin;
    private Connection connection;
    private final String dbName = "auctions.db"; // Database file name

    public AuctionStorage(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    public void initDatabase() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                plugin.getLogger().severe("Could not create plugin data folder!");
                throw new SQLException("Could not create data folder.");
            }
        }

        File dbFile = new File(dataFolder, dbName);
        boolean firstTime = !dbFile.exists();

        if (firstTime) {
            try {
                if (!dbFile.createNewFile()) {
                    plugin.getLogger().severe("Could not create database file!");
                    throw new SQLException("Could not create database file.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "IO exception while creating database file.", e);
                throw new SQLException("IO exception while creating database file.", e);
            }
        }

        try {
            Class.forName("org.sqlite.JDBC"); // Load SQLite JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("Connection to SQLite database established.");

            if (firstTime) {
                plugin.getLogger().info("Creating database tables for the first time...");
            }
            createTables(); // Create tables if they don't exist

        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found. Ensure it's included in the plugin JAR.", e);
            throw new SQLException("SQLite JDBC driver not found.", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database.", e);
            throw e; // Re-throw to be handled by AetherAuctions.java
        }
    }

    public void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing the database connection.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        // Check if the connection is valid before returning it.
        // The 'timeout' parameter is in seconds.
        if (connection == null || !connection.isValid(1)) { // Check validity, timeout of 1 second
            plugin.getLogger().warning("La conexión a SQLite no es válida o está cerrada. Intentando reestablecer...");

            File dbFile = new File(plugin.getDataFolder(), dbName);
            if (!dbFile.exists()) {
                plugin.getLogger().severe("El archivo de la base de datos no existe. No se puede reconectar.");
                throw new SQLException("El archivo de la base de datos no existe al intentar reconectar.");
            }
            try {
                Class.forName("org.sqlite.JDBC"); // Ensure driver is loaded
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                plugin.getLogger().info("Conexión a SQLite reestablecida.");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Driver JDBC de SQLite no encontrado durante la reconexión.", e);
                throw new SQLException("Driver JDBC de SQLite no encontrado durante la reconexión.", e);
            } catch (SQLException e) { // Catch SQLException specifically for the getConnection attempt
                plugin.getLogger().log(Level.SEVERE, "Fallo al reestablecer la conexión a SQLite.", e);
                throw e; // Re-throw to notify the calling method of the failure
            }
        }
        return connection;
    }

    private void createTables() throws SQLException {
        String sqlCreateAuctionsTable = "CREATE TABLE IF NOT EXISTS auctions ("
                                      + "id TEXT PRIMARY KEY NOT NULL,"         // UUID as String
                                      + "seller_id TEXT NOT NULL,"              // UUID as String
                                      + "seller_name TEXT NOT NULL,"
                                      + "itemstack_data TEXT NOT NULL,"         // ItemStack serialized (Base64 or JSON)
                                      + "current_bid REAL NOT NULL,"
                                      + "highest_bidder_id TEXT,"             // UUID as String
                                      + "highest_bidder_name TEXT,"
                                      + "buy_now_price REAL NOT NULL,"          // -1 if no buy now
                                      + "creation_timestamp INTEGER NOT NULL,"
                                      + "expiration_timestamp INTEGER NOT NULL,"
                                      + "status TEXT NOT NULL,"                 // Name of AuctionStatus enum
                                      + "bid_history_json TEXT"                 // Bid history as JSON String
                                      + ");";

        // Example for separate bids table (not used for now, bid_history_json is used)
        /*
        String sqlCreateBidsTable = "CREATE TABLE IF NOT EXISTS bids ("
                                  + "bid_id TEXT PRIMARY KEY NOT NULL," // UUID for bid
                                  + "auction_id TEXT NOT NULL,"      // FK to auctions.id
                                  + "bidder_id TEXT NOT NULL,"
                                  + "bidder_name TEXT NOT NULL,"
                                  + "amount REAL NOT NULL,"
                                  + "timestamp INTEGER NOT NULL,"
                                  + "FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE"
                                  + ");";
        */

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sqlCreateAuctionsTable);
            // if using separate bids table: stmt.execute(sqlCreateBidsTable);
            plugin.getLogger().info("Database tables verified/created.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating database tables.", e);
            throw e;
        }
    }

    public void saveAuction(Auction auction) throws SQLException {
        if (auction == null) {
            plugin.getLogger().warning("Attempted to save a null auction.");
            return;
        }

        String sql = "INSERT OR REPLACE INTO auctions (id, seller_id, seller_name, itemstack_data, "
                   + "current_bid, highest_bidder_id, highest_bidder_name, buy_now_price, "
                   + "creation_timestamp, expiration_timestamp, status, bid_history_json) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auction.getId().toString());
            pstmt.setString(2, auction.getSellerId().toString());
            pstmt.setString(3, auction.getSellerName());

            String itemStackData = SerializationUtil.itemStackToBase64(auction.getItemStack());
            pstmt.setString(4, itemStackData);

            pstmt.setDouble(5, auction.getCurrentBid());
            pstmt.setString(6, auction.getHighestBidderId() != null ? auction.getHighestBidderId().toString() : null);
            pstmt.setString(7, auction.getHighestBidderName());
            pstmt.setDouble(8, auction.getBuyNowPrice());
            pstmt.setLong(9, auction.getCreationTimestamp());
            pstmt.setLong(10, auction.getExpirationTimestamp());
            pstmt.setString(11, auction.getStatus().name());

            String bidHistoryJson = SerializationUtil.bidListToJson(auction.getBidHistory());
            pstmt.setString(12, bidHistoryJson);

            pstmt.executeUpdate();
            // plugin.getLogger().info("Auction ID: " + auction.getId() + " saved/updated in the database.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving auction ID: " + auction.getId() + " to the database.", e);
            throw e;
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "Serialization error while saving auction ID: " + auction.getId(), e);
            throw new SQLException("Serialization error while saving auction.", e);
        }
    }

    private Auction mapResultSetToAuction(ResultSet rs) throws SQLException, IOException, ClassNotFoundException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID sellerId = UUID.fromString(rs.getString("seller_id"));
        String sellerName = rs.getString("seller_name");

        ItemStack itemStack = SerializationUtil.itemStackFromBase64(rs.getString("itemstack_data"));

        double currentBid = rs.getDouble("current_bid");
        String highestBidderIdStr = rs.getString("highest_bidder_id");
        UUID highestBidderId = highestBidderIdStr != null ? UUID.fromString(highestBidderIdStr) : null;
        String highestBidderName = rs.getString("highest_bidder_name");
        double buyNowPrice = rs.getDouble("buy_now_price");
        long creationTimestamp = rs.getLong("creation_timestamp");
        long expirationTimestamp = rs.getLong("expiration_timestamp");
        AuctionStatus status;
        try {
            status = AuctionStatus.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Invalid status in database for auction ID: " + id + ". Status: " + rs.getString("status"), e);
            status = AuctionStatus.CANCELLED; // Default to a safe, inactive status
        }


        List<Bid> bidHistory = SerializationUtil.bidListFromJson(rs.getString("bid_history_json"));

        // The constructor of Auction sets currentBid to startPrice. We need to preserve the actual currentBid from DB.
        // We also need to ensure itemStack is handled if null (e.g. deserialization failed)
        ItemStack effectiveItemStack = itemStack;
        if (itemStack == null) {
             plugin.getLogger().warning("ItemStack deserialized to null for auction ID: " + id + ". Using a placeholder BARRIER item.");
             effectiveItemStack = new ItemStack(Material.BARRIER, 1);
        }

        Auction auction = new Auction(
            id,
            sellerId,
            sellerName,
            effectiveItemStack,
            0, // startPrice - currentBid from DB is the authoritative value here. This constructor value will be overridden.
            buyNowPrice,
            creationTimestamp,
            expirationTimestamp
        );

        auction.setCurrentBid(currentBid);
        auction.setHighestBidderId(highestBidderId);
        auction.setHighestBidderName(highestBidderName);
        auction.setStatus(status);
        auction.setBidHistory(bidHistory);
        // If itemStack was originally null and we used a placeholder, ensure the Auction object has the placeholder.
        // The constructor already received effectiveItemStack. If effectiveItemStack was the placeholder, it's set.
        // If it was null initially, the log above already warned, and now it has BARRIER.

        return auction;
    }

    public Auction loadAuction(UUID auctionId) {
        if (auctionId == null) return null;

        String sql = "SELECT * FROM auctions WHERE id = ?;";
        Auction auction = null;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                auction = mapResultSetToAuction(rs);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading auction ID: " + auctionId + " from the database.", e);
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Deserialization error loading auction ID: " + auctionId, e);
        }
        return auction;
    }

    public List<Auction> loadActiveAuctions() {
        List<Auction> activeAuctionsList = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, AuctionStatus.ACTIVE.name());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                try {
                    Auction auction = mapResultSetToAuction(rs);
                    if (auction != null) { // mapResultSetToAuction might return null if critical data is missing/corrupt
                        activeAuctionsList.add(auction);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE, "Deserialization error loading an active auction (ID: " + rs.getString("id") + "). Skipping this auction.", e);
                } catch (IllegalArgumentException e) { // Catching potential Enum parsing error from mapResultSetToAuction if status is very wrong
                     plugin.getLogger().log(Level.SEVERE, "Invalid data (e.g. status) for auction ID: " + rs.getString("id") + ". Skipping this auction.", e);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading active auctions from the database.", e);
        }
        plugin.getLogger().info("Loaded " + activeAuctionsList.size() + " active auctions from the database.");
        return activeAuctionsList;
    }

    public Auction loadAuctionFuzzy(String idStr) {
        if (idStr == null || idStr.isEmpty()) return null;
        Auction auction = null;

        // First, try as full UUID
        try {
            UUID fullUuid = UUID.fromString(idStr);
            return loadAuction(fullUuid);
        } catch (IllegalArgumentException e) {
            // Not a full UUID, proceed to try as a prefix
        }

        // Try as a prefix (simple LIKE query)
        // This is not perfectly accurate for UUID prefixes if the prefix is very short and could match other parts of a UUID string.
        // A more robust way would be to fetch all and filter in Java, but that's less efficient.
        // For admin usage, this might be acceptable.
        String sql = "SELECT * FROM auctions WHERE id LIKE ? LIMIT 1;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, idStr + "%");
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                auction = mapResultSetToAuction(rs);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading auction by fuzzy ID prefix: " + idStr, e);
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Deserialization error loading auction by fuzzy ID prefix: " + idStr, e);
        }
        return auction;
    }
}
