package com.aetherauctions.gui.rework;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus; // Import AuctionStatus
import com.aetherauctions.config.MessageManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType; // Import ClickType
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor; // For ChatColor.stripColor

import java.util.HashMap; // For maps
import java.util.List;    // For List
import java.util.Map;     // For Map
import java.util.UUID;    // For UUID

public class NewGUIInventoryListener implements Listener {

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final MessageManager messageManager;

    // Maps to manage state for static GUIs
    private final Map<UUID, Integer> playerReturnPageMap = new HashMap<>();
    private final Map<UUID, Integer> playerPendingBidAuctionId = new HashMap<>();


    public NewGUIInventoryListener(AetherAuctions plugin) {
        this.plugin = plugin;
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
        String inventoryTitle = event.getView().getTitle(); // Get title for static GUI check

        if (topInventory == null) {
            return;
        }

        InventoryHolder holder = topInventory.getHolder();

        if (holder instanceof NewMainAuctionGUI) {
            event.setCancelled(true);
            NewMainAuctionGUI mainGui = (NewMainAuctionGUI) holder;
            ItemStack clickedItemRaw = event.getCurrentItem();
            int slot = event.getRawSlot();

            plugin.getLogger().info("[ReworkListener] Detected click in NewMainAuctionGUI. Slot: " + slot + ", ClickType: " + event.getClick().name());

            if (clickedItemRaw == null || clickedItemRaw.getType() == Material.AIR) {
                plugin.getLogger().info("[ReworkListener] Clicked item is null or AIR in NewMainAuctionGUI.");
                return;
            }

            if (slot >= NewMainAuctionGUI.AUCTION_ITEMS_START_SLOT && slot <= NewMainAuctionGUI.AUCTION_ITEMS_END_SLOT) {
                plugin.getLogger().info("[ReworkListener] Slot " + slot + " is an auction item slot in NewMainAuctionGUI.");

                if (event.isRightClick()) { // Preferred way to check for right-click
                    plugin.getLogger().info("[ReworkListener] Right-click detected on slot " + slot + ".");
                    AuctionItem auctionItem = mainGui.getAuctionItemAtSlot(slot);

                    if (auctionItem != null) {
                        plugin.getLogger().info("[ReworkListener] AuctionItem found for slot " + slot + ". ID: " + auctionItem.getId() + ", Status: " + auctionItem.getStatus());
                        if (auctionItem.getStatus() == AuctionStatus.ACTIVE) {
                            plugin.getLogger().info("[DEBUG] Click derecho en NewMainAuctionGUI - intentando abrir detalles para subasta ID " + auctionItem.getId());
                            playerReturnPageMap.put(player.getUniqueId(), mainGui.getCurrentPage()); // Store return page
                            com.aetherauctions.gui.GUIManager.openAuctionInfoGui(player, auctionItem, mainGui.getCurrentPage());
                        } else {
                            plugin.getLogger().info("[ReworkListener] Auction item " + auctionItem.getId() + " is not ACTIVE. Details not opened.");
                            player.sendMessage(ChatColor.RED + "Esta subasta ya no está activa."); // Or use MessageManager
                        }
                    } else {
                        plugin.getLogger().warning("[ReworkListener] Right-click on auction slot " + slot + " but getAuctionItemAtSlot returned NULL. No action taken.");
                    }
                } else {
                    plugin.getLogger().info("[ReworkListener] Click on auction slot " + slot + " was not a right-click. Type: " + event.getClick().name() + ". No action taken for details GUI.");
                }
            }
            else if (slot == NewMainAuctionGUI.CLOSE_BUTTON_SLOT) {
                plugin.getLogger().info("[ReworkListener] Close button clicked in NewMainAuctionGUI.");
                player.closeInventory();
            } else if (slot == NewMainAuctionGUI.PREVIOUS_PAGE_BUTTON_SLOT) {
                 if(clickedItemRaw.getType() == Material.ARROW) {
                    plugin.getLogger().info("[ReworkListener] Previous page button clicked in NewMainAuctionGUI.");
                     if (mainGui.getCurrentPage() > 0) {
                        new NewMainAuctionGUI(plugin, player, mainGui.getCurrentPage() - 1).open();
                     } else {
                        plugin.getLogger().info("[ReworkListener] Previous page button clicked, but already on first page.");
                     }
                 } else {
                    plugin.getLogger().info("[ReworkListener] Click on previous page slot in NewMainAuctionGUI, but item is not an arrow: " + clickedItemRaw.getType());
                 }
            } else if (slot == NewMainAuctionGUI.NEXT_PAGE_BUTTON_SLOT) {
                if(clickedItemRaw.getType() == Material.ARROW) {
                    plugin.getLogger().info("[ReworkListener] Next page button clicked in NewMainAuctionGUI.");
                    if (mainGui.getCurrentPage() < mainGui.getTotalPages() - 1) {
                        new NewMainAuctionGUI(plugin, player, mainGui.getCurrentPage() + 1).open();
                    } else {
                         plugin.getLogger().info("[ReworkListener] Next page button clicked, but already on last page.");
                    }
                } else {
                    plugin.getLogger().info("[ReworkListener] Click on next page slot in NewMainAuctionGUI, but item is not an arrow: " + clickedItemRaw.getType());
                }
            } else {
                plugin.getLogger().info("[ReworkListener] Click on unhandled slot in NewMainAuctionGUI: " + slot);
            }
        } else {
            String expectedDetailsGuiTitle = messageManager.getMessage("auction_details_gui_title");
            if (inventoryTitle.equals(expectedDetailsGuiTitle)) {
                event.setCancelled(true);
                ItemStack clickedItemRaw = event.getCurrentItem();
                int slot = event.getRawSlot();

                plugin.getLogger().info("[ReworkListener] Detected click in AuctionDetailsGUI (title match). Slot: " + slot + ", ClickType: " + event.getClick().name());

                if (clickedItemRaw == null || clickedItemRaw.getType() == Material.AIR) {
                    plugin.getLogger().info("[ReworkListener] Clicked item in AuctionDetailsGUI is null or AIR.");
                    return;
                }

                ItemStack centralItem = topInventory.getItem(com.aetherauctions.gui.AuctionDetailsGUI.ITEM_DISPLAY_SLOT);
                AuctionItem auctionItem = null;
                int auctionId = -1;

                if (centralItem != null && centralItem.hasItemMeta() && centralItem.getItemMeta().hasLore()) {
                    auctionId = parseIdFromLore(centralItem.getItemMeta().getLore());
                    if (auctionId != -1) {
                        auctionItem = auctionManager.getAuction(auctionId);
                    }
                }

                if (auctionItem == null) {
                    plugin.getLogger().severe("[ReworkListener] Could not retrieve AuctionItem from lore in AuctionDetailsGUI for player " + player.getName() + ". Auction ID parsed: " + auctionId);
                    player.sendMessage(ChatColor.RED + "Error: No se pudo procesar la acción de la subasta.");
                    player.closeInventory();
                    return;
                }

                if (auctionItem.getStatus() != AuctionStatus.ACTIVE &&
                    slot != com.aetherauctions.gui.AuctionDetailsGUI.BACK_BUTTON_SLOT) { // Allow back button even if auction ended
                    plugin.getLogger().info("[ReworkListener] Click in AuctionDetailsGUI for auction " + auctionItem.getId() + " but it's no longer active. Status: " + auctionItem.getStatus());
                    messageManager.sendMessage(player, "auction_ended_info_closed");
                    player.closeInventory(); // Close current
                    // Attempt to open main GUI to the stored return page
                    int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
                    new NewMainAuctionGUI(plugin, player, returnPage).open();
                    return;
                }

                if (slot == com.aetherauctions.gui.AuctionDetailsGUI.BACK_BUTTON_SLOT) {
                    int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
                    plugin.getLogger().info("[ReworkListener] Back button clicked in AuctionDetailsGUI. Returning to page " + returnPage);
                    new NewMainAuctionGUI(plugin, player, returnPage).open();
                } else if (slot == com.aetherauctions.gui.AuctionDetailsGUI.BID_BUTTON_SLOT) {
                    if (clickedItemRaw.getType() == Material.EMERALD) { // Material from AuctionDetailsGUI
                        plugin.getLogger().info("[ReworkListener] Bid button clicked in AuctionDetailsGUI for auction ID: " + auctionItem.getId());
                        player.closeInventory();
                        setPlayerPendingBidState(player.getUniqueId(), auctionItem.getId());
                        messageManager.sendMessage(player, "new_gui_chat_prompt_bid_amount");
                    }
                } else if (slot == com.aetherauctions.gui.AuctionDetailsGUI.BUY_NOW_BUTTON_SLOT) {
                    if (clickedItemRaw.getType() == Material.GOLD_INGOT) { // Material from AuctionDetailsGUI
                        plugin.getLogger().info("[ReworkListener] Buy Now button clicked in AuctionDetailsGUI for auction ID: " + auctionItem.getId());
                        if (auctionItem.getBuyNowPrice() > 0 && plugin.getConfigManager().isBuyNowAllowed() && auctionItem.getStatus() == AuctionStatus.ACTIVE) {
                            if (auctionManager.buyNow(player, auctionItem.getId())) {
                                player.closeInventory();
                            }
                        } else {
                             plugin.getLogger().warning("[ReworkListener] Buy Now attempt on non-buyable item in details GUI. Auction ID: " + auctionItem.getId());
                             messageManager.sendMessage(player, "error_auction_not_buyable_details_gui");
                        }
                    }
                } else if (slot == com.aetherauctions.gui.AuctionDetailsGUI.ITEM_DISPLAY_SLOT) {
                    plugin.getLogger().info("[ReworkListener] Click on central display item in AuctionDetailsGUI. No action.");
                } else {
                    plugin.getLogger().info("[ReworkListener] Click on unhandled slot in AuctionDetailsGUI: " + slot);
                }
            }
        }
    }

    private int parseIdFromLore(List<String> lore) {
        if (lore == null) return -1;
        // Assuming "auction_lore_id_prefix" resolves to something like "ID:" (potentially colored)
        // And "details_auction_id" is something like "&7ID: &b%id%"
        // We need a reliable way to get the raw prefix string.
        // For now, let's assume the ID line from auction_lore list is used: "&7ID: &f%id%"
        // So the prefix to strip is what comes before the ID number.

        String idLineTemplate = plugin.getMessageManager().getRaw("details_auction_id"); // Example: "&7ID: &b%id%"
        if (idLineTemplate == null || !idLineTemplate.contains("%id%")) {
             plugin.getLogger().warning("[ReworkListener] Lore parsing: details_auction_id key is missing or doesn't contain %id%. Cannot parse ID from lore.");
             return -1;
        }
        String idPrefix = ChatColor.stripColor(idLineTemplate.substring(0, idLineTemplate.indexOf("%id%")));

        for (String line : lore) {
            String strippedLine = ChatColor.stripColor(line);
            if (strippedLine.startsWith(idPrefix)) {
                try {
                    return Integer.parseInt(strippedLine.substring(idPrefix.length()).trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().severe("[ReworkListener] Could not parse auction ID from lore line: '" + strippedLine + "'. Error: " + e.getMessage());
                    return -1;
                }
            }
        }
        plugin.getLogger().warning("[ReworkListener] Could not find auction ID in item lore using prefix: '" + idPrefix + "'");
        return -1;
    }

    // Methods to manage pending bid state (for PlayerChatListener to access)
    public void setPlayerPendingBidState(UUID playerUUID, int auctionId) {
        playerPendingBidAuctionId.put(playerUUID, auctionId);
    }

    public Integer getAndRemovePlayerPendingBidAuctionId(UUID playerUUID) {
        return playerPendingBidAuctionId.remove(playerUUID);
    }

    public boolean isPlayerPendingBid(UUID playerUUID) {
        return playerPendingBidAuctionId.containsKey(playerUUID);
    }
}
