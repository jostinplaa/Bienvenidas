package com.aetherauctions.database;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DatabaseManager {

    private Connection connection;
    private final AetherAuctions plugin;
    private final String dbName = "aetherauctions.db";

    public DatabaseManager(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, dbName);
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("Conectado a la base de datos SQLite.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo conectar a la base de datos SQLite.", e);
            throw e;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "No se encontró el driver de SQLite. Asegúrate de que la librería está incluida.", e);
            // En un entorno real, esto podría requerir descargar la dependencia.
            // Por ahora, asumimos que está disponible o se manejará externamente.
             throw new SQLException("SQLite JDBC driver not found.", e);
        }
    }

    public synchronized void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Desconectado de la base de datos SQLite.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al desconectar la base de datos SQLite.", e);
        }
    }

    public synchronized void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS auctions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "seller_uuid TEXT NOT NULL, " +
                "seller_name TEXT NOT NULL, " +
                "item_serialized TEXT NOT NULL, " +
                "start_time BIGINT NOT NULL, " +
                "duration BIGINT NOT NULL, " +
                "start_price REAL NOT NULL, " +
                "buy_now_price REAL, " +
                "current_bid REAL, " +
                "highest_bidder_uuid TEXT, " +
                "highest_bidder_name TEXT, " +
                "status TEXT NOT NULL" +
                ");";
        String sqlClaimableItems = "CREATE TABLE IF NOT EXISTS claimable_items (" +
                                   "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                   "player_uuid TEXT NOT NULL, " +
                                   "item_serialized TEXT NOT NULL, " +
                                   "reason_message TEXT" +
                                   ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            plugin.getLogger().info("Tabla 'auctions' creada o ya existente.");
            stmt.execute(sqlClaimableItems);
            plugin.getLogger().info("Tabla 'claimable_items' creada o ya existente.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo crear una o más tablas.", e);
            throw e;
        }
    }

    /**
     * Saves an auction item. If the item's ID is 0, it's an INSERT, otherwise an UPDATE.
     * @param auction The AuctionItem to save.
     * @return The ID of the saved auction, or -1 on failure.
     * @throws SQLException if a database access error occurs.
     */
    public synchronized int saveAuction(AuctionItem auction) throws SQLException {
        String sql;
        boolean isNewItem = auction.getId() == 0;

        if (isNewItem) {
            sql = "INSERT INTO auctions(seller_uuid, seller_name, item_serialized, start_time, duration, " +
                  "start_price, buy_now_price, current_bid, highest_bidder_uuid, highest_bidder_name, status) " +
                  "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        } else {
            sql = "UPDATE auctions SET seller_uuid = ?, seller_name = ?, item_serialized = ?, start_time = ?, " +
                  "duration = ?, start_price = ?, buy_now_price = ?, current_bid = ?, highest_bidder_uuid = ?, " +
                  "highest_bidder_name = ?, status = ? WHERE id = ?";
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, auction.getSellerUUID());
            pstmt.setString(2, auction.getSellerName());
            pstmt.setString(3, ItemSerializer.itemStackToBase64(auction.getItemStack()));
            pstmt.setLong(4, auction.getStartTime());
            pstmt.setLong(5, auction.getDuration());
            pstmt.setDouble(6, auction.getStartPrice());
            pstmt.setDouble(7, auction.getBuyNowPrice()); // Handles -1 correctly for DB
            pstmt.setDouble(8, auction.getCurrentBid());
            pstmt.setString(9, auction.getHighestBidderUUID());
            pstmt.setString(10, auction.getHighestBidderName());
            pstmt.setString(11, auction.getStatus().name());

            if (!isNewItem) {
                pstmt.setInt(12, auction.getId());
            }

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                if (isNewItem) {
                    try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            auction.setId(generatedKeys.getInt(1)); // Set the generated ID back to the object
                            return auction.getId();
                        } else {
                             plugin.getLogger().warning("No se pudo obtener el ID generado para la nueva subasta.");
                            return -1;
                        }
                    }
                } else {
                    return auction.getId(); // Return existing ID on update
                }
            }
            return -1;
        } catch (IllegalStateException | SQLException e) { // IllegalStateException from ItemSerializer
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la subasta ID: " + auction.getId() + (isNewItem ? " (nueva)" : ""), e);
            throw e;
        }
    }

    public synchronized void deleteAuction(int auctionId) throws SQLException {
        String sql = "DELETE FROM auctions WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al eliminar la subasta: " + auctionId, e);
            throw e;
        }
    }

    private AuctionItem mapResultSetToAuctionItem(ResultSet rs) throws SQLException, IOException {
        ItemStack item = null;
        try {
            item = ItemSerializer.itemStackFromBase64(rs.getString("item_serialized"));
        } catch (IOException | IllegalStateException e) { // Catch IllegalStateException from serializer too
            plugin.getLogger().log(Level.SEVERE, "Error deserializando ItemStack para la subasta ID: " + rs.getInt("id"), e);
            // Podrías decidir devolver null o lanzar la excepción dependiendo de cómo quieras manejar este error.
            // Por ahora, permitimos que la excepción se propague para ser manejada por el llamador.
            throw e;
        }

        return new AuctionItem(
                rs.getInt("id"),
                rs.getString("seller_uuid"),
                rs.getString("seller_name"),
                item,
                rs.getLong("start_time"),
                rs.getLong("duration"),
                rs.getDouble("start_price"),
                rs.getDouble("buy_now_price"),
                rs.getDouble("current_bid"),
                rs.getString("highest_bidder_uuid"),
                rs.getString("highest_bidder_name"),
                AuctionStatus.valueOf(rs.getString("status"))
        );
    }

    public synchronized AuctionItem getAuction(int auctionId) throws SQLException, IOException {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToAuctionItem(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al obtener la subasta: " + auctionId, e);
            throw e;
        } catch (IOException e) { // Atrapa la IOException de mapResultSetToAuctionItem
            plugin.getLogger().log(Level.SEVERE, "Error de deserialización al obtener la subasta: " + auctionId, e);
            throw e;
        }
        return null;
    }

    public synchronized List<AuctionItem> getActiveAuctions() throws SQLException, IOException {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, AuctionStatus.ACTIVE.name());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                 try {
                    auctions.add(mapResultSetToAuctionItem(rs));
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Error deserializando item para subasta activa ID: " + rs.getInt("id") + ". Omitiendo.", e);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al obtener subastas activas.", e);
            throw e;
        }
        return auctions;
    }

    public synchronized List<AuctionItem> getAuctionsByPlayer(String playerUUID) throws SQLException, IOException {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE seller_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    auctions.add(mapResultSetToAuctionItem(rs));
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Error deserializando item para subasta del jugador " + playerUUID + ", ID: " + rs.getInt("id") + ". Omitiendo.", e);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al obtener subastas para el jugador: " + playerUUID, e);
            throw e;
        }
        return auctions;
    }

    public synchronized List<AuctionItem> getExpiredAuctions() throws SQLException, IOException {
        List<AuctionItem> auctions = new ArrayList<>();
        // Considera 'ACTIVE' y que startTime + duration < currentTimeMillis
        long currentTime = System.currentTimeMillis();
        String sql = "SELECT * FROM auctions WHERE status = ? AND (start_time + duration) < ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, AuctionStatus.ACTIVE.name());
            pstmt.setLong(2, currentTime);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                 try {
                    auctions.add(mapResultSetToAuctionItem(rs));
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Error deserializando item para subasta expirada ID: " + rs.getInt("id") + ". Omitiendo.", e);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al obtener subastas expiradas.", e);
            throw e;
        }
        return auctions;
    }

    public synchronized void updateAuctionStatus(int auctionId, AuctionStatus status) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setInt(2, auctionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al actualizar estado de la subasta: " + auctionId, e);
            throw e;
        }
    }

    public synchronized void updateBid(int auctionId, String bidderUUID, String bidderName, double newBid) throws SQLException {
        String sql = "UPDATE auctions SET current_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, newBid);
            pstmt.setString(2, bidderUUID);
            pstmt.setString(3, bidderName);
            pstmt.setInt(4, auctionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al actualizar la puja para la subasta: " + auctionId, e);
            throw e;
        }
    }

    public synchronized void addClaimableItem(String playerUUID, ItemStack item, String reason) throws SQLException {
        String sql = "INSERT INTO claimable_items(player_uuid, item_serialized, reason_message) VALUES(?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            pstmt.setString(2, ItemSerializer.itemStackToBase64(item));
            pstmt.setString(3, reason);
            pstmt.executeUpdate();
        } catch (IllegalStateException | SQLException e) { // IllegalStateException from ItemSerializer
            plugin.getLogger().log(Level.SEVERE, "Error añadiendo ítem reclamable para el jugador: " + playerUUID, e);
            throw e;
        }
    }
}
