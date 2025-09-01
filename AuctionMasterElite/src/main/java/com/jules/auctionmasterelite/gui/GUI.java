package com.jules.auctionmasterelite.gui;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a generic, clickable GUI.
 */
import com.jules.auctionmasterelite.AuctionMasterElite;

public abstract class GUI implements InventoryHolder {

    protected final Inventory inventory;
    protected final AuctionMasterElite plugin;


    public GUI(AuctionMasterElite plugin, int size, String title) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /**
     * Called when a player clicks inside this GUI.
     * @param event The InventoryClickEvent.
     */
    public abstract void onClick(InventoryClickEvent event);

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
