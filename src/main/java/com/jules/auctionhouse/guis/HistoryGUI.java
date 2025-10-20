package com.jules.auctionhouse.guis;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class HistoryGUI {

    private final Inventory inventory;

    public HistoryGUI(Player player, int page) {
        this.inventory = Bukkit.createInventory(null, 54, "Historial (Pág. " + (page + 1) + ")");
        initializeItems(player, page);
    }

    private void initializeItems(Player player, int page) {
        List<Auction> history = AuctionHouse.getInstance().getDatabaseManager().getPlayerHistory(player.getUniqueId(), 45, page * 45);

        // Paginación y volver
        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Página Anterior"));
        }
        inventory.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Volver"));
        if (history.size() == 45) { // Asume que puede haber más páginas
            inventory.setItem(53, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Página Siguiente"));
        }

        // Contenido
        for (int i = 0; i < history.size(); i++) {
            Auction auction = history.get(i);
            inventory.setItem(i, createHistoryItem(auction, player.getUniqueId()));
        }
    }

    private ItemStack createHistoryItem(Auction auction, UUID viewerUuid) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            boolean wasSeller = auction.getSellerUuid().equals(viewerUuid);

            if (auction.getStatus() == Auction.AuctionStatus.SOLD) {
                if (wasSeller) {
                    meta.setDisplayName(ChatColor.GREEN + "Vendido: " + item.getType());
                    lore.add(ChatColor.WHITE + "Vendido a: " + ChatColor.YELLOW + Bukkit.getOfflinePlayer(auction.getCurrentWinner()).getName());
                    lore.add(ChatColor.WHITE + "Precio: " + ChatColor.GOLD + auction.getCurrentPrice());
                } else {
                    meta.setDisplayName(ChatColor.GREEN + "Comprado: " + item.getType());
                    lore.add(ChatColor.WHITE + "Comprado a: " + ChatColor.YELLOW + Bukkit.getOfflinePlayer(auction.getSellerUuid()).getName());
                    lore.add(ChatColor.WHITE + "Precio: " + ChatColor.GOLD + auction.getCurrentPrice());
                }
            } else if (auction.getStatus() == Auction.AuctionStatus.EXPIRED) {
                meta.setDisplayName(ChatColor.RED + "Expirado: " + item.getType());
            } else { // CANCELLED
                 meta.setDisplayName(ChatColor.GRAY + "Cancelado: " + item.getType());
            }

            lore.add(ChatColor.GRAY + "Fecha: " + sdf.format(new Date(auction.getEndTime())));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}