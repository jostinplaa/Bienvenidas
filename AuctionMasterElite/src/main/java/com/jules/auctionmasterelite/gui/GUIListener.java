package com.jules.auctionmasterelite.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        InventoryHolder holder = event.getInventory().getHolder();

        // Check if the inventory is one of our GUIs
        if (holder instanceof GUI) {
            GUI gui = (GUI) holder;

            // Let the specific GUI handle the click
            gui.onClick(event);
        }
    }
}
