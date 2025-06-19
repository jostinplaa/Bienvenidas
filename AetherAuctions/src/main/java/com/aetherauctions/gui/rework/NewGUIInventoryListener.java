package com.aetherauctions.gui.rework;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.config.MessageManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class NewGUIInventoryListener implements Listener {

    private final AetherAuctions plugin;
    private final NewGUIManager newGuiManager;
    private final AuctionManager auctionManager;
    private final MessageManager messageManager;

    // Define constants for slots to avoid magic numbers
    // For NewMainAuctionGUI (54 slots)
    private static final int MAIN_SLOT_PREVIOUS_PAGE = 48;
    private static final int MAIN_SLOT_CLOSE = 49;
    private static final int MAIN_SLOT_NEXT_PAGE = 50;
    private static final int MAIN_AUCTION_ITEMS_START_SLOT = 0;
    private static final int MAIN_AUCTION_ITEMS_END_SLOT = 35;

    // For AuctionDetailsGUI (36 slots)
    private static final int DETAILS_SLOT_BID = 30; // Example, adjust if needed
    private static final int DETAILS_SLOT_BUY_NOW = 31; // Example
    private static final int DETAILS_SLOT_BACK = 32; // Example


    public NewGUIInventoryListener(AetherAuctions plugin) {
        this.plugin = plugin;
        this.newGuiManager = plugin.getNewGuiManager();
        this.auctionManager = plugin.getAuctionManager();
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();

        if (topInventory == null || topInventory.getHolder() == null) {
            return;
        }

        InventoryHolder holder = topInventory.getHolder();

        if (holder instanceof NewMainAuctionGUI) {
            event.setCancelled(true);
            NewMainAuctionGUI mainGui = (NewMainAuctionGUI) holder;
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getRawSlot(); // Use raw slot for top inventory consistency

            // Check if click is in top inventory
            if (slot >= topInventory.getSize()) {
                 // Click was in player's inventory
                return;
            }

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            if (slot == MAIN_SLOT_CLOSE) {
                player.closeInventory();
            } else if (slot == MAIN_SLOT_PREVIOUS_PAGE) {
                if (mainGui.getCurrentPage() > 0) {
                    // Check if the item is actually an arrow, not a decorative pane
                    if (clickedItem.getType() == Material.ARROW) {
                        newGuiManager.openNewMainAuctionGUI(player, mainGui.getCurrentPage() - 1);
                    }
                }
            } else if (slot == MAIN_SLOT_NEXT_PAGE) {
                 // Check if the item is actually an arrow
                if (clickedItem.getType() == Material.ARROW) {
                    if (mainGui.getCurrentPage() < mainGui.getTotalPages() - 1) {
                        newGuiManager.openNewMainAuctionGUI(player, mainGui.getCurrentPage() + 1);
                    }
                }
            } else if (slot >= MAIN_AUCTION_ITEMS_START_SLOT && slot <= MAIN_AUCTION_ITEMS_END_SLOT) {
                if (event.isRightClick()) {
                    AuctionItem auctionItem = mainGui.getAuctionItemAtSlot(slot);
                    if (auctionItem != null) {
                        newGuiManager.openAuctionDetailsGUI(player, auctionItem, mainGui.getCurrentPage());
                    }
                }
            }

        } else if (holder instanceof AuctionDetailsGUI) {
            event.setCancelled(true);
            AuctionDetailsGUI detailsGui = (AuctionDetailsGUI) holder;
            AuctionItem auctionItem = detailsGui.getAuctionItem();
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getRawSlot(); // Use raw slot

            if (slot >= topInventory.getSize()) {
                // Click was in player's inventory
               return;
           }

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            // Ensure auction item is still valid for actions
            if (auctionItem == null || auctionItem.getStatus() != AuctionStatus.ACTIVE) {
                // Optionally send a message that the auction is no longer active
                messageManager.sendMessage(player, "auction_ended_info_closed"); // Or a more generic message
                newGuiManager.openNewMainAuctionGUI(player, detailsGui.getPreviousPage()); // Go back
                return;
            }


            if (slot == DETAILS_SLOT_BACK) {
                newGuiManager.openNewMainAuctionGUI(player, detailsGui.getPreviousPage());
            } else if (slot == DETAILS_SLOT_BUY_NOW) {
                // Double check buy now conditions, though AuctionManager will also do it
                if (auctionItem.getBuyNowPrice() > 0 && plugin.getConfigManager().isBuyNowAllowed() && clickedItem.getType() == Material.EMERALD_BLOCK) {
                    boolean success = auctionManager.buyNow(player, auctionItem.getId());
                    if (success) {
                        player.closeInventory(); // Close on successful buy
                    }
                    // AuctionManager handles feedback messages
                } else {
                     messageManager.sendMessage(player, "error_auction_not_buyable_details_gui");
                }
            } else if (slot == DETAILS_SLOT_BID) {
                 if (clickedItem.getType() == Material.GREEN_WOOL) { // Or LIME_WOOL
                    player.closeInventory();
                    newGuiManager.setPlayerPendingBid(player.getUniqueId(), auctionItem.getId());
                    messageManager.sendMessage(player, "new_gui_chat_prompt_bid_amount");
                 }
            }
        }
    }
}
