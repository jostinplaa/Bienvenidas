package com.julesmc.subastas.gui.helpers;

import com.julesmc.subastas.SubastasPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;

public class AnvilInputHelper {

    private final SubastasPlugin plugin;
    private final Player player;
    private final ItemStack initialItem;
    private final Consumer<String> onConfirm;
    private Inventory anvilInventory;
    private AnvilListener listener;

    public AnvilInputHelper(SubastasPlugin plugin, Player player, String initialText, Consumer<String> onConfirm) {
        this.plugin = plugin;
        this.player = player;
        this.initialItem = new ItemStack(Material.PAPER); // Default item
        ItemMeta meta = initialItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(initialText); // Set initial text as item name
            initialItem.setItemMeta(meta);
        }
        this.onConfirm = onConfirm;
    }

    public AnvilInputHelper(SubastasPlugin plugin, Player player, ItemStack initialItem, Consumer<String> onConfirm) {
        this.plugin = plugin;
        this.player = player;
        this.initialItem = initialItem;
        this.onConfirm = onConfirm;
    }


    public void openAnvil() {
        anvilInventory = Bukkit.createInventory(player, InventoryType.ANVIL, "Enter Bid"); // Title can be customized
        anvilInventory.setItem(0, initialItem); // Place the item in the first slot

        listener = new AnvilListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        player.openInventory(anvilInventory);
    }

    private class AnvilListener implements Listener {
        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (event.getWhoClicked().equals(player) && event.getInventory().equals(anvilInventory)) {
                if (event.getSlotType() == InventoryType.SlotType.RESULT) { // Clicked the result slot
                    ItemStack item = event.getCurrentItem();
                    if (item != null && item.hasItemMeta()) {
                        String bidText = item.getItemMeta().getDisplayName();
                        // Prevent anvil use sound and item pickup by player
                        event.setCancelled(true);
                        // Schedule task to close inventory and process, to avoid issues with event handling
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            player.closeInventory(); // Close anvil first
                            onConfirm.accept(bidText); // Then process bid
                        }, 1L);
                        return;
                    }
                }
                // Allow modifying the input item (slot 0) or taking back the initial item
                if (event.getSlot() == 0 || event.getSlot() == 1) {
                     // Allow if it's about placing or taking from the first two slots
                    if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_ONE ||
                        event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.PICKUP_ALL ||
                        event.getAction() == InventoryAction.PICKUP_HALF || event.getAction() == InventoryAction.PICKUP_ONE ||
                        event.getAction() == InventoryAction.PICKUP_SOME || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                        // Fine, allow modification of input slots
                        return;
                    }
                }
                 // Prevent other interactions like shift-clicking items into the anvil from player inventory
                if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY &&
                    event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                    // if it's not moving to player inventory and it is player inventory, allow
                } else if (event.getClickedInventory() == anvilInventory) {
                     event.setCancelled(true);
                }
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (event.getPlayer().equals(player) && event.getInventory().equals(anvilInventory)) {
                HandlerList.unregisterAll(listener); // Unregister listeners when anvil is closed
                // The item in slot 0 (input) might be taken by the player on close if not handled.
                // Clear the anvil inventory to prevent this, though it's usually handled by closing it before processing.
                event.getInventory().clear();
            }
        }
    }
}
