package com.jules.auctionhouse.db;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private Connection connection;
    private final AuctionHouse plugin;

    public DatabaseManager(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            File dataFolder = new File(plugin.getDataFolder(), "auctions.db");
            if (!dataFolder.exists()) {
                dataFolder.getParentFile().mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            plugin.getLogger().info("Conexión con SQLite establecida.");

            initializeDatabase();

        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo conectar a la base de datos SQLite.", e);
        }
    }

    private synchronized void initializeDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS auctions (" +
                         "auction_id TEXT PRIMARY KEY," +
                         "seller_uuid TEXT NOT NULL," +
                         "item_base64 TEXT NOT NULL," +
                         "end_time INTEGER NOT NULL," +
                         "start_price REAL NOT NULL," +
                         "current_price REAL NOT NULL," +
                         "buy_now_price REAL NOT NULL," +
                         "current_winner_uuid TEXT," +
                         "status TEXT NOT NULL," +
                         "type TEXT NOT NULL" +
                         ");";
            statement.execute(sql);

            String historySql = "CREATE TABLE IF NOT EXISTS auction_history (" +
                                "auction_id TEXT PRIMARY KEY," +
                                "seller_uuid TEXT NOT NULL," +
                                "buyer_uuid TEXT," +
                                "item_base64 TEXT NOT NULL," +
                                "end_time INTEGER NOT NULL," +
                                "start_price REAL NOT NULL," +
                                "final_price REAL NOT NULL," +
                                "buy_now_price REAL NOT NULL," +
                                "status TEXT NOT NULL," +
                                "type TEXT NOT NULL" +
                                ");";
            statement.execute(historySql);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo cerrar la conexión con la base de datos.", e);
        }
    }

    public synchronized void saveAuction(Auction auction) {
        String sql = "INSERT INTO auctions(auction_id, seller_uuid, item_base64, end_time, start_price, current_price, buy_now_price, current_winner_uuid, status, type) " +
                     "VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auction.getAuctionId().toString());
            pstmt.setString(2, auction.getSellerUuid().toString());
            pstmt.setString(3, itemStackToBase64(auction.getItem()));
            pstmt.setLong(4, auction.getEndTime());
            pstmt.setDouble(5, auction.getStartingPrice());
            pstmt.setDouble(6, auction.getCurrentPrice());
            pstmt.setDouble(7, auction.getBuyNowPrice());
            pstmt.setString(8, auction.getCurrentWinner() != null ? auction.getCurrentWinner().toString() : null);
            pstmt.setString(9, auction.getStatus().name());
            pstmt.setString(10, auction.getType().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar la subasta " + auction.getAuctionId(), e);
        }
    }

    public synchronized void updateAuction(Auction auction) {
        String sql = "UPDATE auctions SET current_price = ?, current_winner_uuid = ?, status = ? WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, auction.getCurrentPrice());
            pstmt.setString(2, auction.getCurrentWinner() != null ? auction.getCurrentWinner().toString() : null);
            pstmt.setString(3, auction.getStatus().name());
            pstmt.setString(4, auction.getAuctionId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo actualizar la subasta " + auction.getAuctionId(), e);
        }
    }

    public synchronized void deleteAuction(UUID auctionId) {
        String sql = "DELETE FROM auctions WHERE auction_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auctionId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo eliminar la subasta " + auctionId, e);
        }
    }

    public synchronized void archiveAuction(Auction auction) {
        deleteAuction(auction.getAuctionId());

        String sql = "INSERT INTO auction_history(auction_id, seller_uuid, buyer_uuid, item_base64, end_time, start_price, final_price, buy_now_price, status, type) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, auction.getAuctionId().toString());
            pstmt.setString(2, auction.getSellerUuid().toString());
            pstmt.setString(3, auction.getCurrentWinner() != null ? auction.getCurrentWinner().toString() : null);
            pstmt.setString(4, itemStackToBase64(auction.getItem()));
            pstmt.setLong(5, auction.getEndTime());
            pstmt.setDouble(6, auction.getStartingPrice());
            pstmt.setDouble(7, auction.getCurrentPrice());
            pstmt.setDouble(8, auction.getBuyNowPrice());
            pstmt.setString(9, auction.getStatus().name());
            pstmt.setString(10, auction.getType().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo archivar la subasta " + auction.getAuctionId(), e);
        }
    }

    public synchronized List<Auction> loadAllActiveAuctions() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                // La reconstrucción del objeto Auction desde el ResultSet es compleja y la haré en un método separado
                auctions.add(auctionFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudieron cargar las subastas activas.", e);
        }
        return auctions;
    }

    public synchronized List<Auction> getPlayerHistory(UUID playerUuid, int limit, int offset) {
        List<Auction> history = new ArrayList<>();
        String sql = "SELECT * FROM auction_history WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY end_time DESC LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, playerUuid.toString());
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(auctionFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo cargar el historial del jugador " + playerUuid, e);
        }
        return history;
    }

    public synchronized List<Auction> getPlayerAuctions(UUID playerUuid, int limit, int offset) {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM ( " +
                     "SELECT * FROM auctions WHERE seller_uuid = ? " +
                     "UNION ALL " +
                     "SELECT * FROM auction_history WHERE seller_uuid = ? " +
                     ") ORDER BY end_time DESC LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, playerUuid.toString());
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    auctions.add(auctionFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudieron cargar las subastas del jugador " + playerUuid, e);
        }
        return auctions;
    }

    private Auction auctionFromResultSet(ResultSet rs) throws SQLException {
        UUID auctionId = UUID.fromString(rs.getString("auction_id"));
        UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
        ItemStack item = itemStackFromBase64(rs.getString("item_base64"));
        long endTime = rs.getLong("end_time");
        double startPrice = rs.getDouble("start_price");
        double currentPrice = rs.getDouble("current_price");
        double buyNowPrice = rs.getDouble("buy_now_price");
        String winnerUuidStr = rs.getString("current_winner_uuid");
        UUID winnerUuid = winnerUuidStr != null ? UUID.fromString(winnerUuidStr) : null;
        Auction.AuctionStatus status = Auction.AuctionStatus.valueOf(rs.getString("status"));
        Auction.AuctionType type = Auction.AuctionType.valueOf(rs.getString("type"));

        return new Auction(auctionId, sellerUuid, item, endTime, startPrice, currentPrice, buyNowPrice, winnerUuid, status, type);
    }

    // --- Utilidades de Serialización ---
    public static String itemStackToBase64(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el item a Base64.", e);
        }
    }

    public static ItemStack itemStackFromBase64(String base64) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo deserializar el item desde Base64.", e);
        }
    }
}