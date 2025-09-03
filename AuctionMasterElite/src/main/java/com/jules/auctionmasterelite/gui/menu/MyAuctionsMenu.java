package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.util.LoreUtil;
import com.jules.auctionmasterelite.util.TimeUtil;
import com.jules.auctionmasterelite.gui.menu.MainMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MyAuctionsMenu extends GUI {

    private final int page;

    public MyAuctionsMenu(AuctionMasterElite plugin, Player player, int page) {
        super(plugin, 54, "§8Mis Subastas (Página " + (page + 1) + ")");
        this.page = page;
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        UUID playerId = player.getUniqueId();
        List<Auction> sellerAuctions = plugin.getAuctionManager().getAuctionsBySeller(playerId);
        List<Auction> bidderAuctions = plugin.getAuctionManager().getAuctionsByBidder(playerId);

        List<Auction> allMyAuctions = Stream.concat(sellerAuctions.stream(), bidderAuctions.stream())
                .distinct()
                .collect(Collectors.toList());

        int maxItemsPerPage = 45;
        int startIndex = page * maxItemsPerPage;

        for (int i = 0; i < maxItemsPerPage; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex >= allMyAuctions.size()) {
                break;
            }
            Auction auction = allMyAuctions.get(auctionIndex);
            inventory.setItem(i, createAuctionItem(auction, playerId));
        }

        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, "§aPágina Anterior", LoreUtil.getLore("active-auctions-menu.previous-page", "page", String.valueOf(page))));
        }
        if (allMyAuctions.size() > maxItemsPerPage * (page + 1)) {
            inventory.setItem(53, createGuiItem(Material.ARROW, "§aPágina Siguiente", LoreUtil.getLore("active-auctions-menu.next-page", "page", String.valueOf(page + 2))));
        }
        inventory.setItem(49, createGuiItem(Material.RED_WOOL, "§cVolver al Menú", LoreUtil.getLore("active-auctions-menu.back-button")));
    }

    private ItemStack createAuctionItem(Auction auction, UUID viewerId) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (auction.getSellerId().equals(viewerId)) {
                lore.addAll(LoreUtil.getLore("my-auctions-menu.your-auction"));
            } else if (auction.getTopBidderId() != null && auction.getTopBidderId().equals(viewerId)) {
                lore.addAll(LoreUtil.getLore("my-auctions-menu.top-bidder"));
            }
            lore.addAll(LoreUtil.getLore("active-auctions-menu.auction-item",
                    "seller", auction.getSellerName(),
                    "price", String.format("%.2f", auction.getCurrentBid()),
                    "time", TimeUtil.formatDuration(auction.getEndTime() - System.currentTimeMillis())
            ));
            meta.setLore(lore);
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
                player.openInventory(new MyAuctionsMenu(plugin, player, page + 1).getInventory());
            } else if (meta != null && meta.getDisplayName().contains("Anterior")) {
                player.openInventory(new MyAuctionsMenu(plugin, player, page - 1).getInventory());
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            player.openInventory(new MainMenu(plugin).getInventory());
        } else {
            player.sendMessage("§eAuction management coming soon!");
        }
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
}
