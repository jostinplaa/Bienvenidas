package com.aetherauctions.storage;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.model.Bid;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.util.SerializationUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class AuctionStorage {
    private final AetherAuctions plugin;
    private Connection connection;
    private final String dbName = "auctions.db";

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
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("Connection to SQLite database established.");

            if (firstTime) {
                plugin.getLogger().info("Creating database tables for the first time...");
            }
            createTables();

        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found. Ensure it's included in the plugin JAR.", e);
            throw new SQLException("SQLite JDBC driver not found.", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database.", e);
            throw e;
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
        if (this.connection == null || this.connection.isClosed()) {
            plugin.getLogger().warning("Conexión a SQLite es null o está cerrada. Intentando reestablecer...");
            File dbFile = new File(plugin.getDataFolder(), dbName);
            if (!dbFile.exists()) {
                plugin.getLogger().severe("El archivo de la base de datos no existe. No se puede reconectar.");
                throw new SQLException("El archivo de la base de datos no existe al intentar reconectar.");
            }
            try {
                Class.forName("org.sqlite.JDBC");
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                plugin.getLogger().info("Conexión a SQLite reestablecida.");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Driver JDBC de SQLite no encontrado durante la reconexión.", e);
                throw new SQLException("Driver JDBC de SQLite no encontrado durante la reconexión.", e);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fallo al reestablecer la conexión a SQLite.", e);
                throw e;
            }
        }
        return this.connection;
    }

    private void createTables() throws SQLException {
        String sqlCreateAuctionsTable = "CREATE TABLE IF NOT EXISTS auctions ("
                                      + "id TEXT PRIMARY KEY NOT NULL,"
                                      + "seller_id TEXT NOT NULL,"
                                      + "seller_name TEXT NOT NULL,"
                                      + "itemstack_data TEXT NOT NULL,"
                                      + "current_bid REAL NOT NULL,"
                                      + "highest_bidder_id TEXT,"
                                      + "highest_bidder_name TEXT,"
                                      + "buy_now_price REAL NOT NULL,"
                                      + "creation_timestamp INTEGER NOT NULL,"
                                      + "expiration_timestamp INTEGER NOT NULL,"
                                      + "status TEXT NOT NULL,"
                                      + "bid_history_json TEXT"
                                      + ");";

        Connection conn = getConnection(); // Get the shared connection
        try (Statement stmt = conn.createStatement()) { // Use try-with-resources for Statement
            stmt.execute(sqlCreateAuctionsTable);
            plugin.getLogger().info("Database tables verified/created.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating database tables.", e);
            throw e;
        }
        // Do NOT close conn here
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

        Connection conn = getConnection(); // Get the shared connection
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) { // Use try-with-resources for PreparedStatement
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
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving auction ID: " + auction.getId() + " to the database.", e);
            throw e;
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "Serialization error while saving auction ID: " + auction.getId(), e);
            throw new SQLException("Serialization error while saving auction.", e);
        }
        // Do NOT close conn here
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
            status = AuctionStatus.CANCELLED;
        }
        List<Bid> bidHistory = SerializationUtil.bidListFromJson(rs.getString("bid_history_json"));
        ItemStack effectiveItemStack = itemStack;
        if (itemStack == null) {
             plugin.getLogger().warning("ItemStack deserialized to null for auction ID: " + id + ". Using a placeholder BARRIER item.");
             effectiveItemStack = new ItemStack(Material.BARRIER, 1);
        }
        Auction auction = new Auction(
            id, sellerId, sellerName, effectiveItemStack, 0,
            buyNowPrice, creationTimestamp, expirationTimestamp
        );
        auction.setCurrentBid(currentBid);
        auction.setHighestBidderId(highestBidderId);
        auction.setHighestBidderName(highestBidderName);
        auction.setStatus(status);
        auction.setBidHistory(bidHistory);
        return auction;
    }

    public Auction loadAuction(UUID auctionId) {
        if (auctionId == null) return null;
        String sql = "SELECT * FROM auctions WHERE id = ?;";
        Auction auction = null;
        Connection conn = null;
        try {
            conn = getConnection(); // Get the shared connection
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, auctionId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        auction = mapResultSetToAuction(rs);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading auction ID: " + auctionId + " from the database.", e);
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Deserialization error loading auction ID: " + auctionId, e);
        }
        // Do NOT close conn here
        return auction;
    }

    public List<Auction> loadActiveAuctions() {
        List<Auction> activeAuctionsList = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?;";
        Connection conn = null;
        try {
            conn = getConnection(); // Get the shared connection
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, AuctionStatus.ACTIVE.name());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        try {
                            Auction auction = mapResultSetToAuction(rs);
                            if (auction != null) {
                                activeAuctionsList.add(auction);
                            }
                        } catch (IOException | ClassNotFoundException e) {
                            plugin.getLogger().log(Level.SEVERE, "Deserialization error loading an active auction (ID: " + (rs.getString("id") != null ? rs.getString("id") : "UNKNOWN") + "). Skipping this auction.", e);
                        } catch (IllegalArgumentException e) {
                             plugin.getLogger().log(Level.SEVERE, "Invalid data (e.g. status) for auction ID: " + (rs.getString("id") != null ? rs.getString("id") : "UNKNOWN") + ". Skipping this auction.", e);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading active auctions from the database.", e);
        }
        // Do NOT close conn here
        plugin.getLogger().info("Loaded " + activeAuctionsList.size() + " active auctions from the database.");
        return activeAuctionsList;
    }

    public Auction loadAuctionFuzzy(String idStr) {
        if (idStr == null || idStr.isEmpty()) return null;
        Auction auction = null;

        try {
            UUID fullUuid = UUID.fromString(idStr);
            return loadAuction(fullUuid);
        } catch (IllegalArgumentException e) {
            // Not a full UUID
        }

        String sql = "SELECT * FROM auctions WHERE id LIKE ? LIMIT 1;";
        Connection conn = null;
        try {
            conn = getConnection(); // Get the shared connection
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, idStr + "%");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        auction = mapResultSetToAuction(rs);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading auction by fuzzy ID prefix: " + idStr, e);
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Deserialization error loading auction by fuzzy ID prefix: " + idStr, e);
        }
        // Do NOT close conn here
        return auction;
    }
}
