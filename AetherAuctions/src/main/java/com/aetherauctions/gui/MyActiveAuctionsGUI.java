package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import com.aetherauctions.util.ItemUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor; // Importación añadida

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map; // Importación añadida
import java.util.HashMap; // Importación añadida

public class MyActiveAuctionsGUI {

    // Las constantes de slot se leerán de config.yml
    // public static final int PREVIOUS_PAGE_SLOT = 45;
    // public static final int CLOSE_GUI_SLOT = 49; // Renombrado a BACK_TO_MAIN_SLOT
    // public static final int NEXT_PAGE_SLOT = 53;
    // public static final int HISTORY_BUTTON_SLOT = 51;

    public static void open(Player player, int page) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        AuctionManager auctionManager = plugin.getAuctionManager();

        List<Auction> playerActiveAuctions = auctionManager.getPlayerActiveAuctions(player.getUniqueId());

        int itemsPerPage = cfgManager.getGuiItemsPerPage(); // gui.general_appearance.items_per_page
        int totalItems = playerActiveAuctions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = msgManager.getRawMessage("my_auctions_gui_title", "%player_name%", player.getName()) +
                       (totalPages > 1 ? " &7(Pág. " + (page + 1) + "/" + totalPages + ")" : "");
        Inventory gui = Bukkit.createInventory(player, 54, title);

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        String guiKey = "my_active_auctions_gui"; // Clave base para esta GUI en config

        if (playerActiveAuctions.isEmpty()) {
            gui.setItem(22, ItemUtil.createItemStack(cfgManager.getNoResultsItemMaterial(), // gui.general_appearance.no_results_item
                                                     msgManager.getRawMessage("my_auctions_gui_no_auctions")));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Auction auction = playerActiveAuctions.get(i);
                int guiSlot = i - startIndex;
                 // La comprobación de si guiSlot >= PREVIOUS_PAGE_SLOT ya no es necesaria aquí
                if (guiSlot >= itemsPerPage) break;

                ItemStack displayItem;
                ItemMeta meta;
                List<String> lore = new ArrayList<>();

                if (auction.isMystery()) {
                    displayItem = new ItemStack(Material.ENDER_CHEST);
                    meta = displayItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(msgManager.getRawMessage("mystery_auction_gui_item_name")); // Reutilizar clave
                        lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_description", "%description%", auction.getMysteryDescription()));
                        try {
                            int itemCount = plugin.getAuctionStorage().getMysteryAuctionContentsCount(auction.getAuctionId());
                            lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_item_count", "%count%", String.valueOf(itemCount)));
                        } catch (java.sql.SQLException e) {
                            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not get item count for mystery auction " + auction.getId() + " for MyActiveAuctionsGUI.", e);
                            lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_item_count_error"));
                        }
                    }
                } else {
                    if (auction.getItemStack() == null || auction.getItemStack().getType() == Material.AIR) {
                        displayItem = new ItemStack(Material.BARRIER);
                        meta = displayItem.getItemMeta();
                        if (meta != null) meta.setDisplayName(ChatColor.RED + msgManager.getRawMessage("error_item_not_available"));
                    } else {
                        displayItem = auction.getItemStack().clone();
                        meta = displayItem.getItemMeta();
                    }
                    if (meta == null && displayItem != null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

                    if (meta != null) {
                        String itemName = meta.hasDisplayName() ? meta.getDisplayName() : InventoryUtil.formatMaterialName(displayItem.getType());
                        meta.setDisplayName(msgManager.getRawMessage("my_auctions_gui_item_name_format", "%item_name%", itemName));
                        // Podríamos añadir material si no es misteriosa:
                        // lore.add(msgManager.getRawMessage("main_gui_lore_item_material", "%material%", displayItem.getType().toString()));
                    }
                }

                if (meta != null) {
                    lore.add(msgManager.getRawMessage("my_auctions_gui_lore_status", "%status%", auction.getStatus().getDisplayName()));
                    lore.add(msgManager.getRawMessage("my_auctions_gui_lore_price",
                        "%price%", String.format("%.2f", auction.getCurrentBid()),
                        "%currency%", cfgManager.getCurrencySymbol()
                    ));
                    if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) {
                        lore.add(msgManager.getRawMessage("my_auctions_gui_lore_buy_now",
                            "%buy_now_price%", String.format("%.2f", auction.getBuyNowPrice()),
                            "%currency%", cfgManager.getCurrencySymbol()
                        ));
                    }
                    lore.add(msgManager.getRawMessage("my_auctions_gui_lore_time_remaining", "%time%", InventoryUtil.formatTime(auction.getRemainingTimeMillis())));
                    lore.add(msgManager.getRawMessage("my_auctions_gui_lore_id", "%id%", auction.getId().toString())); // Usar ID completo aquí
                    lore.add(msgManager.getRawMessage("my_auctions_gui_lore_instruction_cancel"));

                    meta.setLore(lore);
                    if (displayItem != null) displayItem.setItemMeta(meta);
                }

                if (displayItem == null) { // Fallback final
                    displayItem = new ItemStack(Material.BARRIER);
                    meta = displayItem.getItemMeta();
                    if (meta != null) meta.setDisplayName(ChatColor.RED + "Error");
                    if (displayItem != null && meta != null) displayItem.setItemMeta(meta);
                }
                gui.setItem(guiSlot, displayItem);
            }
        }

        if (page > 0) {
            gui.setItem(cfgManager.getButtonSlot(guiKey, "previous_page", 45),
                        ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "previous_page", "ARROW"),
                                                 msgManager.getRawMessage("main_gui_button_previous_page")));
        }

        gui.setItem(cfgManager.getButtonSlot(guiKey, "back_to_main_auctions", 49),
                    ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "back_to_main_auctions", "NETHER_STAR"),
                                             msgManager.getRawMessage("my_auctions_gui_button_back_to_main")));

        if (page < totalPages - 1) {
            gui.setItem(cfgManager.getButtonSlot(guiKey, "next_page", 53),
                        ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "next_page", "ARROW"),
                                                 msgManager.getRawMessage("main_gui_button_next_page")));
        }

        if (cfgManager.isHistoryEnabled()) {
            gui.setItem(cfgManager.getButtonSlot(guiKey, "player_history", 51),
                        ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "player_history", "CLOCK"),
                                                 msgManager.getRawMessage("main_gui_history_button_name"),
                                                 msgManager.getStringList("main_gui_history_button_lore")));
        }

        Material decoMat = cfgManager.getMainDecorativePaneMaterial(); // gui.general_appearance.main_decorative_pane
        ItemStack decorativePane = ItemUtil.createItemStack(decoMat, msgManager.getRawMessage("main_gui_decorative_pane_name"));
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, decorativePane.clone());
            }
        }
        player.openInventory(gui);

        // Registrar el GUI abierto con OpenGUIManager
        Map<java.util.UUID, Integer> auctionSlots = new HashMap<>(); // Necesitamos mapear UUID de subasta a slot
        Map<Integer, java.util.UUID> visibleAuctionsMap = new HashMap<>();
        int currentSlot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (currentSlot >= itemsPerPage || currentSlot >= MyActiveAuctionsGUI.PREVIOUS_PAGE_SLOT) break;
            Auction auction = playerActiveAuctions.get(i);
            visibleAuctionsMap.put(currentSlot, java.util.UUID.fromString(auction.getId()));
            currentSlot++;
        }
        final Map<Integer, java.util.UUID> finalVisibleAuctionsMap = new HashMap<>(visibleAuctionsMap);
        plugin.getOpenGUIManager().playerOpenedGUI(player, null, gui, page, "MyActiveAuctionsGUI", finalVisibleAuctionsMap, "default");
    }
}
