package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.sql.DataSource;
import com.jules.auctionmasterelite.data.sql.SQLiteDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {

    private final AuctionMasterElite plugin;
    private DataSource dataSource;

    public DatabaseManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
        initializeDataSource();
    }

    private void initializeDataSource() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String dbType = config.getString("database.type", "sqlite").toLowerCase();

        plugin.getLogger().info("Using " + dbType.toUpperCase() + " for data storage.");

        if (dbType.equals("mysql")) {
            this.dataSource = new com.jules.auctionmasterelite.data.sql.MySQLDataSource(plugin);
        } else {
            this.dataSource = new SQLiteDataSource(plugin);
        }
    }

    public void connect() throws SQLException {
        dataSource.connect();
        // Initialize tables after connecting
        dataSource.initializeDatabase();
    }

    public void disconnect() {
        if (dataSource != null) {
            dataSource.disconnect();
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
