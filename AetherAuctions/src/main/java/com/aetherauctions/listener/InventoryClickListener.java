package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.gui.GUIManager;
import com.aetherauctions.gui.rework.NewMainAuctionGUI; // Import NewMainAuctionGUI
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
        if (event.getClickedInventory() == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        String inventoryTitle = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();

        // Titles for GUIs still managed by this listener
        String createGuiTitle = messageManager.stripColors(messageManager.getRaw("auction_create_gui_title"));
        String myAuctionsBaseTitle = messageManager.stripColors(messageManager.getRaw("my_auctions_gui_title").split("\\(")[0]).trim();
        String historyBaseTitle = messageManager.stripColors(messageManager.getRaw("auction_history_gui_title").split("\\(")[0]).trim();
        // String oldMainGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("main_gui_title").split("\\(")[0]).trim(); // Kept for reference, but logic using it will be removed

        boolean isCreateGui = inventoryTitle.equals(createGuiTitle);
        boolean isMyAuctionsGui = inventoryTitle.startsWith(myAuctionsBaseTitle);
        boolean isHistoryGui = inventoryTitle.startsWith(historyBaseTitle);
        // boolean isDeprecatedMainGui = inventoryTitle.startsWith(oldMainGuiBaseTitle); // Logic for this will be removed

        // Simplified check: if it's one of the GUIs this listener still handles.
        // The NewGUIInventoryListener handles the new GUIs.
        boolean isPluginGui = isCreateGui || isMyAuctionsGui || isHistoryGui;

        if (!isPluginGui) {
            return;
        }

        // Removed block for isDeprecatedMainGui

        if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        } else if (event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            event.setCancelled(true);
            return;
        }

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            if (!(isCreateGui && event.getSlot() == 13)) {
                return;
            }
        }

        String clickedItemName = "";
         if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()){
            clickedItemName = messageManager.stripColors(clickedItem.getItemMeta().getDisplayName());
        }

        // Removed block for isNewMainGui

        if (isCreateGui) { // Adjusted else-if to if
            if (event.getSlot() == 13) { // Slot for placing item in Create GUI
                ItemStack itemOnCursor = event.getCursor();
                ItemStack currentItemInSlot = guiManager.getItemToAuction(player.getUniqueId());

                if (itemOnCursor != null && itemOnCursor.getType() != Material.AIR) {
                    if (currentItemInSlot != null) {
                        player.setItemOnCursor(currentItemInSlot.clone());
                    } else {
                        player.setItemOnCursor(null);
                    }
                    guiManager.setItemToAuction(player.getUniqueId(), itemOnCursor.clone());
                } else if (currentItemInSlot != null) {
                    player.setItemOnCursor(currentItemInSlot.clone());
                    guiManager.setItemToAuction(player.getUniqueId(), null);
                }
                guiManager.openCreateAuctionGui(player);
                return;
            }
            if(clickedItem != null && clickedItem.getType() != Material.AIR) { // Ensure not clicking an empty part of the item slot if logic changes
                 handleCreateAuctionGuiButtonClick(player, clickedItemName);
            }
        }
        // Removed block for isAuctionDetailsGui
        else if (isMyAuctionsGui && clickedItem != null) {
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());
             if (event.getSlot() < 45 && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) { // Item click
                List<String> lore = clickedItem.getItemMeta().getLore();
                String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith(messageManager.stripColors(messageManager.getRaw("lore_auction_id").split(":")[0] + ":"))).findFirst().orElse(null);
                if (idStringLore != null) {
                    try {
                        int auctionIdToCancel = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1).trim()));
                        AuctionItem auction = auctionManager.getAuction(auctionIdToCancel);
                        if (auction == null) {
                             try{ auction = plugin.getDatabaseManager().getAuction(auctionIdToCancel); } catch (Exception e) {}
                        }

                        if (auction != null && auction.getStatus() == com.aetherauctions.auction.AuctionStatus.ACTIVE) {
                             auctionManager.cancelAuction(player, auctionIdToCancel);
                             guiManager.openMyAuctionsGui(player, currentPage);
                        } else if (auction != null) {
                            messageManager.sendMessage(player, "cannot_cancel_auction_not_active");
                        } else {
                             messageManager.sendMessage(player, "invalid_auction_id", "%id%", String.valueOf(auctionIdToCancel));
                             guiManager.openMyAuctionsGui(player, currentPage);
                        }
                    } catch (Exception e) {
                        messageManager.sendMessage(player, "internal_error");
                         plugin.getLogger().severe("Error parsing auction ID from MyAuctions lore: " + idStringLore + " for player " + player.getName() + " Error: " + e.getMessage());
                    }
                }
            } else {
                handleMyAuctionsGuiButtonClick(player, clickedItemName, currentPage);
            }
        } else if (isHistoryGui && clickedItem != null) {
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());
            handleAuctionHistoryGuiButtonClick(player, clickedItemName, currentPage);
        }
    }

    // Removed onInventoryClose as it was tied to the old AuctionDetailsGUI state (playerViewingAuctionId)

    // Removed handleMainGuiButtonClick as it's no longer relevant

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
                new NewMainAuctionGUI(plugin, player, 0).open(); // Direct instantiation
            } else {
                guiManager.openCreateAuctionGui(player);
            }
        }
    }

    // Removed handleAuctionInfoGuiButtonClick as it's no longer relevant

    private void handleMyAuctionsGuiButtonClick(Player player, String clickedItemName, int currentPage) {
        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_previous_page")))) {
            if (currentPage > 1) guiManager.openMyAuctionsGui(player, currentPage - 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_next_page")))) {
            guiManager.openMyAuctionsGui(player, currentPage + 1);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_refresh")))) {
            guiManager.openMyAuctionsGui(player, currentPage);
        } else if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_main_auctions")))) {
             new NewMainAuctionGUI(plugin, player, 0).open(); // Direct instantiation
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
             new NewMainAuctionGUI(plugin, player, 0).open(); // Direct instantiation
        }
    }
}
