package com.jules.auctionhouse.listeners;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.guis.ActiveAuctionsGUI;
import com.jules.auctionhouse.guis.AuctionDetailsGUI;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class AuctionDetailsListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AuctionDetailsGUI)) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        AuctionDetailsGUI gui = (AuctionDetailsGUI) holder;
        Auction auction = gui.getAuction();

        // Botón de Volver
        if (clickedItem.getType() == Material.ARROW) {
            new ActiveAuctionsGUI(player, 0).open(player);
            return;
        }

        // Botón de Pujar
        if (clickedItem.getType() == Material.GOLD_NUGGET) {
            double minIncrement = auction.getCurrentPrice() * 0.05;
            double nextBid = auction.getCurrentPrice() + minIncrement;

            boolean success = AuctionHouse.getInstance().getAuctionManager().placeBid(player, auction.getAuctionId(), nextBid);

            if (success) {
                new AuctionDetailsGUI(auction).open(player); // Refrescar GUI
            }
            return;
        }

        // Botón de Comprar Ya
        if (clickedItem.getType() == Material.EMERALD_BLOCK) {
            boolean success = AuctionHouse.getInstance().getAuctionManager().buyNow(player, auction.getAuctionId());
            if (success) {
                player.closeInventory();
            }
        }
    }
}