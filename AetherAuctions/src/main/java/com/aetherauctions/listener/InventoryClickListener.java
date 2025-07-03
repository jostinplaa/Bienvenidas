package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.model.Auction;
import com.aetherauctions.gui.AuctionDetailsGUI; // Puede que se necesite si MyActiveAuctionsGUI lo abre
import com.aetherauctions.gui.GUIManager;
import com.aetherauctions.gui.MainAuctionGUI;
import com.aetherauctions.guis.ClaimRewardsGUI;
import com.aetherauctions.guis.MyActiveAuctionsGUI;
import com.aetherauctions.guis.PlayerHistoryGUI;
import com.aetherauctions.guis.ManageAuctionContextGUI;
import com.aetherauctions.guis.AdminHistoryGUI; // Añadido
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.config.ConfigManager;

import org.bukkit.Bukkit; // Añadido
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
// import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Importar InventoryCloseEvent
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

        String inventoryTitle = ChatColor.stripColor(event.getView().getTitle()); // Usar ChatColor.stripColor para comparar sin códigos de color
        String mainGuiTitlePrefix = ChatColor.stripColor(msgManager.getRawMessage("main_gui_title_prefix"));
        String detailsGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("auction_details_gui_title"));
        String myAuctionsGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("my_auctions_gui_title", "%player_name%", player.getName()));
        String playerHistoryGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("player_history_gui_title", "%player_name%", player.getName()));
        String adminHistoryGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("admin_history_gui_title", "%player_name%", "")); // El nombre del jugador se añade dinámicamente
        String manageAuctionContextGuiTitlePrefix = ChatColor.stripColor(msgManager.getRawMessage("manage_auction_gui_title").split("%id%")[0]);
        // ClaimRewardsGUI tiene su propio Listener.

        if (inventoryTitle.startsWith(mainGuiTitlePrefix)) { // --- MainAuctionGUI ---
            event.setCancelled(true);
            // plugin.getSoundManager().playSound(player, "click"); // Reemplazar sonido directo
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();

            int currentPage = getCurrentPageFromTitle(inventoryTitle);

            if (slot == MainAuctionGUI.CLOSE_GUI_SLOT) {
                plugin.getSoundManager().playSound(player, "close_gui");
                player.closeInventory();
            } else if (slot == MainAuctionGUI.PREVIOUS_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MainAuctionGUI.open(player, currentPage - 1);
            } else if (slot == MainAuctionGUI.NEXT_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MainAuctionGUI.open(player, currentPage + 1);
            } else if (slot == MainAuctionGUI.REWARDS_BUTTON_SLOT && clickedItem.getType() == Material.CHEST) {
                plugin.getSoundManager().playSound(player, "open_gui");
                new ClaimRewardsGUI(plugin).open(player);
            } else if (slot == MainAuctionGUI.MY_AUCTIONS_SLOT && clickedItem.getType() == Material.WRITABLE_BOOK) {
                if (cfgManager.isMyAuctionsGuiEnabled()) {
                    plugin.getSoundManager().playSound(player, "open_gui");
                    MyActiveAuctionsGUI.open(player, 0);
                }
            } else if (slot == MainAuctionGUI.HISTORY_SLOT && clickedItem.getType() == Material.CLOCK) {
                if (cfgManager.isHistoryEnabled()) {
                    plugin.getSoundManager().playSound(player, "open_gui");
                    PlayerHistoryGUI.open(player, 0);
                }
            } else if (slot >= MainAuctionGUI.AUCTION_ITEMS_START_SLOT && slot < cfgManager.getGuiItemsPerPage()) {
                plugin.getSoundManager().playSound(player, "click");
                handleAuctionItemClick(player, clickedItem, currentPage, event);
            } else {
                plugin.getSoundManager().playSound(player, "click"); // Sonido genérico para otros clics en la GUI (paneles)
            }
        }
        else if (inventoryTitle.startsWith(myAuctionsGuiTitle)) { // --- MyActiveAuctionsGUI ---
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();
            int currentPage = getCurrentPageFromTitle(inventoryTitle);

            if (slot == MyActiveAuctionsGUI.CLOSE_GUI_SLOT) {
                plugin.getSoundManager().playSound(player, "close_gui"); // O un sonido de "volver"
                MainAuctionGUI.open(player, 0);
            } else if (slot == MyActiveAuctionsGUI.PREVIOUS_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MyActiveAuctionsGUI.open(player, currentPage - 1);
            } else if (slot == MyActiveAuctionsGUI.NEXT_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MyActiveAuctionsGUI.open(player, currentPage + 1);
            } else if (slot == MyActiveAuctionsGUI.HISTORY_BUTTON_SLOT && clickedItem.getType() == Material.CLOCK) {
                 if (cfgManager.isHistoryEnabled()) {
                    plugin.getSoundManager().playSound(player, "open_gui");
                    PlayerHistoryGUI.open(player,0);
                 }
            } else if (slot < cfgManager.getGuiItemsPerPage()) {
                String auctionIdString = getIdStringFromLore(clickedItem.getItemMeta().getLore(), "my_auctions_gui_lore_id");
                if (auctionIdString != null) {
                    Auction auction = auctionManager.getAuctionById(UUID.fromString(auctionIdString));
                    if (auction != null && auction.getSellerId().equals(player.getUniqueId()) && auction.getStatus() == AuctionStatus.ACTIVE) {
                        plugin.getSoundManager().playSound(player, "click"); // Sonido para abrir contexto
                        ManageAuctionContextGUI.open(player, auction);
                    } else if (auction != null) {
                         plugin.getSoundManager().playSound(player, "click");
                         GUIManager.openAuctionInfoGui(player, auction, currentPage);
                    }
                }
            } else {
                plugin.getSoundManager().playSound(player, "click");
            }
        }
        else if (inventoryTitle.startsWith(playerHistoryGuiTitle)) { // --- PlayerHistoryGUI ---
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();
            int currentPage = getCurrentPageFromTitle(inventoryTitle);

            if (slot == PlayerHistoryGUI.CLOSE_GUI_SLOT) {
                plugin.getSoundManager().playSound(player, "close_gui"); // O "volver"
                MainAuctionGUI.open(player, 0);
            } else if (slot == PlayerHistoryGUI.PREVIOUS_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                plugin.getSoundManager().playSound(player, "page_turn");
                PlayerHistoryGUI.open(player, currentPage - 1);
            } else if (slot == PlayerHistoryGUI.NEXT_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
                plugin.getSoundManager().playSound(player, "page_turn");
                PlayerHistoryGUI.open(player, currentPage + 1);
            } else {
                plugin.getSoundManager().playSound(player, "click");
            }
        }
        else if (inventoryTitle.startsWith(adminHistoryGuiTitle)) { // --- AdminHistoryGUI ---
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();
            // No se extrae targetPlayerName del título aquí, se necesitaría pasar de otra forma si se requiere para paginación
            // Por ahora, la paginación de AdminHistoryGUI se manejaría pasándole los parámetros de nuevo.
            // int currentPage = getCurrentPageFromTitle(inventoryTitle);

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (slot == AdminHistoryGUI.CLOSE_GUI_SLOT) {
                player.closeInventory();
            }
            // Faltaría lógica de paginación si AdminHistoryGUI la implementa y necesita que este listener la maneje.
            // Por ahora, se asume que si hay paginación en AdminHistoryGUI, se reabre con nuevos parámetros.
        }
         else if (inventoryTitle.startsWith(manageAuctionContextGuiTitlePrefix)) { // --- ManageAuctionContextGUI ---
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);

            // Extraer el ID de la subasta del título de la GUI de contexto
            String auctionIdFromTitle = "";
            if(inventoryTitle.length() > manageAuctionContextGuiTitlePrefix.length()) {
                auctionIdFromTitle = inventoryTitle.substring(manageAuctionContextGuiTitlePrefix.length()).trim();
            }
            Auction auctionToManage = auctionManager.getAuctionByIdFuzzy(auctionIdFromTitle); // Usar fuzzy por si el título acorta el ID

            if (auctionToManage == null) {
                player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active")); // O un error más específico
                player.closeInventory();
                MyActiveAuctionsGUI.open(player, 0); // Volver a "mis subastas"
                return;
            }

            if (slot == ManageAuctionContextGUI.BACK_BUTTON_SLOT) {
                MyActiveAuctionsGUI.open(player, 0); // Asumiendo que la página 0 es la correcta
            } else if (slot == ManageAuctionContextGUI.VIEW_DETAILS_SLOT) {
                GUIManager.openAuctionInfoGui(player, auctionToManage, playerReturnPageMap.getOrDefault(player.getUniqueId(),0));
            } else if (slot == ManageAuctionContextGUI.CANCEL_AUCTION_SLOT) {
                if (auctionManager.cancelAuction(player, auctionToManage.getId())) {
                    // Mensaje de éxito ya enviado por cancelAuction
                    MyActiveAuctionsGUI.open(player, 0); // Refrescar
                } else {
                    // Mensaje de error ya enviado por cancelAuction
                    player.closeInventory(); // Cerrar si la cancelación falla por alguna razón crítica
                }
            }
        }
        else if (inventoryTitle.equals(detailsGuiTitle)) { // --- AuctionDetailsGUI ---
            handleAuctionDetailsGUIClick(event, player, topInventory, detailsGuiTitle);
        }
    }

    private void handleAuctionItemClick(Player player, ItemStack clickedItem, int currentPage, InventoryClickEvent event) { //Añadir event
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.LEFT) {
            String auctionUUIDString = getIdStringFromLore(clickedItem.getItemMeta().getLore(), "main_gui_lore_id");
            if (auctionUUIDString != null) {
                Auction auction = null;
                try {
                    auction = auctionManager.getAuctionById(UUID.fromString(auctionUUIDString));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().severe("Invalid UUID from lore: " + auctionUUIDString + " in MainAuctionGUI. Error: " + e.getMessage());
                }

                if (auction != null && auction.getStatus() == AuctionStatus.ACTIVE) {
                    playerReturnPageMap.put(player.getUniqueId(), currentPage);
                    GUIManager.openAuctionInfoGui(player, auction, currentPage);
                } else if (auction != null) {
                    player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
                } else {
                    player.sendMessage(ChatColor.RED + "Error: Subasta no encontrada.");
                }
            } else {
                plugin.getLogger().warning("Could not extract auction ID from lore in MainAuctionGUI item.");
            }
        }
    }

    private void handleAuctionDetailsGUIClick(InventoryClickEvent event, Player player, Inventory topInventory, String detailsGuiTitle) {
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

        if (slot == AuctionDetailsGUI.BACK_BUTTON_SLOT) {
            int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
            MainAuctionGUI.open(player, returnPage); // Usar MainAuctionGUI.open
            return;
        }

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
            int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
            MainAuctionGUI.open(player, returnPage); // Usar MainAuctionGUI.open
            return;
        }

        if (slot == AuctionDetailsGUI.BID_BUTTON_SLOT && clickedItem.getType() == Material.EMERALD) {
            player.closeInventory();
            playerPendingBidAuctionId.put(player.getUniqueId(), auction.getId());
            player.sendMessage(msgManager.getMessage("chat_prompt_enter_bid_amount", "%id%", auction.getId().toString()));
        } else if (slot == AuctionDetailsGUI.BUY_NOW_BUTTON_SLOT && clickedItem.getType() == Material.GOLD_INGOT) {
            if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) {
                if (auctionManager.buyNow(player, auction.getId())) {
                    player.closeInventory();
                }
            } else {
                 player.sendMessage(msgManager.getPrefixedMessage("auction_buy_now_error_not_available"));
            }
        }
    }


    private int getCurrentPageFromTitle(String title) {
        if (title.contains("(Pág. ")) {
            try {
                String pageStrPart = title.substring(title.indexOf("(Pág. ") + 6);
                String pageStr = pageStrPart.substring(0, pageStrPart.indexOf("/")).trim();
                return Integer.parseInt(pageStr) - 1;
            } catch (Exception e) {
                plugin.getLogger().warning("Could not parse current page from GUI title: " + title + ". Defaulting to 0. Error: " + e.getMessage());
            }
        }
        return 0;
    }

    private String getIdStringFromLore(List<String> lore, String idMessageKey) {
        if (lore == null) return null;
        // Asumimos que el ID está en el formato: "ID: <uuid>" o "ID Subasta: <uuid>"
        // Y que MessageManager devuelve el prefijo correcto para idMessageKey (ej. "ID: ")
        String idPrefixStripped = ChatColor.stripColor(msgManager.getRawMessage(idMessageKey).split("%id%")[0]);

        for (String line : lore) {
            String strippedLine = ChatColor.stripColor(line);
            if (strippedLine.startsWith(idPrefixStripped)) {
                String potentialId = strippedLine.substring(idPrefixStripped.length()).trim();
                 // Validar si parece un UUID (longitud típica de 36, o 8 para un corto si se usara)
                if (potentialId.length() == 36 || potentialId.length() == 8) { // Ajustar si se usa ID corto en algún lore específico
                    return potentialId;
                }
            }
        }
        plugin.getLogger().warning("No se pudo encontrar el ID en el lore usando la clave: " + idMessageKey + " (Prefijo buscado: '" + idPrefixStripped + "')");
        return null;
    }

    public boolean isPlayerPendingBid(UUID playerId) {
        return playerPendingBidAuctionId.containsKey(playerId);
    }
    public UUID getAndRemovePlayerPendingBidAuctionId(UUID playerId) {
        return playerPendingBidAuctionId.remove(playerId);
    }
    public void clearPlayerStatesOnQuit(UUID playerId){
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            plugin.getOpenGUIManager().playerClosedGUI(player); // Notificar al OpenGUIManager
        }
        playerReturnPageMap.remove(playerId);
        playerPendingBidAuctionId.remove(playerId);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            // Notificar a OpenGUIManager que una GUI se cerró.
            // OpenGUIManager internamente verificará si es una de las que rastrea.
            plugin.getOpenGUIManager().playerClosedGUI(player);

            // También limpiar estados específicos de este listener si la GUI cerrada es relevante
            String closedInventoryTitle = ChatColor.stripColor(event.getView().getTitle());
            String detailsGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("auction_details_gui_title"));
            if (closedInventoryTitle.equals(detailsGuiTitle)) {
                playerReturnPageMap.remove(player.getUniqueId());
            }

            if (isPlayerPendingBid(player.getUniqueId())) {
                getAndRemovePlayerPendingBidAuctionId(player.getUniqueId());
                // msgManager.sendMessage(player, "chat_bid_cancelled_gui_closed"); // Opcional
            }
        }
    }
}
