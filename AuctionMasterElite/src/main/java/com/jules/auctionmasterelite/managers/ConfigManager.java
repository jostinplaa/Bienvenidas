package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigManager {

    private final AuctionMasterElite plugin;
    private FileConfiguration config;
    private File configFile;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        saveDefaultMessages();
    }

    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            config.setDefaults(defaultConfig);
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    public void reloadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defaultMessagesStream = plugin.getResource("messages.yml");
        if (defaultMessagesStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultMessagesStream));
            messages.setDefaults(defaultMessages);
        }
    }

    public FileConfiguration getMessages() {
        if (messages == null) {
            reloadMessages();
        }
        return messages;
    }

    public void saveDefaultMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }
}
