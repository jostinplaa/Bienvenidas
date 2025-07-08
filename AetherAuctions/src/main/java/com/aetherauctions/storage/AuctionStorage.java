package com.aetherauctions.storage;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.model.Bid;
import com.aetherauctions.model.PendingReward; // Import PendingReward
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
                                      + "itemstack_data TEXT," // Permitir NULL para subastas misteriosas
                                      + "current_bid REAL NOT NULL,"
                                      + "highest_bidder_id TEXT,"
                                      + "highest_bidder_name TEXT,"
                                      + "buy_now_price REAL NOT NULL,"
                                      + "creation_timestamp INTEGER NOT NULL,"
                                      + "expiration_timestamp INTEGER NOT NULL,"
                                      + "status TEXT NOT NULL,"
                                      + "bid_history_json TEXT,"
                                      + "is_mystery INTEGER DEFAULT 0,"      // Nueva columna
                                      + "mystery_description TEXT"         // Nueva columna
                                      + ");";

        String sqlCreateAuctionMysteryContentsTable = "CREATE TABLE IF NOT EXISTS auction_mystery_contents ("
                                                 + "content_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                                 + "auction_id TEXT NOT NULL,"
                                                 + "item_data TEXT NOT NULL,"
                                                 + "FOREIGN KEY(auction_id) REFERENCES auctions(id) ON DELETE CASCADE"
                                                 + ");";

        String sqlCreatePendingRewardsTable = "CREATE TABLE IF NOT EXISTS pending_rewards ("
                                           + "reward_id TEXT PRIMARY KEY NOT NULL,"
                                           + "owner_id TEXT NOT NULL,"
                                           + "reward_type TEXT NOT NULL,"
                                           + "item_data TEXT,"
                                           + "money_amount REAL DEFAULT 0,"
                                           + "reason_message_key TEXT NOT NULL,"
                                           + "reason_placeholders_json TEXT,"
                                           + "creation_timestamp INTEGER NOT NULL,"
                                           + "delivered INTEGER NOT NULL DEFAULT 0"
                                           + ");";

        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreateAuctionsTable);
            stmt.execute(sqlCreatePendingRewardsTable);
            stmt.execute(sqlCreateAuctionMysteryContentsTable); // Crear la nueva tabla

            String sqlCreateAuctionHistoryTable = "CREATE TABLE IF NOT EXISTS auction_history ("
                + "history_id INTEGER PRIMARY KEY AUTOINCREMENT," // Usar INTEGER para autoincremento en SQLite
                + "player_uuid TEXT NOT NULL,"
                + "auction_id TEXT," // Puede ser el UUID de la subasta original
                + "item_name TEXT,"
                + "item_material TEXT,"
                + "item_snapshot TEXT," // Serialized ItemStack o descripción detallada
                + "event_type TEXT NOT NULL," // e.g., SOLD, BOUGHT, EXPIRED, CANCELLED, BID_PLACED, BID_OUTBID
                + "price REAL,"
                + "counterparty_name TEXT,"
                + "counterparty_uuid TEXT,"
                + "timestamp INTEGER NOT NULL"
                + ");";
            stmt.execute(sqlCreateAuctionHistoryTable);
            plugin.getLogger().info("Database tables verified/created (including auction_history).");
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
                   + "creation_timestamp, expiration_timestamp, status, bid_history_json, "
                   + "is_mystery, mystery_description) " // Añadir nuevas columnas
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"; // Añadir placeholders
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auction.getId().toString());
            pstmt.setString(2, auction.getSellerUUID().toString());
            pstmt.setString(3, auction.getSellerName());

            // Para subastas misteriosas, el itemstack_data principal puede ser null o un placeholder
            // Los ítems reales están en auction_mystery_contents
            if (auction.isMystery() && (auction.getItemStack() == null || auction.getItemStack().getType() == Material.AIR) ) {
                 pstmt.setNull(4, java.sql.Types.VARCHAR); // O guardar un placeholder serializado si se prefiere
            } else {
                pstmt.setString(4, SerializationUtil.itemStackToBase64(auction.getItemStack()));
            }

            pstmt.setDouble(5, auction.getCurrentBid());
            pstmt.setString(6, auction.getHighestBidderUUID() != null ? auction.getHighestBidderUUID().toString() : null);
            pstmt.setString(7, auction.getHighestBidderName());
            pstmt.setDouble(8, auction.getBuyNowPrice());
            pstmt.setLong(9, auction.getTimeCreated());
            pstmt.setLong(10, auction.getEndTimeMillis());
            pstmt.setString(11, auction.getStatus().name());
            pstmt.setString(12, SerializationUtil.bidListToJson(auction.getBidHistory()));
            pstmt.setInt(13, auction.isMystery() ? 1 : 0); // Guardar estado de misterio
            pstmt.setString(14, auction.getMysteryDescription()); // Guardar descripción de misterio

            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving auction ID: " + auction.getId() + " to the database.", e);
            throw e;
        } catch (IllegalStateException e) { // Catching potential SerializationUtil errors
            plugin.getLogger().log(Level.SEVERE, "Serialization error while saving auction ID: " + auction.getId(), e);
            throw new SQLException("Serialization error while saving auction.", e);
        }
    }

    private Auction mapResultSetToAuction(ResultSet rs) throws SQLException, IOException, ClassNotFoundException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID sellerId = UUID.fromString(rs.getString("seller_id"));
        String sellerName = rs.getString("seller_name");

        boolean isMystery = rs.getInt("is_mystery") == 1;
        String mysteryDescription = rs.getString("mystery_description");
        ItemStack itemStack = null;
        if (!isMystery || rs.getString("itemstack_data") != null) { // Solo deserializar si no es misterio o si hay un item principal para el misterio
             itemStack = SerializationUtil.itemStackFromBase64(rs.getString("itemstack_data"));
        }

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
            plugin.getLogger().severe("Invalid status in database for auction ID: " + id + ". Status: " + rs.getString("status") + ". Defaulting to CANCELLED.");
            status = AuctionStatus.CANCELLED; // Default a un estado seguro
        }
        List<Bid> bidHistory = SerializationUtil.bidListFromJson(rs.getString("bid_history_json"));

        ItemStack effectiveItemStack = itemStack; // itemStack ya maneja el caso de ser null para misteriosas
        if (!isMystery && itemStack == null) { // Solo es un problema si NO es misteriosa y el item es null
             plugin.getLogger().warning("ItemStack deserialized to null for NON-MYSTERY auction ID: " + id + ". Using a placeholder BARRIER item.");
             effectiveItemStack = new ItemStack(Material.BARRIER, 1);
        }

        // Usar el constructor que incluye isMystery y mysteryDescription
        Auction auction = new Auction(
            id, sellerId, sellerName, effectiveItemStack, 0, // startPrice no se guarda/carga directamente, currentBid inicial es startPrice
            buyNowPrice, creationTimestamp, expirationTimestamp,
            isMystery, mysteryDescription
        );
        auction.setCurrentBid(currentBid); // currentBid se setea después, ya que puede cambiar.
        auction.setHighestBidderUUID(highestBidderId);
        auction.setHighestBidderName(highestBidderName);
        auction.setStatus(status);
        auction.setBidHistory(bidHistory);
        // auction.setMystery(isMystery); // Ya se hace en el constructor
        // auction.setMysteryDescription(mysteryDescription); // Ya se hace en el constructor
        return auction;
    }

    public Auction loadAuction(UUID auctionId) {
        if (auctionId == null) return null;
        String sql = "SELECT * FROM auctions WHERE id = ?;";
        Auction auction = null;
        Connection conn;
        try {
            conn = getConnection();
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
        return auction;
    }

    public List<Auction> loadActiveAuctions() {
        List<Auction> activeAuctionsList = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?;";
        Connection conn;
        try {
            conn = getConnection();
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
        Connection conn;
        try {
            conn = getConnection();
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
        return auction;
    }

    // --- PendingReward Methods ---

    public void saveReward(PendingReward reward) throws SQLException {
        if (reward == null) {
            plugin.getLogger().warning("Se intentó guardar una recompensa pendiente null.");
            return;
        }
        String sql = "INSERT OR REPLACE INTO pending_rewards (reward_id, owner_id, reward_type, item_data, "
                   + "money_amount, reason_message_key, reason_placeholders_json, creation_timestamp, delivered) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, reward.getRewardId().toString());
            pstmt.setString(2, reward.getOwnerId().toString());
            pstmt.setString(3, reward.getType().name());
            if (reward.getItemToClaim() != null) {
                pstmt.setString(4, SerializationUtil.itemStackToBase64(reward.getItemToClaim()));
            } else {
                pstmt.setNull(4, java.sql.Types.VARCHAR);
            }
            pstmt.setDouble(5, reward.getMoneyToClaim());
            pstmt.setString(6, reward.getReasonMessageKey());
            pstmt.setString(7, SerializationUtil.stringListToJson(reward.getReasonPlaceholders())); // Changed to use stringListToJson
            pstmt.setLong(8, reward.getCreationTimestamp());
            pstmt.setInt(9, reward.isDelivered() ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar PendingReward ID: " + reward.getRewardId(), e);
            throw e;
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de serialización de ItemStack al guardar PendingReward ID: " + reward.getRewardId(), e);
            throw new SQLException("Error de serialización de ItemStack para PendingReward.", e);
        }
    }

    private PendingReward mapResultSetToPendingReward(ResultSet rs) throws SQLException, IOException, ClassNotFoundException {
        UUID rewardId = UUID.fromString(rs.getString("reward_id"));
        UUID ownerId = UUID.fromString(rs.getString("owner_id"));
        PendingReward.RewardType type = PendingReward.RewardType.valueOf(rs.getString("reward_type"));
        ItemStack itemToClaim = null;
        String itemData = rs.getString("item_data");
        if (itemData != null) {
            itemToClaim = SerializationUtil.itemStackFromBase64(itemData);
            if (itemToClaim == null) {
                 plugin.getLogger().warning("ItemStack deserializado para PendingReward ID " + rewardId + " es null. Puede haber datos corruptos.");
            }
        }
        double moneyToClaim = rs.getDouble("money_amount");
        String reasonMessageKey = rs.getString("reason_message_key");
        List<String> reasonPlaceholders = SerializationUtil.stringListFromJson(rs.getString("reason_placeholders_json")); // Changed to use stringListFromJson
        if (reasonPlaceholders == null) reasonPlaceholders = new ArrayList<>();
        long creationTimestamp = rs.getLong("creation_timestamp");
        boolean delivered = rs.getInt("delivered") == 1;
        return new PendingReward(rewardId, ownerId, type, itemToClaim, moneyToClaim, reasonMessageKey, reasonPlaceholders, creationTimestamp, delivered);
    }

    public List<PendingReward> getPendingRewardsByOwner(UUID ownerId) {
        List<PendingReward> rewards = new ArrayList<>();
        String sql = "SELECT * FROM pending_rewards WHERE owner_id = ? AND delivered = 0 ORDER BY creation_timestamp ASC;";
        Connection conn;
        try {
            conn = getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ownerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        try {
                            rewards.add(mapResultSetToPendingReward(rs));
                        } catch (IOException | ClassNotFoundException e) {
                            plugin.getLogger().log(Level.SEVERE, "Error de deserialización al cargar PendingReward ID: " + (rs.getString("reward_id") != null ? rs.getString("reward_id") : "UNKNOWN") + ". Saltando.", e);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().log(Level.SEVERE, "Error de datos (ej. Enum RewardType inválido) al cargar PendingReward ID: " + (rs.getString("reward_id") != null ? rs.getString("reward_id") : "UNKNOWN") + ". Saltando.", e);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al cargar PendingRewards para owner ID: " + ownerId, e);
        }
        return rewards;
    }

    public PendingReward getPendingReward(UUID rewardId) {
        if (rewardId == null) return null;
        String sql = "SELECT * FROM pending_rewards WHERE reward_id = ?;";
        PendingReward reward = null;
        Connection conn;
        try {
            conn = getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, rewardId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        reward = mapResultSetToPendingReward(rs);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading PendingReward ID: " + rewardId + " from the database.", e);
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Deserialization error loading PendingReward ID: " + rewardId, e);
        }
        return reward;
    }

    public void markRewardDelivered(UUID rewardId) throws SQLException {
        String sql = "UPDATE pending_rewards SET delivered = 1 WHERE reward_id = ?;";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rewardId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al marcar PendingReward ID: " + rewardId + " como entregada.", e);
            throw e;
        }
    }

    public int deleteOldDeliveredRewards(long olderThanTimestamp) throws SQLException {
        String sql = "DELETE FROM pending_rewards WHERE delivered = 1 AND creation_timestamp < ?;";
        Connection conn = getConnection();
        int rowsAffected = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, olderThanTimestamp);
            rowsAffected = pstmt.executeUpdate();
            // Logging of actual deletion count moved to RewardManager after this method returns.
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al eliminar recompensas entregadas antiguas.", e);
            throw e;
        }
        return rowsAffected;
    }

    // --- Mystery Auction Contents Methods ---
    public void saveMysteryAuctionContents(UUID auctionId, List<ItemStack> items) throws SQLException {
        if (items == null || items.isEmpty()) return;
        String sql = "INSERT INTO auction_mystery_contents (auction_id, item_data) VALUES (?, ?);";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    pstmt.setString(1, auctionId.toString());
                    pstmt.setString(2, SerializationUtil.itemStackToBase64(item));
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving mystery auction contents for auction ID: " + auctionId, e);
            throw e;
        }
    }

    public List<ItemStack> getMysteryAuctionContents(UUID auctionId) throws SQLException {
        List<ItemStack> items = new ArrayList<>();
        String sql = "SELECT item_data FROM auction_mystery_contents WHERE auction_id = ?;";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        ItemStack item = SerializationUtil.itemStackFromBase64(rs.getString("item_data"));
                        if (item != null) {
                            items.add(item);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Error deserializing item for mystery auction ID: " + auctionId, e);
                    }
                }
            }
        }
        return items;
    }
     public int getMysteryAuctionContentsCount(UUID auctionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM auction_mystery_contents WHERE auction_id = ?;";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }


    // --- Auction History Methods ---

    public void saveHistoryEvent(com.aetherauctions.model.AuctionHistoryEvent event) throws SQLException {
        if (event == null) {
            plugin.getLogger().warning("Attempted to save a null history event.");
            return;
        }
        String sql = "INSERT INTO auction_history (player_uuid, auction_id, item_name, item_material, item_snapshot, "
                   + "event_type, price, counterparty_name, counterparty_uuid, timestamp) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, event.getPlayerUuid().toString());
            pstmt.setString(2, event.getAuctionId() != null ? event.getAuctionId().toString() : null);
            pstmt.setString(3, event.getItemName());
            pstmt.setString(4, event.getItemMaterial());
            pstmt.setString(5, event.getItemSnapshot());
            pstmt.setString(6, event.getEventType().name());
            pstmt.setDouble(7, event.getPrice());
            pstmt.setString(8, event.getCounterpartyName());
            pstmt.setString(9, event.getCounterpartyUuid() != null ? event.getCounterpartyUuid().toString() : null);
            pstmt.setLong(10, event.getTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving auction history event for player: " + event.getPlayerUuid(), e);
            throw e;
        }
    }

    private com.aetherauctions.model.AuctionHistoryEvent mapResultSetToHistoryEvent(ResultSet rs) throws SQLException {
        int historyId = rs.getInt("history_id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String auctionIdStr = rs.getString("auction_id");
        UUID auctionId = auctionIdStr != null ? UUID.fromString(auctionIdStr) : null;
        String itemName = rs.getString("item_name");
        String itemMaterial = rs.getString("item_material");
        String itemSnapshot = rs.getString("item_snapshot");
        com.aetherauctions.model.AuctionHistoryEvent.HistoryEventType eventType;
        try {
            eventType = com.aetherauctions.model.AuctionHistoryEvent.HistoryEventType.valueOf(rs.getString("event_type"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid HistoryEventType in database: " + rs.getString("event_type") + " for history_id: " + historyId);
            eventType = null; // Or a default/unknown type
        }
        double price = rs.getDouble("price");
        String counterpartyName = rs.getString("counterparty_name");
        String counterpartyUuidStr = rs.getString("counterparty_uuid");
        UUID counterpartyUuid = counterpartyUuidStr != null ? UUID.fromString(counterpartyUuidStr) : null;
        long timestamp = rs.getLong("timestamp");

        return new com.aetherauctions.model.AuctionHistoryEvent(historyId, playerUuid, auctionId, itemName, itemMaterial, itemSnapshot, eventType, price, counterpartyName, counterpartyUuid, timestamp);
    }

    public List<com.aetherauctions.model.AuctionHistoryEvent> getAuctionHistory(UUID playerUuid, int page, int itemsPerPage) throws SQLException {
        List<com.aetherauctions.model.AuctionHistoryEvent> historyEvents = new ArrayList<>();
        String sql = "SELECT * FROM auction_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?;";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, itemsPerPage);
            pstmt.setInt(3, page * itemsPerPage);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    com.aetherauctions.model.AuctionHistoryEvent event = mapResultSetToHistoryEvent(rs);
                    if (event.getEventType() != null) { // Only add if event type was valid
                        historyEvents.add(event);
                    }
                }
            }
        }
        return historyEvents;
    }

    public int getHistoryCount(UUID playerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM auction_history WHERE player_uuid = ?;";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<com.aetherauctions.model.AuctionHistoryEvent> getAuctionHistoryForAdmin(UUID playerUuid, long startDate, long endDate, int page, int itemsPerPage) throws SQLException {
        List<com.aetherauctions.model.AuctionHistoryEvent> historyEvents = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM auction_history WHERE player_uuid = ? ");
        if (startDate > 0) sqlBuilder.append("AND timestamp >= ? ");
        if (endDate > 0) sqlBuilder.append("AND timestamp <= ? ");
        sqlBuilder.append("ORDER BY timestamp DESC LIMIT ? OFFSET ?;");

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, playerUuid.toString());
            if (startDate > 0) pstmt.setLong(paramIndex++, startDate);
            if (endDate > 0) pstmt.setLong(paramIndex++, endDate);
            pstmt.setInt(paramIndex++, itemsPerPage);
            pstmt.setInt(paramIndex, page * itemsPerPage);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                     com.aetherauctions.model.AuctionHistoryEvent event = mapResultSetToHistoryEvent(rs);
                    if (event.getEventType() != null) {
                        historyEvents.add(event);
                    }
                }
            }
        }
        return historyEvents;
    }

    public int getHistoryCountForAdmin(UUID playerUuid, long startDate, long endDate) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder("SELECT COUNT(*) FROM auction_history WHERE player_uuid = ? ");
        if (startDate > 0) sqlBuilder.append("AND timestamp >= ? ");
        if (endDate > 0) sqlBuilder.append("AND timestamp <= ? ");

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, playerUuid.toString());
            if (startDate > 0) pstmt.setLong(paramIndex++, startDate);
            if (endDate > 0) pstmt.setLong(paramIndex, endDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public void purgeOldPlayerHistory(UUID playerUuid, int recordsToKeep) throws SQLException {
        // This is a bit complex with SQLite as it doesn't directly support "LIMIT" in a subquery for DELETE like MySQL.
        // A common workaround is to find the timestamp of the Nth record and delete older ones.
        String findNthTimestampSQL = "SELECT timestamp FROM auction_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 1 OFFSET ?;";
        long thresholdTimestamp = -1;

        Connection conn = getConnection();
        try (PreparedStatement pstmtFind = conn.prepareStatement(findNthTimestampSQL)) {
            pstmtFind.setString(1, playerUuid.toString());
            // Offset is N-1. If recordsToKeep is 50, we want the 50th record, so offset is 49.
            // If there are fewer than `recordsToKeep` records, this query will return no rows.
            pstmtFind.setInt(2, Math.max(0, recordsToKeep -1));
            ResultSet rs = pstmtFind.executeQuery();
            if (rs.next()) {
                thresholdTimestamp = rs.getLong("timestamp");
            } else {
                // Fewer than `recordsToKeep` records exist, so nothing to purge.
                return;
            }
        }

        if (thresholdTimestamp > 0) {
            String deleteSql = "DELETE FROM auction_history WHERE player_uuid = ? AND timestamp < ?;";
            try (PreparedStatement pstmtDelete = conn.prepareStatement(deleteSql)) {
                pstmtDelete.setString(1, playerUuid.toString());
                pstmtDelete.setLong(2, thresholdTimestamp);
                int deletedRows = pstmtDelete.executeUpdate();
                if (deletedRows > 0) {
                    plugin.getLogger().info("Purged " + deletedRows + " old history entries for player " + playerUuid.toString());
                }
            }
        }
    }
}
