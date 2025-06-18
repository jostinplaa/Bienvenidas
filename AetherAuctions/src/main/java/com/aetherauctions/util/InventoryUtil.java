package com.aetherauctions.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InventoryUtil {

    /**
     * Creates a GUI item with a name and lore.
     *
     * @param material The material of the item.
     * @param name     The name of the item.
     * @param loreLines The lines of lore for the item.
     * @return The created ItemStack.
     */
    public static ItemStack createGuiItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines != null && loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a GUI item with a name, lore, and optional glow.
     *
     * @param material The material of the item.
     * @param name     The name of the item.
     * @param addGlow  True to add a glow effect, false otherwise.
     * @param loreLines The lines of lore for the item.
     * @return The created ItemStack.
     */
    public static ItemStack createGuiItem(Material material, String name, boolean addGlow, String... loreLines) {
        ItemStack item = createGuiItem(material, name, loreLines);
        if (addGlow) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true); // Dummy enchant for glow
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Creates a GUI item with a name and a list of lore lines.
     *
     * @param material The material of the item.
     * @param name     The name of the item.
     * @param lore     The list of lore lines.
     * @return The created ItemStack.
     */
    public static ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Formats time in milliseconds to a human-readable string like "1d 2h 30m", "15m 10s", "Finalizando".
     * @param millis Time in milliseconds.
     * @return Formatted string.
     */
    public static String formatTime(long millis) {
        if (millis < 0) return "Error";
        if (millis < 1000 * 60) { // Less than 1 minute
             long seconds = millis / 1000;
             return seconds <= 15 ? "Finalizando" : seconds + "s";
        }

        long days = millis / (24 * 60 * 60 * 1000);
        millis %= (24 * 60 * 60 * 1000);
        long hours = millis / (60 * 60 * 1000);
        millis %= (60 * 60 * 1000);
        long minutes = millis / (60 * 1000);
        millis %= (60 * 1000);
        long seconds = millis / 1000;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 && days == 0) sb.append(minutes).append("m "); // Only show minutes if no days
        if (seconds > 0 && days == 0 && hours == 0) sb.append(seconds).append("s"); // Only show seconds if no days/hours

        String result = sb.toString().trim();
        return result.isEmpty() ? "Finalizando" : result;
    }
}
