package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.model.Auction;
import com.aetherauctions.gui.AuctionDetailsGUI; // Puede que se necesite si MyActiveAuctionsGUI lo abre
import com.aetherauctions.gui.GUIManager;
import com.aetherauctions.gui.MainAuctionGUI;
import com.aetherauctions.gui.ClaimRewardsGUI;
import com.aetherauctions.gui.MyActiveAuctionsGUI;
import com.aetherauctions.gui.PlayerHistoryGUI;
import com.aetherauctions.gui.ManageAuctionContextGUI;
import com.aetherauctions.gui.AdminHistoryGUI;
import com.aetherauctions.gui.PrepareMysteryLotGUI; // Importar la nueva GUI
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
        String adminHistoryGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("admin_history_gui_title", "%player_name%", ""));
        String manageAuctionContextGuiTitlePrefix = ChatColor.stripColor(msgManager.getRawMessage("manage_auction_gui_title").split("%id%")[0]);
        String prepareMysteryGuiTitle = ChatColor.stripColor(msgManager.getRawMessage("prepare_mystery_gui_title", "%player%", player.getName()));
        // ClaimRewardsGUI tiene su propio Listener.

        PrepareMysteryLotGUI openPrepareGui = plugin.getOpenGUIManager().getOpenPrepareMysteryLotGUI(player);

        if (openPrepareGui != null && topInventory.equals(openPrepareGui.getInventory())) {
            handlePrepareMysteryLotGUIClick(event, player, openPrepareGui);
        }
        else if (inventoryTitle.startsWith(mainGuiTitlePrefix)) { // --- MainAuctionGUI ---
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();

            int currentPage = getCurrentPageFromTitle(inventoryTitle);
            String guiKey = "main_auction_house"; // Clave para MainAuctionGUI en config

            // Usar cfgManager.getButtonSlot y cfgManager.getButtonMaterial
            if (slot == cfgManager.getButtonSlot(guiKey, "close", 49) &&
                clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "close", "BARRIER")) {
                plugin.getSoundManager().playSound(player, "close_gui");
                player.closeInventory();
            } else if (slot == cfgManager.getButtonSlot(guiKey, "previous_page", 45) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "previous_page", "ARROW")) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MainAuctionGUI.open(player, currentPage - 1);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "next_page", 53) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "next_page", "ARROW")) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MainAuctionGUI.open(player, currentPage + 1);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "claim_rewards", 52) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "claim_rewards", "CHEST")) {
                plugin.getSoundManager().playSound(player, "open_gui");
                new ClaimRewardsGUI(plugin).open(player);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "my_auctions", 47) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "my_auctions", "WRITABLE_BOOK")) {
                if (cfgManager.isMyAuctionsGuiIntegrationEnabled()) { // Usa el nuevo getter
                    plugin.getSoundManager().playSound(player, "open_gui");
                    MyActiveAuctionsGUI.open(player, 0);
                }
            } else if (slot == cfgManager.getButtonSlot(guiKey, "player_history", 51) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "player_history", "CLOCK")) {
                if (cfgManager.isHistoryEnabled()) {
                    plugin.getSoundManager().playSound(player, "open_gui");
                    PlayerHistoryGUI.open(player, 0);
                }
            } else if (slot >= 0 && slot < cfgManager.getGuiItemsPerPage()) { // AUCTION_ITEMS_START_SLOT es 0
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
            String guiKey = "my_active_auctions_gui";

            if (slot == cfgManager.getButtonSlot(guiKey, "back_to_main_auctions", 49) &&
                clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "back_to_main_auctions", "NETHER_STAR")) {
                plugin.getSoundManager().playSound(player, "close_gui");
                MainAuctionGUI.open(player, 0);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "previous_page", 45) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "previous_page", "ARROW")) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MyActiveAuctionsGUI.open(player, currentPage - 1);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "next_page", 53) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "next_page", "ARROW")) {
                plugin.getSoundManager().playSound(player, "page_turn");
                MyActiveAuctionsGUI.open(player, currentPage + 1);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "player_history", 51) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "player_history", "CLOCK")) {
                 if (cfgManager.isHistoryEnabled()) {
                    plugin.getSoundManager().playSound(player, "open_gui");
                    PlayerHistoryGUI.open(player,0);
                 }
            } else if (slot < cfgManager.getGuiItemsPerPage()) {
                String auctionIdString = getIdStringFromLore(clickedItem.getItemMeta().getLore(), "my_auctions_gui_lore_id"); // Clave de message
                if (auctionIdString != null) {
                    Auction auction = auctionManager.getAuctionById(UUID.fromString(auctionIdString));
                    if (auction != null && auction.getSellerUUID().equals(player.getUniqueId()) && auction.getStatus() == AuctionStatus.ACTIVE) {
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
            String guiKey = "player_history_gui";

            if (slot == cfgManager.getButtonSlot(guiKey, "back_to_main_auctions", 49) &&
                clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "back_to_main_auctions", "NETHER_STAR")) {
                plugin.getSoundManager().playSound(player, "close_gui");
                MainAuctionGUI.open(player, 0);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "previous_page", 45) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "previous_page", "ARROW")) {
                plugin.getSoundManager().playSound(player, "page_turn");
                PlayerHistoryGUI.open(player, currentPage - 1);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "next_page", 53) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "next_page", "ARROW")) {
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
            // La paginación de AdminHistoryGUI requiere parámetros adicionales (targetPlayerName, startDate, endDate)
            // que no están fácilmente disponibles aquí solo con el título.
            // Por lo tanto, los botones de paginación en AdminHistoryGUI.java deberían reabrir la GUI
            // con los parámetros correctos, en lugar de que este listener los maneje directamente.
            String guiKey = "admin_history_gui";

            plugin.getSoundManager().playSound(player, "click");
            if (slot == cfgManager.getButtonSlot(guiKey, "close", 49) &&
                clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "close", "BARRIER")) {
                plugin.getSoundManager().playSound(player, "close_gui");
                player.closeInventory();
            }
            // Los botones Previous/Next Page para AdminHistoryGUI deberían ser manejados por AdminHistoryGUI.open()
            // si se implementan, ya que necesitan el contexto de targetPlayerName, startDate, endDate.
        }
         else if (inventoryTitle.startsWith(manageAuctionContextGuiTitlePrefix)) { // --- ManageAuctionContextGUI ---
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            int slot = event.getSlot();
            plugin.getSoundManager().playSound(player, "click");
            String guiKey = "manage_auction_context_gui";

            String auctionIdFromTitle = ""; // Esta extracción de ID del título es frágil.
            if(inventoryTitle.length() > manageAuctionContextGuiTitlePrefix.length()) {
                auctionIdFromTitle = inventoryTitle.substring(manageAuctionContextGuiTitlePrefix.length()).trim();
            }
            Auction auctionToManage = auctionManager.getAuctionByIdFuzzy(auctionIdFromTitle);

            if (auctionToManage == null) {
                player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
                player.closeInventory();
                MyActiveAuctionsGUI.open(player, 0);
                return;
            }

            if (slot == cfgManager.getButtonSlot(guiKey, "back_to_my_auctions", 22) &&
                clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "back_to_my_auctions", "ARROW")) {
                MyActiveAuctionsGUI.open(player, 0);
            } else if (slot == cfgManager.getButtonSlot(guiKey, "view_auction_details", 15) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "view_auction_details", "BOOK")) {
                GUIManager.openAuctionInfoGui(player, auctionToManage, playerReturnPageMap.getOrDefault(player.getUniqueId(),0));
            } else if (slot == cfgManager.getButtonSlot(guiKey, "cancel_this_auction", 11) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "cancel_this_auction", "RED_WOOL")) {
                if (auctionManager.cancelAuction(player, UUID.fromString(auctionToManage.getId()))) {
                    MyActiveAuctionsGUI.open(player, 0);
                } else {
                    player.closeInventory();
                }
            }
        }
        else if (inventoryTitle.equals(detailsGuiTitle)) { // --- AuctionDetailsGUI ---
            handleAuctionDetailsGUIClick(event, player, topInventory, detailsGuiTitle);
        }
    }

    private void handleAuctionItemClick(Player player, ItemStack clickedItem, int currentPage, InventoryClickEvent event) {
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.LEFT) {
            String auctionUUIDString = getIdStringFromLore(clickedItem.getItemMeta().getLore(), "main_gui_lore_id"); // Clave de message
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
        String guiKey = "auction_details_gui";

        ItemStack centralItem = topInventory.getItem(cfgManager.getButtonSlot(guiKey, "item_display_slot", 22));
        if (centralItem == null || !centralItem.hasItemMeta() || !centralItem.getItemMeta().hasLore()) {
             plugin.getLogger().severe("Item central o su lore no encontrado en AuctionDetailsGUI.");
             player.closeInventory();
             return;
        }

        String auctionUUIDString = getIdStringFromLore(centralItem.getItemMeta().getLore(), "main_gui_lore_id"); // Reutilizar clave de ID del lore
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

        if (slot == cfgManager.getButtonSlot(guiKey, "back_to_listing", 49) &&
            clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "back_to_listing", "BARRIER")) {
            int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
            MainAuctionGUI.open(player, returnPage);
            return;
        }

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            player.sendMessage(msgManager.getPrefixedMessage("auction_bid_error_not_active"));
            int returnPage = playerReturnPageMap.getOrDefault(player.getUniqueId(), 0);
            MainAuctionGUI.open(player, returnPage);
            return;
        }

        if (slot == cfgManager.getButtonSlot(guiKey, "bid", 38) &&
            clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "bid", "EMERALD")) {
            player.closeInventory();
            playerPendingBidAuctionId.put(player.getUniqueId(), UUID.fromString(auction.getId()));
            player.sendMessage(msgManager.getMessage("chat_prompt_enter_bid_amount", "%id%", auction.getId().toString()));
        } else if (slot == cfgManager.getButtonSlot(guiKey, "buy_now", 40) &&
                   clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "buy_now", "GOLD_INGOT")) {
            if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) {
                if (auctionManager.buyNow(player, UUID.fromString(auction.getId()))) {
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

            // Manejar cierre de PrepareMysteryLotGUI
            com.aetherauctions.gui.PrepareMysteryLotGUI openPrepareGui = plugin.getOpenGUIManager().getOpenPrepareMysteryLotGUI(player);
            if (openPrepareGui != null && event.getInventory().equals(openPrepareGui.getInventory()) && !openPrepareGui.isConfirmed()) {
                List<ItemStack> itemsToReturn = openPrepareGui.getLotItems(); // Obtiene items de la GUI
                for (ItemStack item : itemsToReturn) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        msgManager.sendMessage(player, "prepare_mystery_gui_items_dropped_on_close");
                    }
                }
                if (!itemsToReturn.isEmpty()) {
                    msgManager.sendMessage(player, "prepare_mystery_gui_items_returned_on_close");
                }
                // El OpenGUIManager se encargará de removerlo de sus mapas en su propio onInventoryCloseEvent -> handleInventoryClose
            }
        }
    }

    private void handlePrepareMysteryLotGUIClick(InventoryClickEvent event, Player player, PrepareMysteryLotGUI prepareGui) {
        event.setCancelled(true);
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack currentItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedInventory == null) return;

        if (clickedInventory.equals(prepareGui.getInventory())) { // Clic en la GUI de preparación
            plugin.getSoundManager().playSound(player, "click");
            String guiKey = "prepare_mystery_lot_gui"; // Clave para config

            if (slot < prepareGui.getMaxLotItems()) { // Clic en el área de ítems, usa el getter
                if (currentItem != null && currentItem.getType() != Material.AIR) {
                    player.getInventory().addItem(currentItem.clone());
                    // La siguiente línea es crucial para la lógica de PrepareMysteryLotGUI.removeItemFromLot
                    // que actualiza la lista interna 'lotItems'.
                    prepareGui.removeItemFromLot(slot); // 'slot' es el índice para la lista interna
                    // removeItemFromLot ya llama a renderGUI()
                }
            } else if (slot == cfgManager.getButtonSlot(guiKey, "cancel_preparation", 47) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "cancel_preparation", "BARRIER")) {
                plugin.getSoundManager().playSound(player, "close_gui");
                List<ItemStack> itemsToReturn = prepareGui.getLotItems(); // Esto obtiene de los slots de la GUI
                for (ItemStack item : itemsToReturn) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                         msgManager.sendMessage(player, "prepare_mystery_gui_items_dropped_on_cancel");
                    }
                }
                if(!itemsToReturn.isEmpty()) msgManager.sendMessage(player, "prepare_mystery_gui_cancelled_items_returned");
                else msgManager.sendMessage(player, "prepare_mystery_gui_cancelled_no_items");
                player.closeInventory();
            } else if (slot == cfgManager.getButtonSlot(guiKey, "confirm_and_create", 51) &&
                       clickedItem.getType() == cfgManager.getButtonMaterial(guiKey, "confirm_and_create", "EMERALD_BLOCK")) {
                List<ItemStack> lotItems = prepareGui.getLotItems(); // Esto obtiene de los slots de la GUI
                if (lotItems.isEmpty()) {
                    msgManager.sendMessage(player, "prepare_mystery_gui_error_no_items_on_confirm");
                    plugin.getSoundManager().playSound(player, "error");
                    return;
                }

                long durationSeconds = prepareGui.getDurationHours() * 3600;
                boolean success = plugin.getAuctionManager().createMysteryAuction( // Declarar y asignar 'success'
                        player,
                        lotItems,
                        prepareGui.getStartPrice(),
                        prepareGui.getBuyNowPrice(),
                        durationSeconds,
                        prepareGui.getDescription()
                );

                if (success) {
                    // El AuctionManager.createMysteryAuction ya envía mensaje de éxito/error y sonido
                    prepareGui.setConfirmed(true); // Marcar como confirmada SOLO si la creación fue exitosa
                    player.closeInventory(); // OpenGUIManager se encargará de la limpieza
                } else {
                    // Si falló, NO se marca como confirmada.
                    // El mensaje de error ya fue enviado por AuctionManager.
                    // Los ítems se devolverán automáticamente por onInventoryClose porque isConfirmed es false.
                    // Opcionalmente, se puede cerrar la GUI aquí también si no se cierra automáticamente por el flujo de error.
                    // Si AuctionManager no cierra la GUI en error, ciérrala aquí.
                    // Por ahora, asumimos que el jugador puede querer reintentar o la GUI se cierra.
                    // Si no se cierra, el onInventoryClose al final devolverá los ítems.
                    // Si se cierra aquí, onInventoryClose se activará.
                    plugin.getSoundManager().playSound(player, "error"); // Sonido de error adicional desde la GUI
                    // player.closeInventory(); // Descomentar si se quiere forzar cierre inmediato y devolución por onInventoryClose
                }
            }
        } else if (clickedInventory.equals(player.getInventory())) { // Clic en el inventario del jugador
            if (currentItem != null && currentItem.getType() != Material.AIR) {
                // Si es doble clic, y sigue dando problemas, podríamos intentar
                // tratarlo como un clic normal aquí o simplemente confiar en la cancelación.
                // Por ahora, nos enfocaremos en el orden de operaciones.

                if (prepareGui.getLotItems().size() < PrepareMysteryLotGUI.MAX_LOT_ITEMS) {
                    ItemStack itemToAdd = currentItem.clone(); // Clonamos el ítem para añadirlo a la GUI

                    // 1. Eliminar el ítem del inventario del jugador PRIMERO
                    event.setCurrentItem(null); // o clickedInventory.setItem(slot, null);

                    // 2. Añadir el clon a la GUI de preparación
                    prepareGui.addItemToLot(itemToAdd); // addItemToLot clona de nuevo internamente y llama a renderGUI()

                    // 3. Opcional: Forzar una actualización del inventario del jugador
                    // player.updateInventory();

                    plugin.getSoundManager().playSound(player, "click");
                } else {
                    msgManager.sendMessage(player, "prepare_mystery_gui_error_lot_full");
                    plugin.getSoundManager().playSound(player, "error");
                }
            }
        }
    }
}
