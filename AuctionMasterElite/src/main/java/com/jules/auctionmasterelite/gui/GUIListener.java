package com.jules.auctionmasterelite.gui;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.gui.claims.ClaimMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        // Handle ClaimMenu
        if (event.getView().getTitle().equals("Claim Your Items")) {
            AuctionMasterElite plugin = JavaPlugin.getPlugin(AuctionMasterElite.class);
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                ClaimMenu claimMenu = plugin.getClaimMenus().get(player.getUniqueId());
                if (claimMenu != null) {
                    claimMenu.handleClick(event);
                }
            }
            return; // Stop further processing
        }

        InventoryHolder holder = event.getInventory().getHolder();

        // Check if the inventory is one of our GUIs
        if (holder instanceof GUI) {
            GUI gui = (GUI) holder;

            // Let the specific GUI handle the click
            gui.onClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Handle ClaimMenu
        if (event.getView().getTitle().equals("Claim Your Items")) {
            AuctionMasterElite plugin = JavaPlugin.getPlugin(AuctionMasterElite.class);
            if (event.getPlayer() instanceof Player) {
                Player player = (Player) event.getPlayer();
                // We remove the menu instance to prevent memory leaks
                plugin.getClaimMenus().remove(player.getUniqueId());
            }
        }
    }
}
