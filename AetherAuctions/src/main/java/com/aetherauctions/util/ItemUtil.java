package com.aetherauctions.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ItemUtil {

    /**
     * Crea un ItemStack con nombre y lore.
     *
     * @param material El material del ítem.
     * @param displayName El nombre para mostrar del ítem (se traducirán códigos de color).
     * @param loreLines Las líneas de lore para el ítem (se traducirán códigos de color).
     * @return El ItemStack creado.
     */
    public static ItemStack createItemStack(Material material, String displayName, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (displayName != null && !displayName.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }
            if (loreLines != null && !loreLines.isEmpty()) {
                List<String> coloredLore = loreLines.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Crea un ItemStack solo con nombre (sin lore).
     *
     * @param material El material del ítem.
     * @param displayName El nombre para mostrar del ítem.
     * @return El ItemStack creado.
     */
    public static ItemStack createItemStack(Material material, String displayName) {
        return createItemStack(material, displayName, new ArrayList<>());
    }
}
