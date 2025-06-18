package com.example.aetherauctions.gui;

import com.example.aetherauctions.AetherAuctions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {

    private final AetherAuctions plugin;
    private final Map<UUID, BaseGui> openGuis = new HashMap<>();

    public GuiManager(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    public void openMainGui(Player player) {
        // Close any existing GUI first to prevent weirdness
        if (openGuis.containsKey(player.getUniqueId())) {
            // player.closeInventory(); // This might trigger onInventoryClose and remove from map,
                                 // so let's be careful or just overwrite.
        }
        MainAuctionGui mainGui = new MainAuctionGui(plugin, player, 0); // Start at page 0
        mainGui.open();
        openGuis.put(player.getUniqueId(), mainGui);
    }

    public void openGuiForPlayer(Player player, BaseGui gui) {
        gui.open();
        openGuis.put(player.getUniqueId(), gui);
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        UUID playerUuid = player.getUniqueId();

        if (openGuis.containsKey(playerUuid)) {
            BaseGui gui = openGuis.get(playerUuid);
            // Check if the click is within the GUI that we are tracking for the player
            // event.getInventory() is the top inventory, event.getClickedInventory() is the one actually clicked.
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(gui.getInventory())) {
                 gui.handleClick(event);
            } else if (event.isShiftClick() && event.getInventory().equals(gui.getInventory())) {
                // Block shift-clicks into the GUI from player inventory
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID playerUuid = player.getUniqueId();

            // Only remove if the closed inventory is the one we tracked
            BaseGui openGui = openGuis.get(playerUuid);
            if (openGui != null && event.getInventory().equals(openGui.getInventory())) {
                 openGuis.remove(playerUuid);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }

    public boolean isPlayerInGui(UUID playerUuid) {
        return openGuis.containsKey(playerUuid);
    }

    public BaseGui getOpenGui(UUID playerUuid) {
        return openGuis.get(playerUuid);
    }
}
