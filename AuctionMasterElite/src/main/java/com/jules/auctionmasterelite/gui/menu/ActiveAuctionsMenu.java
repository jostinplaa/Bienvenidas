package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.util.TimeUtil;
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

public class ActiveAuctionsMenu extends GUI {

    private final int page;
    private final Map<Integer, UUID> slotToAuctionId = new HashMap<>();

    public ActiveAuctionsMenu(AuctionMasterElite plugin, Player player, int page) {
        super(plugin, 54, "§8Subastas Activas (Página " + (page + 1) + ")");
        this.page = page;
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        Map<java.util.UUID, Auction> auctionMap = plugin.getAuctionManager().getActiveAuctions();
        List<Auction> auctions = new ArrayList<>(auctionMap.values());

        // 45 is the max items per page (54 slots - 9 for controls)
        int maxItemsPerPage = 45;
        int startIndex = page * maxItemsPerPage;

        for (int i = 0; i < maxItemsPerPage; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex >= auctions.size()) {
                break; // No more auctions to display
            }
            Auction auction = auctions.get(auctionIndex);
            inventory.setItem(i, createAuctionItem(auction));
            slotToAuctionId.put(i, auction.getAuctionId());
        }

        // Pagination controls
        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, "§aPágina Anterior", "§7Ir a la página " + page));
        }
        if (auctions.size() > maxItemsPerPage * (page + 1)) {
            inventory.setItem(53, createGuiItem(Material.ARROW, "§aPágina Siguiente", "§7Ir a la página " + (page + 2)));
        }
    }

    private ItemStack createAuctionItem(Auction auction) {
        ItemStack item = auction.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§8§m--------------------");
            lore.add("§7Vendedor: §e" + auction.getSellerName());
            lore.add("§7Precio Actual: §6" + String.format("%.2f", auction.getCurrentBid()));
            lore.add("§7Finaliza en: §c" + TimeUtil.formatDuration(auction.getEndTime() - System.currentTimeMillis()));
            lore.add("§8§m--------------------");
            lore.add("§a¡Haz clic para pujar!");
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
                player.openInventory(new ActiveAuctionsMenu(plugin, player, page + 1).getInventory());
            } else if (meta != null && meta.getDisplayName().contains("Anterior")) {
                player.openInventory(new ActiveAuctionsMenu(plugin, player, page - 1).getInventory());
            }
        } else {
            // It's an auction item
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
}
