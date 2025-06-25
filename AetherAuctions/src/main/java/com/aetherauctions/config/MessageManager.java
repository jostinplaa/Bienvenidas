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
        String rawMessage = messagesConfig.getString(key);
        String coloredMessage;

        if (rawMessage == null) {
            plugin.getLogger().warning("[MessageManager] Clave de mensaje no encontrada en messages.yml: '" + key + "'. Usando valor por defecto.");
            // Return a default error message that is visible in-game
            return ChatColor.RED + "Error: Msg key missing (" + key + ")";
        }

        coloredMessage = ChatColor.translateAlternateColorCodes('&', rawMessage);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                coloredMessage = coloredMessage.replace(entry.getKey(), String.valueOf(entry.getValue())); // Ensure value is string, and use coloredMessage
            }
        }
        return coloredMessage;
    }

    // Convenience method for simple placeholder pairs
    public String getMessage(String key, String... placeholderPairs) {
        // plugin.getLogger().info("[MessageManager] getMessage for key: '" + key + "', pairs: " + Arrays.toString(placeholderPairs)); // Logging - Commented out
        String rawMessage = messagesConfig.getString(key);

        if (rawMessage == null) {
            plugin.getLogger().warning("[MessageManager] Clave de mensaje no encontrada en messages.yml: '" + key + "'. Usando valor por defecto.");
            return ChatColor.RED + "Error: Msg key missing (" + key + ")";
        }

        if (placeholderPairs.length % 2 != 0) {
            plugin.getLogger().severe("[MessageManager] Error de placeholders para la clave '" + key + "'. Se proporcionó un número impar de argumentos para los placeholders. Placeholders: " + Arrays.toString(placeholderPairs));
            return ChatColor.translateAlternateColorCodes('&', rawMessage); // Return raw message (colored) without placeholder replacement
        }

        String message = rawMessage; // Work with the raw message for replacements
        Map<String, String> placeholdersMap = new HashMap<>();
        for (int i = 0; i < placeholderPairs.length; i += 2) {
            placeholdersMap.put(placeholderPairs[i], placeholderPairs[i + 1]);
        }

        if (!placeholdersMap.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholdersMap.entrySet()) {
                message = message.replace(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }


    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        // This method will now benefit from the improved getMessage(key, placeholdersMap)
        String messageWithAppliedPlaceholders = getMessage(key, placeholders);

        // Check if the original message (before placeholder replacement but after potential error message from getMessage)
        // is an error message from getMessage itself. If so, don't add prefix.
        if (messageWithAppliedPlaceholders.startsWith(ChatColor.RED + "Error: Msg key missing")) {
            sender.sendMessage(messageWithAppliedPlaceholders);
            return;
        }

        // Check if the message *already* contains a prefix similar to the global one, or if it's a "no prefix" message
        // This is a simple check; more sophisticated checks might be needed if message formats vary greatly.
        String rawMessageFromConfig = messagesConfig.getString(key, ""); // Get original config string for prefix check
        boolean messageHasOwnPrefix = ChatColor.stripColor(rawMessageFromConfig).trim().startsWith(ChatColor.stripColor(this.prefix).trim());

        if (!messageHasOwnPrefix && !key.startsWith("bare.") && !key.endsWith("_bare")) { // Convention for no-prefix messages
            sender.sendMessage(this.prefix + messageWithAppliedPlaceholders);
        } else {
            sender.sendMessage(messageWithAppliedPlaceholders);
        }
    }

    // Convenience method for simple placeholder pairs
    public void sendMessage(CommandSender sender, String key, String... placeholderPairs) {
        // This method will now benefit from the improved getMessage(key, placeholderPairs...)
        String messageWithAppliedPlaceholders = getMessage(key, placeholderPairs);

        // Check if the original message (before placeholder replacement but after potential error message from getMessage)
        // is an error message from getMessage itself. If so, don't add prefix.
         if (messageWithAppliedPlaceholders.startsWith(ChatColor.RED + "Error: Msg key missing")) {
            sender.sendMessage(messageWithAppliedPlaceholders);
            return;
        }

        String rawMessageFromConfig = messagesConfig.getString(key, ""); // Get original config string for prefix check
        boolean messageHasOwnPrefix = ChatColor.stripColor(rawMessageFromConfig).trim().startsWith(ChatColor.stripColor(this.prefix).trim());

        if (!messageHasOwnPrefix && !key.startsWith("bare.") && !key.endsWith("_bare")) { // Convention for no-prefix messages
            sender.sendMessage(this.prefix + messageWithAppliedPlaceholders);
        } else {
            sender.sendMessage(messageWithAppliedPlaceholders);
        }
    }

    public String getRaw(String key) {
        String message = messagesConfig.getString(key);
        if (message == null) {
            plugin.getLogger().warning("[MessageManager] Clave de mensaje (raw) no encontrada: '" + key + "'. Devolviendo la clave.");
            return key; // Return the key itself if not found, making it obvious in GUIs/code
        }
        return message;
    }

    public String getPrefixedRaw(String key) {
        return this.prefix + ChatColor.translateAlternateColorCodes('&', getRaw(key));
    }

    public String getPrefix() {
        return prefix;
    }

    public String stripColors(String input) {
        if (input == null) {
            return null;
        }
        return ChatColor.stripColor(input);
    }

    public List<String> getRawStringList(String key) {
        List<String> list = messagesConfig.getStringList(key);
        if (list == null || list.isEmpty()) {
            plugin.getLogger().warning("[MessageManager] Clave de lista de mensajes (raw) no encontrada o vacía: '" + key + "'. Devolviendo lista vacía.");
            return new ArrayList<>();
        }
        return list;
    }
}
