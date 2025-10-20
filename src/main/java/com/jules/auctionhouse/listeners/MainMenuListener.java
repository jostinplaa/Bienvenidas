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
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                player.sendMessage(com.jules.auctionhouse.AuctionHouse.getInstance().getConfigManager().getPrefix() + " §cDebes tener un item en la mano para subastarlo.");
                player.closeInventory();
                return;
            }

            com.jules.auctionhouse.AuctionHouse.getInstance().getAuctionManager().startCreationWizard(player, itemInHand);
            new com.jules.auctionhouse.guis.AuctionCreationGUI(com.jules.auctionhouse.AuctionHouse.getInstance().getAuctionManager().getCreationWizard(player)).open(player);
        }

        if (clickedItem.getType() == Material.CHEST && clickedItem.getItemMeta().getDisplayName().contains("Subastas Activas")) {
            new com.jules.auctionhouse.guis.ActiveAuctionsGUI(player, 0).open(player);
            return;
        }

        if (clickedItem.getType() == Material.GOLD_INGOT && clickedItem.getItemMeta().getDisplayName().contains("Mis Subastas")) {
            new com.jules.auctionhouse.guis.MyAuctionsGUI(player, 0).open(player);
            return;
        }

        if (clickedItem.getType() == Material.BOOK && clickedItem.getItemMeta().getDisplayName().contains("Historial")) {
            new com.jules.auctionhouse.guis.HistoryGUI(player, 0).open(player);
            return;
        }

        if (clickedItem.getType() == Material.COMPARATOR || clickedItem.getType() == Material.PAPER) {
            player.sendMessage(com.jules.auctionhouse.AuctionHouse.getInstance().getConfigManager().getPrefix() + " §eEsta función estará disponible próximamente.");
            player.closeInventory();
        }
    }
}