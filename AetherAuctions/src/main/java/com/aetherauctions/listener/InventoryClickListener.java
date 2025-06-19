package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.gui.GUIManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class InventoryClickListener implements Listener {

    private final AetherAuctions plugin;
    private final GUIManager guiManager;
    private final AuctionManager auctionManager;
    private final MessageManager messageManager;

    public InventoryClickListener(AetherAuctions plugin, GUIManager guiManager, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.auctionManager = auctionManager;
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) { // Check 1: Clicked inventory is null
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        String inventoryTitle = event.getView().getTitle();

        // Standardize title checks
        String mainGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("main_gui_title").split("\\(")[0]).trim();
        String createGuiTitle = messageManager.stripColors(messageManager.getRaw("auction_create_gui_title"));
        String infoGuiTitle = messageManager.stripColors(messageManager.getRaw("auction_info_gui_title"));
        String myAuctionsBaseTitle = messageManager.stripColors(messageManager.getRaw("my_auctions_gui_title").split("\\(")[0]).trim();
        String historyBaseTitle = messageManager.stripColors(messageManager.getRaw("auction_history_gui_title").split("\\(")[0]).trim();

        boolean isCustomGui = inventoryTitle.startsWith(mainGuiBaseTitle) ||
                              inventoryTitle.equals(createGuiTitle) ||
                              inventoryTitle.equals(infoGuiTitle) ||
                              inventoryTitle.startsWith(myAuctionsBaseTitle) ||
                              inventoryTitle.startsWith(historyBaseTitle);

        if (!isCustomGui) { // Check 2: Not a custom plugin GUI
            return;
        }

        // Check if click is in player's own inventory while custom GUI is open
        if (event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            // For this subtask, we cancel to prevent shift-clicks into the top GUI.
            // Specific features like "click item in your inv to put in GUI slot" would need explicit un-cancelling if desired later.
            event.setCancelled(true);
            return;
        }

        // From this point, the click is within the top custom GUI.
        // Default cancellation for all interactions within the custom GUI (top inventory).
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();

        // If no item was clicked (empty slot), generally do nothing unless it's a special case
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            // Exception: Allow placing an item into the create GUI's item slot even if it's currently empty
            if (inventoryTitle.equals(createGuiTitle) && event.getSlot() == 13) {
                // Action of placing item from cursor is handled below if cursor is not empty
            } else {
                return; // Clicked on an empty slot that's not a special handler
            }
        }

        // If item has no meta or display name, it's not a button we care about, unless it's the item slot in create GUI
        if (clickedItem != null && (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName())) {
            if (inventoryTitle.equals(createGuiTitle) && event.getSlot() == 13 && clickedItem.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                // This is a real item in the create GUI slot, allow interaction attempts (like taking it)
            } else {
                return; // Not a recognized button or special item
            }
        }

        String clickedItemName = "";
        if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()){
            clickedItemName = messageManager.stripColors(clickedItem.getItemMeta().getDisplayName());
        }


        // GUI specific logic
        if (inventoryTitle.startsWith(mainGuiBaseTitle)) {
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());
            if (event.getSlot() < 45 && clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) { // Auction Item Click
                List<String> lore = clickedItem.getItemMeta().getLore();
                String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith("ID:")).findFirst().orElse(null);
                if (idStringLore != null) {
                    try {
                        int auctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1)));
                        AuctionItem auction = auctionManager.getAuction(auctionId);
                        if (auction == null) {
                           try { auction = plugin.getDatabaseManager().getAuction(auctionId); }
                           catch (Exception dbExc) {plugin.getLogger().warning("Failed to get auction " + auctionId + " from DB in click listener: " + dbExc.getMessage());}
                        }

                        if (auction != null) {
                            guiManager.openAuctionInfoGui(player, auction);
                        } else {
                            messageManager.sendMessage(player, "invalid_auction_id", "%id%", String.valueOf(auctionId));
                            guiManager.openMainAuctionGui(player, currentPage);
                        }
                    } catch (Exception e) {
                        messageManager.sendMessage(player, "internal_error");
                        plugin.getLogger().severe("Error parsing auction ID from lore: " + idStringLore + " for player " + player.getName() + " Error: " + e.getMessage());
                    }
                }
            } else if (clickedItem != null) { // Button click
                handleMainGuiButtonClick(player, clickedItemName, currentPage);
            }
        } else if (inventoryTitle.equals(createGuiTitle)) {
            if (event.getSlot() == 13) { // Item slot in create GUI
                ItemStack itemOnCursor = event.getCursor(); // Item on player's cursor
                ItemStack currentItemInSlot = guiManager.getItemToAuction(player.getUniqueId()); // Logical item

                if (itemOnCursor != null && itemOnCursor.getType() != Material.AIR) { // Player is trying to place an item from cursor
                    if (currentItemInSlot != null) { // If slot already has an item, swap it to cursor
                        player.setItemOnCursor(currentItemInSlot.clone());
                    } else {
                        player.setItemOnCursor(null); // Clear cursor if slot was empty
                    }
                    guiManager.setItemToAuction(player.getUniqueId(), itemOnCursor.clone());
                } else if (currentItemInSlot != null) { // Player has empty cursor and is clicking the slot to take the item
                    player.setItemOnCursor(currentItemInSlot.clone());
                    guiManager.setItemToAuction(player.getUniqueId(), null);
                }
                // else: empty cursor clicking empty placeholder - do nothing to placeholder
                guiManager.openCreateAuctionGui(player); // Refresh GUI
                return;
            }
            // For other slots, it's a button click
            if(clickedItem != null && clickedItem.getType() != Material.AIR) {
                 handleCreateAuctionGuiButtonClick(player, clickedItemName);
            }
        } else if (inventoryTitle.equals(infoGuiTitle) && clickedItem != null) {
            ItemStack auctionDisplayItem = event.getView().getTopInventory().getItem(4);
            int auctionId = -1;
            if (auctionDisplayItem != null && auctionDisplayItem.hasItemMeta() && auctionDisplayItem.getItemMeta().hasLore()) {
                 List<String> lore = auctionDisplayItem.getItemMeta().getLore();
                 String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith("ID:")).findFirst().orElse(null);
                 if (idStringLore != null) {
                    try {
                        auctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1)));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Could not parse auction ID from info GUI item: " + idStringLore);
                    }
                 }
            }
            handleAuctionInfoGuiButtonClick(player, clickedItemName, auctionId);
        } else if (inventoryTitle.startsWith(myAuctionsBaseTitle) && clickedItem != null) {
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());
             if (event.getSlot() < 45 && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) { // Auction Item Click (for cancellation)
                List<String> lore = clickedItem.getItemMeta().getLore();
                String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith("ID:")).findFirst().orElse(null);
                if (idStringLore != null) {
                    try {
                        int auctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1)));
                        AuctionItem auction = auctionManager.getAuction(auctionId);
                        if (auction == null) {
                             try{ auction = plugin.getDatabaseManager().getAuction(auctionId); } catch (Exception e) {}
                        }

                        if (auction != null && auction.getStatus() == com.aetherauctions.auction.AuctionStatus.ACTIVE) {
                             auctionManager.cancelAuction(player, auctionId);
                             guiManager.openMyAuctionsGui(player, currentPage);
                        } else if (auction != null) {
                            messageManager.sendMessage(player, "cannot_cancel_auction_not_active");
                        } else {
                             messageManager.sendMessage(player, "invalid_auction_id", "%id%", String.valueOf(auctionId));
                             guiManager.openMyAuctionsGui(player, currentPage);
                        }
                    } catch (Exception e) {
                        messageManager.sendMessage(player, "internal_error");
                         plugin.getLogger().severe("Error parsing auction ID from MyAuctions lore: " + idStringLore + " for player " + player.getName() + " Error: " + e.getMessage());
                    }
                }
            } else { // Button click
                handleMyAuctionsGuiButtonClick(player, clickedItemName, currentPage);
            }
        } else if (inventoryTitle.startsWith(historyBaseTitle) && clickedItem != null) {
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());
            handleAuctionHistoryGuiButtonClick(player, clickedItemName, currentPage);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        // Player player = (Player) event.getPlayer();
        // String inventoryTitle = event.getView().getTitle();
        // if (inventoryTitle.equals(messageManager.stripColors(messageManager.getRaw("auction_create_gui_title")))) {
        //    // Potentially clear data if create GUI is closed without confirming,
        //    // but current logic requires explicit clicks. Leaving this out for now.
        //    // guiManager.clearCreateAuctionData(player.getUniqueId());
        // }
    }

    private void handleMainGuiButtonClick(Player player, String clickedItemName, int currentPage) {
        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_previous_page")))) {
            if (currentPage > 1) guiManager.openMainAuctionGui(player, currentPage - 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_next_page")))) {
            guiManager.openMainAuctionGui(player, currentPage + 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_create_auction")))) {
            guiManager.clearCreateAuctionData(player.getUniqueId());
            guiManager.openCreateAuctionGui(player);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_my_auctions")))) {
            guiManager.openMyAuctionsGui(player, 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_history")))) {
            guiManager.openAuctionHistoryGui(player, 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_search_items")))) {
            messageManager.sendMessage(player, "coming_soon_feature");
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_close_gui")))) {
            player.closeInventory();
        }
    }

    private void handleCreateAuctionGuiButtonClick(Player player, String clickedItemName) {
        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_set_duration")))) {
            player.closeInventory();
            plugin.getPlayerInputState().put(player.getUniqueId(), AetherAuctions.PlayerInputState.AWAITING_DURATION);
            messageManager.sendMessage(player, "chat_prompt_enter_duration");
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_set_start_price")))) {
            player.closeInventory();
            plugin.getPlayerInputState().put(player.getUniqueId(), AetherAuctions.PlayerInputState.AWAITING_START_PRICE);
            messageManager.sendMessage(player, "chat_prompt_enter_start_price");
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_set_buy_now_price")))) {
            player.closeInventory();
            plugin.getPlayerInputState().put(player.getUniqueId(), AetherAuctions.PlayerInputState.AWAITING_BUY_NOW_PRICE);
            messageManager.sendMessage(player, "chat_prompt_enter_buy_now_price");
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_confirm_create")))) {
            ItemStack itemToAuction = guiManager.getItemToAuction(player.getUniqueId());
            Long duration = guiManager.getAuctionDuration(player.getUniqueId());
            Double startPrice = guiManager.getAuctionStartPrice(player.getUniqueId());
            Double buyNowPrice = guiManager.getAuctionBuyNowPrice(player.getUniqueId());

            if (itemToAuction == null) {
                messageManager.sendMessage(player, "item_slot_empty");
                guiManager.openCreateAuctionGui(player); return;
            }
            if (duration == null || duration <= 0) {
                messageManager.sendMessage(player, "invalid_duration_format");
                guiManager.openCreateAuctionGui(player); return;
            }
            if (startPrice == null || startPrice <= 0) {
                messageManager.sendMessage(player, "invalid_price_format");
                guiManager.openCreateAuctionGui(player); return;
            }

            boolean success = auctionManager.createAuction(player, itemToAuction, duration, startPrice, buyNowPrice == null ? -1 : buyNowPrice);
            if (success) {
                guiManager.clearCreateAuctionData(player.getUniqueId());
                guiManager.openMainAuctionGui(player, 1);
            } else {
                guiManager.openCreateAuctionGui(player);
            }
        }
    }

    private void handleAuctionInfoGuiButtonClick(Player player, String clickedItemName, int auctionId) {
        if (auctionId == -1 && !clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_auctions")))) { // Allow back even if ID failed
            messageManager.sendMessage(player, "internal_error_no_auction_id_gui");
            guiManager.openMainAuctionGui(player, guiManager.getPlayerCurrentPage(player.getUniqueId()));
            return;
        }

        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_auctions")))) {
            guiManager.openMainAuctionGui(player, guiManager.getPlayerCurrentPage(player.getUniqueId()));
            return;
        }

        AuctionItem auctionItem = auctionManager.getAuction(auctionId);
        if (auctionItem == null) {
            try { auctionItem = plugin.getDatabaseManager().getAuction(auctionId); }
            catch (Exception e) { /* Already logged by DB manager if error */ }
        }

        if (auctionItem == null) {
            messageManager.sendMessage(player, "auction_ended_no_longer_exists");
            guiManager.openMainAuctionGui(player, guiManager.getPlayerCurrentPage(player.getUniqueId()));
            return;
        }

        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_place_bid")))) {
            player.closeInventory();
            plugin.getPlayerInputState().put(player.getUniqueId(), AetherAuctions.PlayerInputState.AWAITING_BID_AMOUNT);
            plugin.getPlayerTargetAuction().put(player.getUniqueId(), auctionId);
            messageManager.sendMessage(player, "chat_prompt_enter_bid_amount", "%id%", String.valueOf(auctionId));
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_buy_now")))) {
            boolean success = auctionManager.buyNow(player, auctionId);
            if (success) {
                 guiManager.openMainAuctionGui(player, 1);
            } else {
                // Refresh info GUI if buy now failed (e.g. not enough money, or item became unavailable)
                AuctionItem potentiallyUpdatedAuction = auctionManager.getAuction(auctionId);
                if(potentiallyUpdatedAuction != null && potentiallyUpdatedAuction.getStatus() == com.aetherauctions.auction.AuctionStatus.ACTIVE) {
                    guiManager.openAuctionInfoGui(player, potentiallyUpdatedAuction);
                } else {
                    guiManager.openMainAuctionGui(player, 1); // Fallback to main if auction is gone
                }
            }
        }
    }

    private void handleMyAuctionsGuiButtonClick(Player player, String clickedItemName, int currentPage) {
        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_previous_page")))) {
            if (currentPage > 1) guiManager.openMyAuctionsGui(player, currentPage - 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_next_page")))) {
            guiManager.openMyAuctionsGui(player, currentPage + 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_refresh")))) {
            guiManager.openMyAuctionsGui(player, currentPage);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_main_auctions")))) {
             guiManager.openMainAuctionGui(player, 1);
        }
    }

    private void handleAuctionHistoryGuiButtonClick(Player player, String clickedItemName, int currentPage) {
        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_previous_page")))) {
            if (currentPage > 1) guiManager.openAuctionHistoryGui(player, currentPage - 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_next_page")))) {
            guiManager.openAuctionHistoryGui(player, currentPage + 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_refresh")))) {
            guiManager.openAuctionHistoryGui(player, currentPage);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_main_auctions")))) {
             guiManager.openMainAuctionGui(player, 1);
        }
    }
}
