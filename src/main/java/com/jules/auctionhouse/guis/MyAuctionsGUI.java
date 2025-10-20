package com.jules.auctionhouse.guis;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MyAuctionsGUI {

    private final Inventory inventory;

    public MyAuctionsGUI(Player player, int page) {
        this.inventory = Bukkit.createInventory(null, 54, "Mis Subastas (Pág. " + (page + 1) + ")");
        initializeItems(player, page);
    }

    private void initializeItems(Player player, int page) {
        List<Auction> auctions = AuctionHouse.getInstance().getDatabaseManager().getPlayerAuctions(player.getUniqueId(), 45, page * 45);

        // Paginación y volver
        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, "§ePágina Anterior"));
        }
        inventory.setItem(49, createGuiItem(Material.BARRIER, "§cVolver"));
        if (auctions.size() == 45) {
            inventory.setItem(53, createGuiItem(Material.ARROW, "§ePágina Siguiente"));
        }

        // Contenido
        for (int i = 0; i < auctions.size(); i++) {
            Auction auction = auctions.get(i);
            inventory.setItem(i, createAuctionItem(auction));
        }
    }

    private ItemStack createAuctionItem(Auction auction) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() != null ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
            lore.add(" ");
            switch(auction.getStatus()) {
                case ACTIVE:
                    meta.setDisplayName("§e(Activa) §f" + item.getType());
                    lore.add("§fPrecio actual: §a" + auction.getCurrentPrice());
                    lore.add("§fTiempo restante: §c" + formatDuration(auction.getEndTime() - System.currentTimeMillis()));
                    break;
                case SOLD:
                    meta.setDisplayName("§a(Vendida) §f" + item.getType());
                    lore.add("§fPrecio final: §a" + auction.getCurrentPrice());
                    lore.add("§fComprador: §e" + Bukkit.getOfflinePlayer(auction.getCurrentWinner()).getName());
                    break;
                case EXPIRED:
                     meta.setDisplayName("§c(Expirada) §f" + item.getType());
                     lore.add("§fNo se vendió.");
                    break;
                case CANCELLED:
                     meta.setDisplayName("§7(Cancelada) §f" + item.getType());
                     break;
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
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