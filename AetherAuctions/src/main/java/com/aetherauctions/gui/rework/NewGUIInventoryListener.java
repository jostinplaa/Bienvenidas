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

    // Constants are now defined in their respective GUI classes (NewMainAuctionGUI, AuctionDetailsGUI)

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
            event.setCancelled(true); // Cancel event first
            NewMainAuctionGUI mainGui = (NewMainAuctionGUI) holder;
            // Player player = (Player) event.getWhoClicked(); // Already defined above
            ItemStack clickedItemRaw = event.getCurrentItem(); // Renombrado para claridad
            int slot = event.getRawSlot(); // Use raw slot

            plugin.getLogger().info("[ReworkListener] Detected click in NewMainAuctionGUI. Slot: " + slot + ", ClickType: " + event.getClick().name());

            if (clickedItemRaw == null || clickedItemRaw.getType() == Material.AIR) {
                plugin.getLogger().info("[ReworkListener] Clicked item is null or AIR. Aborting further processing for this slot.");
                return; // No procesar si no hay ítem
            }

            // Procesamiento para slots de ítems de subasta
            if (slot >= NewMainAuctionGUI.AUCTION_ITEMS_START_SLOT && slot <= NewMainAuctionGUI.AUCTION_ITEMS_END_SLOT) {
                plugin.getLogger().info("[ReworkListener] Slot " + slot + " is an auction item slot.");

                if (event.isRightClick()) {
                    plugin.getLogger().info("[ReworkListener] Right-click detected on slot " + slot + ".");
                    AuctionItem auctionItem = mainGui.getAuctionItemAtSlot(slot);

                    if (auctionItem != null) {
                        plugin.getLogger().info("[ReworkListener] AuctionItem found for slot " + slot + ". ID: " + auctionItem.getId());
                        // Log solicitado por el usuario, justo antes de la acción:
                        plugin.getLogger().info("[DEBUG] Se intentó abrir la GUI de detalles para la subasta ID: " + auctionItem.getId());
                        newGuiManager.openAuctionDetailsGUI(player, auctionItem, mainGui.getCurrentPage());
                    } else {
                        plugin.getLogger().warning("[ReworkListener] Right-click on auction slot " + slot + " but getAuctionItemAtSlot returned NULL. No action taken.");
                    }
                } else {
                    plugin.getLogger().info("[ReworkListener] Click on auction slot " + slot + " was not a right-click. Type: " + event.getClick().name() + ". No action taken for details GUI.");
                }
            }
            // Procesamiento para botones de navegación (Cerrar, Anterior, Siguiente)
            else if (slot == NewMainAuctionGUI.CLOSE_BUTTON_SLOT) {
                plugin.getLogger().info("[ReworkListener] Close button clicked.");
                player.closeInventory();
            } else if (slot == NewMainAuctionGUI.PREVIOUS_PAGE_BUTTON_SLOT) {
                 if(clickedItemRaw.getType() == Material.ARROW) {
                    plugin.getLogger().info("[ReworkListener] Previous page button clicked.");
                     if (mainGui.getCurrentPage() > 0) { // Check moved inside to ensure it's an arrow first
                        newGuiManager.openNewMainAuctionGUI(player, mainGui.getCurrentPage() - 1);
                     } else {
                        plugin.getLogger().info("[ReworkListener] Previous page button clicked, but already on first page or item is not an arrow.");
                     }
                 } else {
                    plugin.getLogger().info("[ReworkListener] Click on previous page slot, but item is not an arrow: " + clickedItemRaw.getType());
                 }
            } else if (slot == NewMainAuctionGUI.NEXT_PAGE_BUTTON_SLOT) {
                if(clickedItemRaw.getType() == Material.ARROW) {
                    plugin.getLogger().info("[ReworkListener] Next page button clicked.");
                    if (mainGui.getCurrentPage() < mainGui.getTotalPages() - 1) { // Check moved inside
                        newGuiManager.openNewMainAuctionGUI(player, mainGui.getCurrentPage() + 1);
                    } else {
                         plugin.getLogger().info("[ReworkListener] Next page button clicked, but already on last page or item is not an arrow.");
                    }
                } else {
                    plugin.getLogger().info("[ReworkListener] Click on next page slot, but item is not an arrow: " + clickedItemRaw.getType());
                }
            } else {
                plugin.getLogger().info("[ReworkListener] Click on unhandled slot in NewMainAuctionGUI: " + slot);
            }
        } else if (holder instanceof AuctionDetailsGUI) {
            event.setCancelled(true);
            AuctionDetailsGUI detailsGui = (AuctionDetailsGUI) holder;
            // Player player = (Player) event.getWhoClicked(); // Already defined
            ItemStack clickedItemRaw = event.getCurrentItem();
            int slot = event.getRawSlot(); // Use raw slot

            plugin.getLogger().info("[ReworkListener] Detected click in AuctionDetailsGUI. Slot: " + slot + ", ClickType: " + event.getClick().name());

            if (slot >= topInventory.getSize()) {
                plugin.getLogger().info("[ReworkListener] Click was in player's inventory for AuctionDetailsGUI.");
                return;
            }

            if (clickedItemRaw == null || clickedItemRaw.getType() == Material.AIR) {
                plugin.getLogger().info("[ReworkListener] Clicked item in AuctionDetailsGUI is null or AIR.");
                return;
            }

            AuctionItem auctionItem = detailsGui.getAuctionItem();

            if (auctionItem == null) { // Should not happen if GUI opened correctly
                 plugin.getLogger().severe("[ReworkListener] AuctionItem is NULL in AuctionDetailsGUI for player " + player.getName());
                 player.closeInventory();
                 messageManager.sendMessage(player, "internal_error");
                 return;
            }

            // Ensure auction item is still valid for actions, even if it was when GUI opened
            if (auctionItem.getStatus() != AuctionStatus.ACTIVE) {
                plugin.getLogger().info("[ReworkListener] Auction " + auctionItem.getId() + " is no longer active. Status: " + auctionItem.getStatus());
                messageManager.sendMessage(player, "auction_ended_info_closed");
                newGuiManager.openNewMainAuctionGUI(player, detailsGui.getPreviousPage());
                return;
            }

            if (slot == AuctionDetailsGUI.BACK_BUTTON_SLOT) {
                plugin.getLogger().info("[ReworkListener] Back button clicked in AuctionDetailsGUI. Returning to page " + detailsGui.getPreviousPage());
                newGuiManager.openNewMainAuctionGUI(player, detailsGui.getPreviousPage());
            } else if (slot == AuctionDetailsGUI.BUY_NOW_BUTTON_SLOT) {
                if (clickedItemRaw.getType() == Material.EMERALD_BLOCK) { // Check if it's the actual button
                    plugin.getLogger().info("[ReworkListener] Buy Now button clicked in AuctionDetailsGUI for auction ID: " + auctionItem.getId());
                    // Double check buy now conditions, though AuctionManager will also do it
                    if (auctionItem.getBuyNowPrice() > 0 && plugin.getConfigManager().isBuyNowAllowed()) {
                        boolean success = auctionManager.buyNow(player, auctionItem.getId());
                        if (success) {
                            player.closeInventory();
                        }
                        // AuctionManager handles feedback messages
                    } else {
                        plugin.getLogger().warning("[ReworkListener] Buy Now attempt on non-buyable item in details GUI. Auction ID: " + auctionItem.getId());
                        messageManager.sendMessage(player, "error_auction_not_buyable_details_gui");
                    }
                } else {
                     plugin.getLogger().info("[ReworkListener] Click on Buy Now slot in AuctionDetailsGUI, but item is " + clickedItemRaw.getType());
                }
            } else if (slot == AuctionDetailsGUI.BID_BUTTON_SLOT) {
                 if (clickedItemRaw.getType() == Material.GREEN_WOOL) {
                    plugin.getLogger().info("[ReworkListener] Bid button clicked in AuctionDetailsGUI for auction ID: " + auctionItem.getId());
                    player.closeInventory();
                    newGuiManager.setPlayerPendingBid(player.getUniqueId(), auctionItem.getId());
                    messageManager.sendMessage(player, "new_gui_chat_prompt_bid_amount");
                 } else {
                    plugin.getLogger().info("[ReworkListener] Click on Bid slot in AuctionDetailsGUI, but item is " + clickedItemRaw.getType());
                 }
            } else if (slot == AuctionDetailsGUI.ITEM_DISPLAY_SLOT) {
                 plugin.getLogger().info("[ReworkListener] Click on central display item in AuctionDetailsGUI. No action.");
            }
            else {
                 plugin.getLogger().info("[ReworkListener] Click on unhandled slot in AuctionDetailsGUI: " + slot);
            }
        }
    }
}
