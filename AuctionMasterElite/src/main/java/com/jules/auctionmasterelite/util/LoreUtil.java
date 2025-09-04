package com.jules.auctionmasterelite.util;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoreUtil {

    private static FileConfiguration loresConfig;

    public static void load(AuctionMasterElite plugin) {
        loresConfig = plugin.getConfigManager().getLores();
    }

    public static List<String> getLore(String path, String... replacements) {
        String loreString = loresConfig.getString(path, "&cLore not found: " + path);

        // Replace placeholders
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                loreString = loreString.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }

        // Split by newline placeholder and translate color codes
        List<String> loreLines = new ArrayList<>();
        for (String line : loreString.split("%nl%")) {
            loreLines.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        return loreLines;
    }
}
