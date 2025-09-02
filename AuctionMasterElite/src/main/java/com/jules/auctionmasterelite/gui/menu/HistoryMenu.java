package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.gui.GUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HistoryMenu extends GUI {

    private final int page;

    public HistoryMenu(AuctionMasterElite plugin, Player player, int page) {
        super(plugin, 54, "§8Historial (Página " + (page + 1) + ")");
        this.page = page;
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        List<Auction> history = plugin.getDatabaseManager().getDataSource().loadPlayerHistory(player.getUniqueId());

        int maxItemsPerPage = 45;
        int startIndex = page * maxItemsPerPage;

        for (int i = 0; i < maxItemsPerPage; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex >= history.size()) {
                break;
            }
            Auction auction = history.get(auctionIndex);
            inventory.setItem(i, createHistoryItem(auction, player.getUniqueId()));
        }

        // Pagination controls
        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, "§aPágina Anterior", "§7Ir a la página " + page));
        }
        if (history.size() > maxItemsPerPage * (page + 1)) {
            inventory.setItem(53, createGuiItem(Material.ARROW, "§aPágina Siguiente", "§7Ir a la página " + (page + 2)));
        }
    }

    private ItemStack createHistoryItem(Auction auction, UUID viewerId) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§8§m--------------------");
            if (auction.getSellerId().equals(viewerId)) {
                lore.add("§dVendiste este objeto.");
            } else if (auction.getTopBidderId() != null && auction.getTopBidderId().equals(viewerId)) {
                lore.add("§aGanaste esta subasta.");
            }
            lore.add("§7Vendedor: §e" + auction.getSellerName());
            lore.add("§7Puja Ganadora: §6" + String.format("%.2f", auction.getCurrentBid()));
            lore.add("§7Ganador: §b" + (auction.getTopBidderName() != null ? auction.getTopBidderName() : "Nadie"));
            lore.add("§8§m--------------------");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ARROW) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getDisplayName().contains("Siguiente")) {
                player.openInventory(new HistoryMenu(plugin, player, page + 1).getInventory());
            } else if (meta != null && meta.getDisplayName().contains("Anterior")) {
                player.openInventory(new HistoryMenu(plugin, player, page - 1).getInventory());
            }
        }
    }
}
