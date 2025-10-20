package com.jules.auctionhouse.guis;

import com.jules.auctionhouse.models.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class AuctionCreationGUI {

    private final Inventory inventory;

    public AuctionCreationGUI(Auction auction) {
        this.inventory = Bukkit.createInventory(null, 27, "Crear Subasta");
        initializeItems(auction);
    }

    private void initializeItems(Auction auction) {
        // Item Preview
        inventory.setItem(4, auction.getItem());

        // Config Buttons
        inventory.setItem(11, createGuiItem(Material.GOLD_INGOT, "§ePrecio Inicial", "§fActual: §a" + auction.getCurrentPrice()));
        inventory.setItem(13, createGuiItem(Material.CLOCK, "§eDuración", "§fActual: §a" + formatDuration(auction.getEndTime() - System.currentTimeMillis())));
        inventory.setItem(15, createGuiItem(Material.EMERALD, "§eComprar Ya", "§fActual: §a" + (auction.getBuyNowPrice() > 0 ? auction.getBuyNowPrice() : "Desactivado")));

        // Action Buttons
        inventory.setItem(21, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "§a§lConfirmar"));
        inventory.setItem(23, createGuiItem(Material.RED_STAINED_GLASS_PANE, "§c§lCancelar"));
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}