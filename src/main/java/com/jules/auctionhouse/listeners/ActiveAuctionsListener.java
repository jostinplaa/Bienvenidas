package com.jules.auctionhouse.listeners;

import com.jules.auctionhouse.guis.ActiveAuctionsGUI;
import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.guis.AuctionDetailsGUI;
import com.jules.auctionhouse.guis.MainMenu;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class ActiveAuctionsListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.DARK_AQUA + "Subastas Activas")) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1, title.length() - 1)) - 1;

        // Botón de Volver
        if (clickedItem.getType() == Material.BARRIER) {
            new MainMenu().open(player);
            return;
        }

        // Botón de Página Anterior
        if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("Anterior")) {
            if (currentPage > 0) {
                new ActiveAuctionsGUI(player, currentPage - 1).open(player);
            }
            return;
        }

        // Botón de Página Siguiente
        if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("Siguiente")) {
            new ActiveAuctionsGUI(player, currentPage + 1).open(player);
        }

        // Clic en un item de subasta
        if (event.getSlot() >= 9 && event.getSlot() <= 44 && clickedItem.hasItemMeta()) {
            String auctionIdStr = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(AuctionHouse.getInstance(), "auction-id"), PersistentDataType.STRING);
            if (auctionIdStr != null) {
                UUID auctionId = UUID.fromString(auctionIdStr);
                Auction auction = AuctionHouse.getInstance().getAuctionManager().getAuction(auctionId);
                if (auction != null) {
                    new AuctionDetailsGUI(auction).open(player);
                }
            }
        }
    }
}