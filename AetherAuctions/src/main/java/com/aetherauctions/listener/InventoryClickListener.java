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
        if (event.getClickedInventory() == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        String inventoryTitle = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();

        // Define base titles (without page numbers)
        String oldMainGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("main_gui_title").split("\\(")[0]).trim();
        String newMainGuiActualTitlePrefix = guiManager.getNewMainAuctionGuiTitlePrefix(); // Corrected call
        String createGuiTitle = messageManager.stripColors(messageManager.getRaw("auction_create_gui_title"));
        String infoGuiTitle = messageManager.stripColors(messageManager.getRaw("auction_info_gui_title"));
        String myAuctionsBaseTitle = messageManager.stripColors(messageManager.getRaw("my_auctions_gui_title").split("\\(")[0]).trim();
        String historyBaseTitle = messageManager.stripColors(messageManager.getRaw("auction_history_gui_title").split("\\(")[0]).trim();

        boolean isNewMainGui = inventoryTitle.startsWith(newMainGuiActualTitlePrefix);
        boolean isCreateGui = inventoryTitle.equals(createGuiTitle);
        boolean isInfoGui = inventoryTitle.equals(infoGuiTitle);
        boolean isMyAuctionsGui = inventoryTitle.startsWith(myAuctionsBaseTitle);
        boolean isHistoryGui = inventoryTitle.startsWith(historyBaseTitle);
        boolean isDeprecatedMainGui = inventoryTitle.startsWith(oldMainGuiBaseTitle) && !isNewMainGui;

        boolean isPluginGui = isNewMainGui || isCreateGui || isInfoGui || isMyAuctionsGui || isHistoryGui || isDeprecatedMainGui;

        if (!isPluginGui) {
            return;
        }

        if (isDeprecatedMainGui) {
            event.setCancelled(true);
            player.closeInventory();
            guiManager.openNewMainAuctionGui(player, 0);
            // messageManager.sendMessage(player, "old_gui_redirecting_notice"); // Add this key to messages.yml if needed
            return;
        }

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

        if (isNewMainGui) {
            int slot = event.getSlot();
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());

            if (slot == 49 && clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_close_gui")))) {
                player.closeInventory();
            } else if (slot == 48 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                if (currentPage > 0) {
                    guiManager.openNewMainAuctionGui(player, currentPage - 1);
                }
            } else if (slot == 50 && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                guiManager.openNewMainAuctionGui(player, currentPage + 1);
            } else if (slot < 45 && clickedItem != null && clickedItem.getType() != Material.AIR) {
                 List<String> lore = clickedItem.getItemMeta().getLore();
                 if (lore != null) {
                    String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith(messageManager.stripColors(messageManager.getRaw("lore_auction_id").split(":")[0] + ":"))).findFirst().orElse(null);
                    if (idStringLore != null) {
                        try {
                            int auctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1).trim()));
                            AuctionItem auction = auctionManager.getAuction(auctionId);
                            if (auction == null) {
                               try { auction = plugin.getDatabaseManager().getAuction(auctionId); }
                               catch (Exception dbExc) {plugin.getLogger().warning("Failed to get auction " + auctionId + " from DB: " + dbExc.getMessage());}
                            }

                            if (auction != null) {
                                if (event.isLeftClick()) {
                                    // Placeholder for bidding action or opening a bid confirmation GUI
                                    messageManager.sendMessage(player, "debug_bid_action", "%id%", String.valueOf(auction.getId()));
                                    // Potentially: auctionManager.handleBidAttempt(player, auction);
                                } else if (event.isRightClick()) {
                                    guiManager.openAuctionInfoGui(player, auction);
                                }
                            } else {
                                messageManager.sendMessage(player, "invalid_auction_id", "%id%", String.valueOf(auctionId));
                            }
                        } catch (Exception e) {
                            messageManager.sendMessage(player, "internal_error");
                            plugin.getLogger().severe("Error parsing auction ID from new main GUI lore: " + idStringLore + " for player " + player.getName() + " Error: " + e.getMessage());
                        }
                    }
                 }
            }
        } else if (isCreateGui) {
            if (event.getSlot() == 13) {
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
            if(clickedItem != null && clickedItem.getType() != Material.AIR) {
                 handleCreateAuctionGuiButtonClick(player, clickedItemName);
            }
        } else if (isInfoGui && clickedItem != null) {
            ItemStack auctionDisplayItem = event.getView().getTopInventory().getItem(4);
            int auctionId = -1;
            if (auctionDisplayItem != null && auctionDisplayItem.hasItemMeta() && auctionDisplayItem.getItemMeta().hasLore()) {
                 List<String> lore = auctionDisplayItem.getItemMeta().getLore();
                 String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith(messageManager.stripColors(messageManager.getRaw("lore_auction_id").split(":")[0] + ":"))).findFirst().orElse(null);
                 if (idStringLore != null) {
                    try {
                        auctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1).trim()));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Could not parse auction ID from info GUI item: " + idStringLore);
                    }
                 }
            }
            handleAuctionInfoGuiButtonClick(player, clickedItemName, auctionId);
        } else if (isMyAuctionsGui && clickedItem != null) {
            int currentPage = guiManager.getPlayerCurrentPage(player.getUniqueId());
             if (event.getSlot() < 45 && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
                List<String> lore = clickedItem.getItemMeta().getLore();
                String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith(messageManager.stripColors(messageManager.getRaw("lore_auction_id").split(":")[0] + ":"))).findFirst().orElse(null);
                if (idStringLore != null) {
                    try {
                        int auctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1).trim()));
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
            } else {
                handleMyAuctionsGuiButtonClick(player, clickedItemName, currentPage);
            }
        } else if (isHistoryGui && clickedItem != null) {
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
        // This method is now effectively for the DEPRECATED main GUI.
        // It will be removed or updated once the new GUI is fully adopted for all actions.
        // For now, ensure it doesn't interfere or simply log if called.
         plugin.getLogger().info("Deprecated main GUI button click: " + clickedItemName + " by " + player.getName());
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
                guiManager.openNewMainAuctionGui(player, 0);
            } else {
                guiManager.openCreateAuctionGui(player);
            }
        }
    }

    private void handleAuctionInfoGuiButtonClick(Player player, String clickedItemName, int auctionId) {
        if (auctionId == -1 && !clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_auctions")))) {
            messageManager.sendMessage(player, "internal_error_no_auction_id_gui");
            guiManager.openNewMainAuctionGui(player, guiManager.getPlayerCurrentPage(player.getUniqueId()));
            return;
        }

        if (clickedItemName.equals(messageManager.stripColors(messageManager.getRaw("button_back_to_auctions")))) {
            guiManager.openNewMainAuctionGui(player, guiManager.getPlayerCurrentPage(player.getUniqueId()));
            return;
        }

        AuctionItem auctionItem = auctionManager.getAuction(auctionId);
        if (auctionItem == null) {
            try { auctionItem = plugin.getDatabaseManager().getAuction(auctionId); }
            catch (Exception e) { /* Already logged by DB manager if error */ }
        }

        if (auctionItem == null) {
            messageManager.sendMessage(player, "auction_ended_no_longer_exists");
            guiManager.openNewMainAuctionGui(player, guiManager.getPlayerCurrentPage(player.getUniqueId()));
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
                 guiManager.openNewMainAuctionGui(player, 0);
            } else {
                AuctionItem potentiallyUpdatedAuction = auctionManager.getAuction(auctionId);
                if(potentiallyUpdatedAuction != null && potentiallyUpdatedAuction.getStatus() == com.aetherauctions.auction.AuctionStatus.ACTIVE) {
                    guiManager.openAuctionInfoGui(player, potentiallyUpdatedAuction);
                } else {
                    guiManager.openNewMainAuctionGui(player, 0);
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
             guiManager.openNewMainAuctionGui(player, 0);
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
             guiManager.openNewMainAuctionGui(player, 0);
        }
    }
}
