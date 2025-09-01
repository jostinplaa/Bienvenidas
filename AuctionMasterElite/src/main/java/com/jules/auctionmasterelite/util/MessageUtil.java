package com.jules.auctionmasterelite.util;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageUtil {

    private static FileConfiguration messagesConfig;
    private static String prefix;

    public static void load(AuctionMasterElite plugin) {
        messagesConfig = plugin.getConfigManager().getMessages();
        prefix = ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("prefix", "&d&lAuctionMaster &8» "));
    }

    public static void sendMessage(CommandSender sender, String path, String... replacements) {
        String message = messagesConfig.getString(path, "&cMessage not found: " + path);
        message = replacePlaceholders(message, replacements);
        sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void sendRawMessage(CommandSender sender, String path, String... replacements) {
        String message = messagesConfig.getString(path, "&cMessage not found: " + path);
        message = replacePlaceholders(message, replacements);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void broadcastMessage(String path, String... replacements) {
        String message = messagesConfig.getString(path, "&cMessage not found: " + path);
        message = replacePlaceholders(message, replacements);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private static String replacePlaceholders(String message, String... replacements) {
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }
}
