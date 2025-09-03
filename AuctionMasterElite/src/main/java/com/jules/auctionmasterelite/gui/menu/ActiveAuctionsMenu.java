package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.data.AuctionType;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.gui.menu.util.SortMode;
import com.jules.auctionmasterelite.util.LoreUtil;
import com.jules.auctionmasterelite.util.TimeUtil;
import com.jules.auctionmasterelite.gui.menu.MainMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ActiveAuctionsMenu extends GUI {

    private final int page;
    private final SortMode sortMode;
    private final Map<Integer, UUID> slotToAuctionId = new HashMap<>();

    public ActiveAuctionsMenu(AuctionMasterElite plugin, Player player, int page, SortMode sortMode) {
        super(plugin, 54, "§8Subastas Activas (Página " + (page + 1) + ")");
        this.page = page;
        this.sortMode = sortMode;
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        List<Auction> auctions = plugin.getAuctionManager().getActiveAuctions().values().stream()
                .filter(auction -> {
                    if (auction.getType() == AuctionType.PRIVATE) {
                        return auction.getInvitedPlayers().contains(player.getUniqueId()) || auction.getSellerId().equals(player.getUniqueId());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        auctions.sort((a1, a2) -> {
            switch (sortMode) {
                case ENDING_SOONEST:
                    return Long.compare(a1.getEndTime(), a2.getEndTime());
                case NEWEST_LISTED:
                    return Long.compare(a2.getStartTime(), a1.getStartTime());
                case PRICE_ASCENDING:
                    return Double.compare(a1.getCurrentBid(), a2.getCurrentBid());
                case PRICE_DESCENDING:
                    return Double.compare(a2.getCurrentBid(), a1.getCurrentBid());
                default:
                    return 0;
            }
        });

        int maxItemsPerPage = 45;
        int startIndex = page * maxItemsPerPage;

        for (int i = 0; i < maxItemsPerPage; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex >= auctions.size()) {
                break;
            }
            Auction auction = auctions.get(auctionIndex);
            inventory.setItem(i, createAuctionItem(auction));
            slotToAuctionId.put(i, auction.getAuctionId());
        }

        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, "§aPágina Anterior", LoreUtil.getLore("active-auctions-menu.previous-page", "page", String.valueOf(page))));
        }
        if (auctions.size() > maxItemsPerPage * (page + 1)) {
            inventory.setItem(53, createGuiItem(Material.ARROW, "§aPágina Siguiente", LoreUtil.getLore("active-auctions-menu.next-page", "page", String.valueOf(page + 2))));
        }
        inventory.setItem(49, createGuiItem(Material.CLOCK, "§bOrdenar Por", LoreUtil.getLore("active-auctions-menu.sort-button", "sort_mode", sortMode.getDisplayName())));
        inventory.setItem(48, createGuiItem(Material.RED_WOOL, "§cVolver al Menú", LoreUtil.getLore("active-auctions-menu.back-button")));
    }

    private ItemStack createAuctionItem(Auction auction) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> baseLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            List<String> auctionLore = LoreUtil.getLore("active-auctions-menu.auction-item",
                    "seller", auction.getSellerName(),
                    "price", String.format("%.2f", auction.getCurrentBid()),
                    "time", TimeUtil.formatDuration(auction.getEndTime() - System.currentTimeMillis())
            );
            baseLore.addAll(auctionLore);
            meta.setLore(baseLore);
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
                player.openInventory(new ActiveAuctionsMenu(plugin, player, page + 1, sortMode).getInventory());
            } else if (meta != null && meta.getDisplayName().contains("Anterior")) {
                player.openInventory(new ActiveAuctionsMenu(plugin, player, page - 1, sortMode).getInventory());
            }
        } else if (clickedItem.getType() == Material.CLOCK) {
            SortMode nextSortMode = sortMode.next();
            player.openInventory(new ActiveAuctionsMenu(plugin, player, 0, nextSortMode).getInventory());
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            player.openInventory(new MainMenu(plugin).getInventory());
        } else {
            UUID auctionId = slotToAuctionId.get(event.getSlot());
            if (auctionId != null) {
                player.closeInventory();
                player.sendMessage("§ePor favor, introduce el monto de tu puja en el chat.");
                plugin.getPlayerInputManager().requestInput(player.getUniqueId(), (bidAmount) -> {
                    try {
                        double amount = Double.parseDouble(bidAmount);
                        plugin.getAuctionManager().placeBid(player, auctionId, amount);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cEntrada inválida. Por favor, introduce un número válido.");
                    }
                });
            }
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
