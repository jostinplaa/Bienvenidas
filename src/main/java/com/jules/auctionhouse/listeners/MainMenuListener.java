package com.jules.auctionhouse.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class MainMenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(com.jules.auctionhouse.AuctionHouse.getInstance().getConfigManager().getPrefix())) {
            return;
        }

        event.setCancelled(true); // Prevenir que el jugador tome los items

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.EMERALD && clickedItem.getItemMeta().getDisplayName().contains("Crear Subasta")) {
            player.closeInventory();
            player.sendMessage("§eUsa /auction create <precio> [duracion] para crear una subasta.");
            return;
        }

        if (clickedItem.getType() == Material.CHEST && clickedItem.getItemMeta().getDisplayName().contains("Subastas Activas")) {
            new com.jules.auctionhouse.guis.ActiveAuctionsGUI(player, 0).open(player);
            return;
        }

        if (clickedItem.getType() == Material.BOOK && clickedItem.getItemMeta().getDisplayName().contains("Historial")) {
            new com.jules.auctionhouse.guis.HistoryGUI(player, 0).open(player);
        }
    }
}