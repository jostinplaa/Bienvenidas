package com.example.aetherauctions.gui;

import com.example.aetherauctions.AetherAuctions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class BaseGui implements InventoryHolder {

    protected final AetherAuctions plugin;
    protected final Player player;
    protected Inventory inventory;

    public BaseGui(AetherAuctions plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract String getTitle();
    protected abstract int getSize();
    protected abstract void setupItems();

    public void open() {
        inventory = Bukkit.createInventory(this, getSize(), getTitle());
        this.setupItems();
        player.openInventory(inventory);
    }

    // This method will be called by GuiManager
    public abstract void handleClick(InventoryClickEvent event);

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
