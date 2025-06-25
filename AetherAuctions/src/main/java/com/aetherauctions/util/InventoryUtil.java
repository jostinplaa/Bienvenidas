package com.aetherauctions.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;
import java.util.List; // Required for Arrays.asList
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.WordUtils; // For formatting material names

public class InventoryUtil {

    public static ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0 && (lore.length > 1 || lore[0] != null)) { // Ensure lore is not empty or just a null string
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // Overload for List<String> lore
    public static ItemStack createGuiItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }


    public static String formatTime(long millis) {
        if (millis < 0) return "Error"; // Or a translatable key
        if (millis == 0) return "Finalizado"; // Or a translatable key, e.g., messages.getMessage("time_ended")

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) { // Show seconds if it's the only unit or if other units are zero
            sb.append(seconds).append("s");
        }

        String formatted = sb.toString().trim();
        // If all are zero (e.g. duration was < 1s), it might result in "0s" or empty.
        // Let's ensure "0s" if it's truly zero, or the formatted string.
        return formatted.isEmpty() ? "0s" : formatted;
    }

    public static String formatMaterialName(Material material) {
        if (material == null) return "Desconocido"; // Or translatable
        // Capitalizes each word and replaces underscores. e.g. DIAMOND_SWORD -> Diamond Sword
        return WordUtils.capitalizeFully(material.name().replace("_", " ")).trim();
    }
}
