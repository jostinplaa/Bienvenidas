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
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ActiveAuctionsGUI {

    private final Inventory inventory;
    private final int page;

    public ActiveAuctionsGUI(Player player, int page) {
        this.page = page;
        this.inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Subastas Activas (Pág. " + (page + 1) + ")");
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        List<Auction> auctions = new ArrayList<>(AuctionHouse.getInstance().getAuctionManager().getActiveAuctions());
        int totalPages = (int) Math.ceil((double) auctions.size() / 36);

        // Header
        inventory.setItem(0, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Página Anterior"));
        inventory.setItem(8, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Página Siguiente"));

        // Footer
        inventory.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Volver"));

        // Contenido
        int startIndex = page * 36;
        for (int i = 0; i < 36; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex >= auctions.size()) {
                break;
            }
            Auction auction = auctions.get(auctionIndex);
            inventory.setItem(i + 9, createAuctionItem(auction));
        }
    }

    private ItemStack createAuctionItem(Auction auction) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Guardar el UUID de la subasta en el item
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(AuctionHouse.getInstance(), "auction-id"), PersistentDataType.STRING, auction.getAuctionId().toString());

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add(ChatColor.GRAY + "--------------------");

            OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
            lore.add(ChatColor.WHITE + "Vendedor: " + ChatColor.YELLOW + seller.getName());
            lore.add(ChatColor.WHITE + "Precio actual: " + ChatColor.GREEN + "$" + String.format("%,.2f", auction.getCurrentPrice()));

            if (auction.getCurrentWinner() != null) {
                OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getCurrentWinner());
                lore.add(ChatColor.WHITE + "Mejor postor: " + ChatColor.AQUA + winner.getName());
            } else {
                lore.add(ChatColor.WHITE + "Mejor postor: " + ChatColor.GRAY + "Sin pujas");
            }

            lore.add(ChatColor.WHITE + "Tiempo restante: " + ChatColor.RED + formatTime(auction.getEndTime() - System.currentTimeMillis()));
            lore.add(ChatColor.GRAY + "--------------------");
            lore.add(ChatColor.GOLD + "Click para ver detalles");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatTime(long millis) {
        if (millis < 0) return "Expirado";
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
        sb.append(seconds).append("s");

        return sb.toString().trim();
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