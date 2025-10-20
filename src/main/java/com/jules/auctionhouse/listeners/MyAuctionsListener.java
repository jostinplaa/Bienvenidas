package com.jules.auctionhouse.listeners;

import com.jules.auctionhouse.guis.MainMenu;
import com.jules.auctionhouse.guis.MyAuctionsGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class MyAuctionsListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith("Mis Subastas")) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1, title.length() - 1)) - 1;

        if (clickedItem.getType() == Material.BARRIER) {
            new MainMenu().open(player);
            return;
        }

        if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("Anterior")) {
            if (currentPage > 0) {
                new MyAuctionsGUI(player, currentPage - 1).open(player);
            }
            return;
        }

        if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("Siguiente")) {
            new MyAuctionsGUI(player, currentPage + 1).open(player);
        }
    }
}