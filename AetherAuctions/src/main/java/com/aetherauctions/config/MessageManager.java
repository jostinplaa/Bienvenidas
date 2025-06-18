package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MessageManager {

    private final AetherAuctions plugin;
    private FileConfiguration messagesConfig;
    private String prefix; // Store the prefix separately for convenience

    public MessageManager(AetherAuctions plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false); // Save default messages.yml from JAR
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Attempt to load default messages from JAR if the file is somehow empty or new keys are added
        try (InputStream defaultConfigStream = plugin.getResource("messages.yml")) {
            if (defaultConfigStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
                messagesConfig.setDefaults(defaultConfig);
                messagesConfig.options().copyDefaults(true); // Copy defaults for any missing keys
                 // plugin.saveResource("messages.yml", true); // Could overwrite user changes if used carelessly
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load default messages from JAR", e);
        }

        // Load prefix from messages.yml, fallback to config.yml's prefix or a hardcoded one
        this.prefix = messagesConfig.getString("prefix", plugin.getConfigManager().getPluginPrefix());
        if (this.prefix == null || this.prefix.isEmpty()){
            this.prefix = "&e&lAetherAuctions &8» "; // Ultimate fallback
        }
        this.prefix = ChatColor.translateAlternateColorCodes('&', this.prefix); // Translate prefix once

        plugin.getLogger().info("Mensajes cargados.");
    }

    public void reloadMessages() {
        loadMessages();
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key in messages.yml: " + key);
            return ChatColor.RED + "Error: Mensaje no encontrado (" + key + ")";
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), String.valueOf(entry.getValue())); // Ensure value is string
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Convenience method for simple placeholder pairs
    public String getMessage(String key, String... placeholderPairs) {
        if (placeholderPairs.length % 2 != 0) {
            plugin.getLogger().warning("Invalid placeholder pairs for message key: " + key + ". Must be even.");
            return getMessage(key, (Map<String,String>)null); // Call with null map
        }
        Map<String, String> placeholders = new HashMap<>();
        for (int i = 0; i < placeholderPairs.length; i += 2) {
            placeholders.put(placeholderPairs[i], placeholderPairs[i + 1]);
        }
        return getMessage(key, placeholders);
    }


    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getMessage(key, placeholders);
        // Check if the message *already* contains a prefix similar to the global one, or if it's a "no prefix" message
        // This is a simple check; more sophisticated checks might be needed if message formats vary greatly.
        String rawMessageForKey = messagesConfig.getString(key, "");
        boolean messageHasOwnPrefix = ChatColor.stripColor(rawMessageForKey).trim().startsWith(ChatColor.stripColor(this.prefix).trim());

        if (!messageHasOwnPrefix && !key.startsWith("bare.") && !key.endsWith("_bare")) { // Convention for no-prefix messages
            sender.sendMessage(this.prefix + message);
        } else {
            sender.sendMessage(message);
        }
    }

    // Convenience method for simple placeholder pairs
    public void sendMessage(CommandSender sender, String key, String... placeholderPairs) {
         if (placeholderPairs.length % 2 != 0) {
            plugin.getLogger().warning("Invalid placeholder pairs for sending message key: " + key + ". Must be even.");
            sendMessage(sender, key, (Map<String,String>)null);
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        for (int i = 0; i < placeholderPairs.length; i += 2) {
            placeholders.put(placeholderPairs[i], placeholderPairs[i + 1]);
        }
        sendMessage(sender, key, placeholders);
    }

    public String getRaw(String key) {
        return messagesConfig.getString(key, ChatColor.RED + "Missing: " + key);
    }

    public String getPrefixedRaw(String key) {
        return this.prefix + ChatColor.translateAlternateColorCodes('&', getRaw(key));
    }

    public String getPrefix() {
        return prefix;
    }
}
