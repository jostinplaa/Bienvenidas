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

    // Slot constants for AuctionDetailsGUI
    public static final int ITEM_DISPLAY_SLOT = 13; // Central display for the auction item itself
    public static final int BID_BUTTON_SLOT = 30;
    public static final int BUY_NOW_BUTTON_SLOT = 31;
    public static final int BACK_BUTTON_SLOT = 32;

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
        String title = messageManager.getMessage("auction_details_gui_title");
        inventory = Bukkit.createInventory(this, 36, title); // 4 rows, use 'this' as InventoryHolder

        // Add Decorative Panes
        // Accessing config directly for non-standard path, ensure 'plugin' field is available and initialized.
        Material paneMaterial = Material.getMaterial(plugin.getConfig().getString("gui.details_decorative_pane_material", "BLACK_STAINED_GLASS_PANE"));
        if (paneMaterial == null) {
            paneMaterial = Material.BLACK_STAINED_GLASS_PANE; // Fallback
        }
        ItemStack decorativePane = InventoryUtil.createGuiItem(paneMaterial,
                messageManager.getMessage("new_gui_decorative_pane_name"));

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, decorativePane);
        }

        // Show Auctioned Item (Visual) with all details in its lore
        ItemStack centralDisplayItem = auctionItem.getItemStack().clone();
        org.bukkit.inventory.meta.ItemMeta meta = centralDisplayItem.getItemMeta();
        if (meta != null) {
            // Set Display Name
            String originalItemName = meta.hasDisplayName() ? meta.getDisplayName() : centralDisplayItem.getType().name().replace("_", " ");
            String displayNameFormat = messageManager.getMessage("item_default_name_format");
            meta.setDisplayName(displayNameFormat.replace("%item_name%", originalItemName));

            List<String> lore = new ArrayList<>();
            lore.add(" "); // Initial spacer

            String idFormat = messageManager.getMessage("details_auction_id");
            lore.add(idFormat.replace("%id%", String.valueOf(auctionItem.getId())));

            String sellerFormat = messageManager.getMessage("details_seller");
            lore.add(sellerFormat.replace("%seller%", auctionItem.getSellerName())); // Assumes %seller% is the placeholder in messages.yml

            String currentBidFormat = messageManager.getMessage("details_current_bid");
            lore.add(currentBidFormat.replace("%price%", String.format("%,.2f", auctionItem.getCurrentBid())).replace("%currency%", configManager.getCurrencySymbol()));

            if (auctionItem.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed()) {
                String buyNowFormat = messageManager.getMessage("details_buyout_price");
                lore.add(buyNowFormat.replace("%price%", String.format("%,.2f", auctionItem.getBuyNowPrice())).replace("%currency%", configManager.getCurrencySymbol()));
            } else {
                lore.add(messageManager.getMessage("item_lore_buy_now_not_available"));
            }

            String timeFormat = messageManager.getMessage("details_time_remaining");
            lore.add(timeFormat.replace("%time%", InventoryUtil.formatTime((auctionItem.getStartTime() + auctionItem.getDuration()) - System.currentTimeMillis())));

            meta.setLore(lore);
            centralDisplayItem.setItemMeta(meta);
        }
        inventory.setItem(13, centralDisplayItem); // Central slot in a 4-row inventory

        // Slots 19-23 are now decorative panes by default from the initial loop.

        // Add Action Buttons
        // Slot 30: Bid Button
        inventory.setItem(30, InventoryUtil.createGuiItem(Material.GREEN_WOOL, // Or LIME_WOOL
                messageManager.getMessage("button_bid")));

        // Slot 31: Buy Now Button
        if (auctionItem.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed() && auctionItem.getStatus() == AuctionStatus.ACTIVE) {
            inventory.setItem(31, InventoryUtil.createGuiItem(Material.EMERALD_BLOCK,
                    messageManager.getMessage("new_gui_button_buy_now_name")));
        } else {
            // Decorative pane remains, or an item indicating unavailability
        }

        // Slot 32: Back Button
        inventory.setItem(32, InventoryUtil.createGuiItem(Material.RED_WOOL, // Or RED_STAINED_GLASS_PANE
                messageManager.getMessage("new_gui_button_back_name")));
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
