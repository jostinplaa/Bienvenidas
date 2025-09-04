package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.util.LoreUtil;
import com.jules.auctionmasterelite.gui.menu.MainMenu;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HistoryMenu extends GUI {

    private final int page;
    private final Player viewer;
    private final OfflinePlayer target;

    // Constructor for players viewing their own history
    public HistoryMenu(AuctionMasterElite plugin, Player player) {
        this(plugin, player, player, 0);
    }

    // Constructor for admins viewing another player's history
    public HistoryMenu(AuctionMasterElite plugin, Player viewer, OfflinePlayer target) {
        this(plugin, viewer, target, 0);
    }

    // Main constructor
    public HistoryMenu(AuctionMasterElite plugin, Player viewer, OfflinePlayer target, int page) {
        super(plugin, 54, "§8History: " + target.getName() + " (Page " + (page + 1) + ")");
        this.page = page;
        this.viewer = viewer;
        this.target = target;
        initializeItems();
    }

    private void initializeItems() {
        List<Auction> history = plugin.getDatabaseManager().loadPlayerHistory(target.getUniqueId());

        int maxItemsPerPage = 45;
        int startIndex = page * maxItemsPerPage;

        for (int i = 0; i < maxItemsPerPage; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex >= history.size()) {
                break;
            }
            Auction auction = history.get(auctionIndex);
            inventory.setItem(i, createHistoryItem(auction, target.getUniqueId()));
        }

        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, "§aPágina Anterior", LoreUtil.getLore("active-auctions-menu.previous-page", "page", String.valueOf(page))));
        }
        if (history.size() > maxItemsPerPage * (page + 1)) {
            inventory.setItem(53, createGuiItem(Material.ARROW, "§aPágina Siguiente", LoreUtil.getLore("active-auctions-menu.next-page", "page", String.valueOf(page + 2))));
        }
        inventory.setItem(49, createGuiItem(Material.RED_WOOL, "§cVolver al Menú", LoreUtil.getLore("active-auctions-menu.back-button")));
    }

    private ItemStack createHistoryItem(Auction auction, UUID targetId) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (auction.getSellerId().equals(targetId)) {
                lore.addAll(LoreUtil.getLore("history-menu.sold-item"));
            } else if (auction.getTopBidderId() != null && auction.getTopBidderId().equals(targetId)) {
                lore.addAll(LoreUtil.getLore("history-menu.won-item"));
            }
            lore.addAll(LoreUtil.getLore("history-menu.item-details",
                    "seller", auction.getSellerName(),
                    "price", String.format("%.2f", auction.getCurrentBid()),
                    "winner", auction.getTopBidderName() != null ? auction.getTopBidderName() : "Nadie"
            ));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked(); // The viewer is always the one clicking
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ARROW) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getDisplayName().contains("Siguiente")) {
                new HistoryMenu(plugin, viewer, target, page + 1).open(viewer);
            } else if (meta != null && meta.getDisplayName().contains("Anterior")) {
                new HistoryMenu(plugin, viewer, target, page - 1).open(viewer);
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            new MainMenu(plugin).open(viewer);
        }
    }
}
