package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.model.Auction;
import com.aetherauctions.gui.AuctionDetailsGUI;
import com.aetherauctions.gui.GUIManager;
import com.aetherauctions.gui.MainAuctionGUI;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.config.ConfigManager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
// import org.bukkit.event.inventory.InventoryAction; // Not used in provided code
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InventoryClickListener implements Listener {
    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final MessageManager msgManager;
    private final ConfigManager cfgManager;

    // Maps to manage state between static GUIs
    private final Map<UUID, Integer> playerReturnPageMap = new HashMap<>();
    private final Map<UUID, UUID> playerPendingBidAuctionId = new HashMap<>();

    public InventoryClickListener(AetherAuctions plugin) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager();
        this.msgManager = plugin.getMessageManager();
        this.cfgManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();

        if (topInventory == null) return;

        String inventoryTitle = ChatColor.stripColor(event.getView().getTitle());
        String mainGuiTitlePrefix = ChatColor.stripColor(msgManager.getMessage("main_gui_title_prefix"));
        String detailsGuiTitle = ChatColor.stripColor(msgManager.getMessage("auction_details_gui_title"));

        if (inventoryTitle.startsWith(mainGuiTitlePrefix)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();

            int currentPage = 0;
            if (inventoryTitle.contains("(Pág. ")) {
                try {
                    String pageStr = inventoryTitle.substring(inventoryTitle.indexOf("(Pág. ") + 6, inventoryTitle.indexOf("/"));
                    currentPage = Integer.parseInt(pageStr.trim()) - 1;
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not parse current page from MainAuctionGUI title: " + inventoryTitle);
                }
            }

            if (slot == MainAuctionGUI.CLOSE_GUI_SLOT) {
                player.closeInventory();
            } else if (slot == MainAuctionGUI.PREVIOUS_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                GUIManager.openMainAuctionGUI(player, currentPage - 1);
            } else if (slot == MainAuctionGUI.NEXT_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                GUIManager.openMainAuctionGUI(player, currentPage + 1);
            } else if (slot >= MainAuctionGUI.AUCTION_ITEMS_START_SLOT && slot < cfgManager.getGuiItemsPerPage()) {
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.LEFT) {
                    String auctionUUIDString = getIdStringFromLore(clickedItem.getItemMeta().getLore(), "main_gui_lore_id");
                    if (auctionUUIDString != null) {
                        Auction auction = null;
                        try {
                            // Assuming ID in lore is the full UUID string, or at least the first 8 chars if getAuctionByIdFuzzy handles that
                            // For this implementation, assuming getAuctionByIdFuzzy can take short or full UUID strings.
                            auction = auctionManager.getAuctionByIdFuzzy(auctionUUIDString);
                        } catch (IllegalArgumentException e){
                            plugin.getLogger().severe("Invalid UUID string from lore in MainAuctionGUI: " + auctionUUIDString);
                        }

                        if (auction != null && auction.getStatus() == AuctionStatus.ACTIVE) {
                            playerReturnPageMap.put(player.getUniqueId(), currentPage);
                            GUIManager.openAuctionInfoGui(player, auction, currentPage);
                            plugin.getLogger().info("[DEBUG] Click en MainAuctionGUI - intentando abrir detalles para subasta ID " + auction.getId().toString().substring(0,8));
                        } else if (auction != null) {
                             player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
                        } else {
                            plugin.getLogger().warning("Auction not found from ID in lore: " + auctionUUIDString);
                            player.sendMessage(ChatColor.RED + "Error: Subasta no encontrada.");
                        }
                    } else {
                         plugin.getLogger().warning("Could not extract auction ID from lore in MainAuctionGUI, slot " + slot);
                    }
                }
            }
        }
        else if (inventoryTitle.equals(detailsGuiTitle)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();

            ItemStack centralItem = topInventory.getItem(AuctionDetailsGUI.ITEM_DISPLAY_SLOT);
            if (centralItem == null || !centralItem.hasItemMeta() || !centralItem.getItemMeta().hasLore()) {
                 plugin.getLogger().severe("Item central o su lore no encontrado en AuctionDetailsGUI.");
                 player.closeInventory();
                 return;
            }

            String auctionUUIDString = getIdStringFromLore(centralItem.getItemMeta().getLore(), "details_auction_id");

            Auction auction = null;
            if(auctionUUIDString != null){
                try {
                    auction = auctionManager.getAuctionById(UUID.fromString(auctionUUIDString));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().severe("Error parseando UUID desde el lore en AuctionDetailsGUI: " + auctionUUIDString);
                }
            }

            if (auction == null) {
                player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
                player.closeInventory();
                return;
            }

            // Allow back button even if auction status changed while GUI was open
            if (slot == AuctionDetailsGUI.BACK_BUTTON_SLOT) {
                int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
                GUIManager.openMainAuctionGUI(player, returnPage);
                return; // Return after handling back button
            }

            // For other actions, ensure auction is still active
            if (auction.getStatus() != AuctionStatus.ACTIVE) {
                player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
                player.closeInventory(); // Close and force re-open from main list
                GUIManager.openMainAuctionGUI(player, playerReturnPageMap.getOrDefault(player.getUniqueId(), 0));
                return;
            }

            if (slot == AuctionDetailsGUI.BID_BUTTON_SLOT) {
                 if (clickedItem.getType() == Material.EMERALD) {
                    player.closeInventory();
                    playerPendingBidAuctionId.put(player.getUniqueId(), auction.getId());
                    player.sendMessage(msgManager.getMessage("chat_prompt_enter_bid_amount", "%id%", auction.getId().toString().substring(0,8)));
                 }
            } else if (slot == AuctionDetailsGUI.BUY_NOW_BUTTON_SLOT) {
                if (clickedItem.getType() == Material.GOLD_INGOT) {
                    if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) { // Status already checked
                        if (auctionManager.buyNow(player, auction.getId())) {
                            player.closeInventory();
                        }
                    } else {
                         player.sendMessage(msgManager.getPrefixedMessage("auction_buy_now_error_not_available"));
                    }
                }
            }
        }
    }

    private String getIdStringFromLore(List<String> lore, String idMessageKey) {
        if (lore == null) return null;
        String idLineFormat = msgManager.getRawMessage(idMessageKey);
        if(idLineFormat == null || idLineFormat.equals(idMessageKey) || !idLineFormat.contains("%id%")) {
            plugin.getLogger().warning("La clave de mensaje para el prefijo de ID ('" + idMessageKey + "') no existe o no contiene '%id%'. Valor recibido: " + idLineFormat);
            String commonIdPrefix = ChatColor.stripColor("ID:");
            String commonIdPrefixHash = ChatColor.stripColor("ID: #");
            for (String line : lore) {
                String strippedLine = ChatColor.stripColor(line);
                if (strippedLine.startsWith(commonIdPrefix)) {
                    return strippedLine.substring(commonIdPrefix.length()).trim();
                } else if (strippedLine.startsWith(commonIdPrefixHash)) {
                     return strippedLine.substring(commonIdPrefixHash.length()).trim();
                }
            }
            plugin.getLogger().warning("No se pudo encontrar el ID en el lore usando prefijos comunes para la clave: " + idMessageKey);
            return null;
        }
        String idPrefix = ChatColor.stripColor(idLineFormat.substring(0, idLineFormat.indexOf("%id%")));

        for (String line : lore) {
            String strippedLine = ChatColor.stripColor(line);
            if (strippedLine.startsWith(idPrefix)) {
                String potentialId = strippedLine.substring(idPrefix.length()).trim();
                // Validate if it looks like a UUID or short UUID, this is a basic check
                if (potentialId.length() >= 8 && potentialId.matches("[a-fA-F0-9-]+")) {
                     return potentialId;
                }
            }
        }
        plugin.getLogger().warning("No se pudo encontrar el ID en el lore usando la clave de prefijo: " + idMessageKey + " (prefijo buscado: '" + idPrefix + "')");
        return null;
    }

    public boolean isPlayerPendingBid(UUID playerId) {
        return playerPendingBidAuctionId.containsKey(playerId);
    }
    public UUID getAndRemovePlayerPendingBidAuctionId(UUID playerId) {
        return playerPendingBidAuctionId.remove(playerId);
    }
    public void clearPlayerStatesOnQuit(UUID playerId){
        playerReturnPageMap.remove(playerId);
        playerPendingBidAuctionId.remove(playerId);
    }
}
