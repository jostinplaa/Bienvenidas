package com.aetherauctions.gui.rework;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder; // Import InventoryHolder
import org.bukkit.inventory.ItemStack;
// ItemMeta is not directly used in this class after initial review, but good to keep if complex item manipulation was needed.
// import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuctionDetailsGUI implements InventoryHolder { // Implement InventoryHolder

    private final AetherAuctions plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Player player;
    private final AuctionItem auctionItem;
    private final int previousPage;
    private Inventory inventory;

    public AuctionDetailsGUI(AetherAuctions plugin, Player player, AuctionItem auctionItem, int previousPage) {
        this.plugin = plugin;
        this.player = player;
        this.auctionItem = auctionItem;
        this.previousPage = previousPage;
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        buildGUI();
    }

    private void buildGUI() {
        String title = messageManager.getMessage("auction_details_gui_title", "&1Detalles de la Subasta"); // Changed to specific key
        inventory = Bukkit.createInventory(this, 36, title); // 4 rows, use 'this' as InventoryHolder

        // Add Decorative Panes
        // Accessing config directly for non-standard path, ensure 'plugin' field is available and initialized.
        Material paneMaterial = Material.getMaterial(plugin.getConfig().getString("gui.details_decorative_pane_material", "BLACK_STAINED_GLASS_PANE"));
        if (paneMaterial == null) {
            paneMaterial = Material.BLACK_STAINED_GLASS_PANE; // Fallback
        }
        ItemStack decorativePane = InventoryUtil.createGuiItem(paneMaterial,
                messageManager.getMessage("new_gui_decorative_pane_name", "&r "));

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, decorativePane);
        }

        // Show Auctioned Item (Visual) with all details in its lore
        ItemStack centralDisplayItem = auctionItem.getItemStack().clone();
        org.bukkit.inventory.meta.ItemMeta meta = centralDisplayItem.getItemMeta();
        if (meta != null) {
            // Set Display Name (using a new message key or existing one if suitable)
            String originalItemName = meta.hasDisplayName() ? meta.getDisplayName() : centralDisplayItem.getType().name().replace("_", " ");
            meta.setDisplayName(messageManager.getMessage("item_default_name_format", "&r%item_name%").replace("%item_name%", originalItemName)); // Use item_default_name_format

            List<String> lore = new ArrayList<>();
            lore.add(" "); // Initial spacer
            lore.add(messageManager.getMessage("details_auction_id", "&7ID: &b%id%")
                    .replace("%id%", String.valueOf(auctionItem.getId())));
            lore.add(messageManager.getMessage("details_seller", "&7Vendedor: &6%seller%")
                    .replace("%seller%", auctionItem.getSellerName())
                    .replace("%name%", auctionItem.getSellerName())); // %name% is used in details_seller
            lore.add(messageManager.getMessage("details_current_bid", "&7Puja Actual: &e%price% %currency%")
                    .replace("%price%", String.format("%,.2f", auctionItem.getCurrentBid()))
                    .replace("%currency%", configManager.getCurrencySymbol()));

            if (auctionItem.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed()) {
                lore.add(messageManager.getMessage("details_buyout_price", "&7Compra Directa: &a%price% %currency%")
                        .replace("%price%", String.format("%,.2f", auctionItem.getBuyNowPrice()))
                        .replace("%currency%", configManager.getCurrencySymbol()));
            } else {
                lore.add(messageManager.getMessage("item_lore_buy_now_not_available", "&7Compra Directa: &cNo disponible")); // Re-using existing key
            }
            lore.add(messageManager.getMessage("details_time_remaining", "&7Tiempo: &c%time%")
                    .replace("%time%", InventoryUtil.formatTime((auctionItem.getStartTime() + auctionItem.getDuration()) - System.currentTimeMillis())));

            meta.setLore(lore);
            centralDisplayItem.setItemMeta(meta);
        }
        inventory.setItem(13, centralDisplayItem); // Central slot in a 4-row inventory

        // Slots 19-23 are now decorative panes by default from the initial loop.

        // Add Action Buttons
        // Slot 30: Bid Button (Adjusted slot for better spacing if needed)
        inventory.setItem(30, InventoryUtil.createGuiItem(Material.GREEN_WOOL, // Or LIME_WOOL
                messageManager.getMessage("button_bid", "&aPujar"))); // Use standard button_bid

        // Slot 31: Buy Now Button
        if (auctionItem.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed() && auctionItem.getStatus() == AuctionStatus.ACTIVE) {
            inventory.setItem(31, InventoryUtil.createGuiItem(Material.EMERALD_BLOCK,
                    messageManager.getMessage("new_gui_button_buy_now_name", "&6Comprar Ahora")));
        } else {
            // Decorative pane remains, or an item indicating unavailability
        }

        // Slot 32: Back Button
        inventory.setItem(32, InventoryUtil.createGuiItem(Material.RED_WOOL, // Or RED_STAINED_GLASS_PANE
                messageManager.getMessage("new_gui_button_back_name", "&cVolver Atrás")));
    }

    public void open() {
        player.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public AuctionItem getAuctionItem() {
        return auctionItem;
    }

    public Player getPlayer() {
        return player;
    }

    public int getPreviousPage() {
        return previousPage;
    }
}
