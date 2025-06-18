package com.example.aetherauctions.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.stream.Collectors;

public class GuiUtils {

    /**
     * Creates an ItemStack for GUI display.
     *
     * @param material The material of the item.
     * @param name     The display name of the item.
     * @param lore     The lore lines for the item.
     * @return The created ItemStack.
     */
    public static ItemStack createDisplayItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a player head ItemStack for GUI display.
     *
     * @param ownerName   The name of the player whose head to display.
     * @param displayName The display name for the item.
     * @param lore        The lore lines for the item.
     * @return The created player head ItemStack.
     */
    @SuppressWarnings("deprecation") // For setOwner
    public static ItemStack createPlayerHead(String ownerName, String displayName, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwner(ownerName); // Deprecated, but replacement requires player to be online or UUID
            meta.setDisplayName(displayName);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
