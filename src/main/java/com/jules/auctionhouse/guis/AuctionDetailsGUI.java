package com.jules.auctionhouse.guis;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.managers.EconomyManager;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AuctionDetailsGUI implements org.bukkit.inventory.InventoryHolder {

    private final Inventory inventory;
    private final Auction auction;

    public AuctionDetailsGUI(Auction auction) {
        this.auction = auction;
        this.inventory = Bukkit.createInventory(this, 27, "Detalles de Subasta");
        initializeItems();
    }

    private void initializeItems() {
        // Fila 1: Item
        inventory.setItem(4, auction.getItem());

        // Fila 2: Información
        inventory.setItem(9, createSellerSkull(auction.getSellerUuid()));
        inventory.setItem(13, createClock());
        inventory.setItem(17, createCurrentWinnerSkull(auction.getCurrentWinner()));

        // Fila 3: Acciones
        inventory.setItem(20, createBidButton());
        if (auction.getBuyNowPrice() > 0) {
            inventory.setItem(24, createBuyNowButton());
        }
        inventory.setItem(26, createGuiItem(Material.ARROW, ChatColor.RED + "Volver a Subastas"));
    }

    private ItemStack createSellerSkull(UUID sellerUuid) {
        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerUuid);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(seller);
        meta.setDisplayName(ChatColor.GOLD + "Vendedor: " + seller.getName());
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack createCurrentWinnerSkull(UUID winnerUuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (winnerUuid != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerUuid);
            meta.setOwningPlayer(winner);
            meta.setDisplayName(ChatColor.GOLD + "Mejor Postor: " + winner.getName());
        } else {
            meta.setDisplayName(ChatColor.GOLD + "Mejor Postor: " + ChatColor.GRAY + "Nadie");
        }
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack createClock() {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Tiempo Restante");
        long millis = auction.getEndTime() - System.currentTimeMillis();
        meta.setLore(List.of(ChatColor.WHITE + formatTime(millis)));
        clock.setItemMeta(meta);
        return clock;
    }

    private ItemStack createBidButton() {
        EconomyManager econ = AuctionHouse.getInstance().getEconomyManager();
        double minIncrement = auction.getCurrentPrice() * 0.05;
        double nextBid = auction.getCurrentPrice() + minIncrement;

        ItemStack bidButton = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = bidButton.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Pujar (Incremento Mínimo)");
        meta.setLore(List.of(ChatColor.WHITE + "Puja por: " + ChatColor.GOLD + econ.format(nextBid)));
        bidButton.setItemMeta(meta);
        return bidButton;
    }

    private ItemStack createBuyNowButton() {
        EconomyManager econ = AuctionHouse.getInstance().getEconomyManager();
        ItemStack buyNowButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = buyNowButton.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Comprar Ya");
        meta.setLore(List.of(ChatColor.WHITE + "Compra inmediata por: " + ChatColor.GOLD + econ.format(auction.getBuyNowPrice())));
        buyNowButton.setItemMeta(meta);
        return buyNowButton;
    }

    private String formatTime(long millis) {
        if (millis < 0) return "Expirado";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
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

    public Auction getAuction() {
        return this.auction;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}