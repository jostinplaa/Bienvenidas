package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.AuctionHistoryEvent;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.ItemUtil;
import com.aetherauctions.util.SerializationUtil; // Para deserializar item_snapshot si es necesario
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerHistoryGUI {

    // Las constantes de slot se leerán de config.yml
    // public static final int PREVIOUS_PAGE_SLOT = 45;
    // public static final int CLOSE_GUI_SLOT = 49; // Renombrado a BACK_TO_MAIN_SLOT
    // public static final int NEXT_PAGE_SLOT = 53;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm");


    public static void open(Player player, int page) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        String guiKey = "player_history_gui"; // Clave base para esta GUI en config

        if (!cfgManager.isHistoryEnabled()) {
            msgManager.sendMessage(player, "history_disabled");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int itemsPerPage = cfgManager.getGuiItemsPerPage(); // gui.general_appearance.items_per_page
                int totalItems = plugin.getAuctionStorage().getHistoryCount(player.getUniqueId());
                List<AuctionHistoryEvent> historyEvents = plugin.getAuctionStorage().getAuctionHistory(player.getUniqueId(), page, itemsPerPage);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
                    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

                    String title = msgManager.getRawMessage("player_history_gui_title", "%player_name%", player.getName()) +
                                   (totalPages > 1 ? " &7(Pág. " + (currentPage + 1) + "/" + totalPages + ")" : "");
                    Inventory gui = Bukkit.createInventory(player, 54, title);

                    if (historyEvents.isEmpty()) {
                        gui.setItem(22, ItemUtil.createItemStack(cfgManager.getNoResultsItemMaterial(), // gui.general_appearance.no_results_item
                                                                 msgManager.getRawMessage("player_history_gui_no_events")));
                    } else {
                        for (int i = 0; i < historyEvents.size(); i++) {
                            AuctionHistoryEvent event = historyEvents.get(i);
                            int guiSlot = i;
                             // La comprobación de si guiSlot >= PREVIOUS_PAGE_SLOT ya no es necesaria aquí
                            if (guiSlot >= itemsPerPage) break;

                            Material itemMat = Material.PAPER;
                            try {
                                if (event.getItemMaterial() != null && !event.getItemMaterial().isEmpty()) {
                                    itemMat = Material.valueOf(event.getItemMaterial());
                                }
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Material inválido en historial: " + event.getItemMaterial() + " para evento " + event.getHistoryId());
                            }

                            ItemStack displayItem = new ItemStack(itemMat);
                            ItemMeta meta = displayItem.getItemMeta();
                            if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

                            meta.setDisplayName(msgManager.getRawMessage("history_event_name_" + event.getEventType().name().toLowerCase(),
                                    "%item_name%", event.getItemName() != null ? event.getItemName() : "N/A"));

                            List<String> lore = new ArrayList<>();
                            lore.add(msgManager.getRawMessage("history_event_lore_type", "%type%", event.getEventType().toString()));
                            if (event.getAuctionId() != null) {
                                lore.add(msgManager.getRawMessage("history_event_lore_auction_id", "%id%", event.getAuctionId().toString()));
                            }
                            lore.add(msgManager.getRawMessage("history_event_lore_item", "%item%", event.getItemName() != null ? event.getItemName() : "N/A"));
                            if (event.getPrice() > 0) {
                                lore.add(msgManager.getRawMessage("history_event_lore_price", "%price%", String.format("%.2f", event.getPrice()), "%currency%", cfgManager.getCurrencySymbol()));
                            }
                            if (event.getCounterpartyName() != null) {
                                lore.add(msgManager.getRawMessage("history_event_lore_counterparty", "%player%", event.getCounterpartyName()));
                            }
                            lore.add(msgManager.getRawMessage("history_event_lore_date", "%date%", DATE_FORMAT.format(new Date(event.getTimestamp()))));

                            meta.setLore(lore);
                            displayItem.setItemMeta(meta);
                            gui.setItem(guiSlot, displayItem);
                        }
                    }

                    if (currentPage > 0) {
                        gui.setItem(cfgManager.getButtonSlot(guiKey, "previous_page", 45),
                                    ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "previous_page", "ARROW"),
                                                             msgManager.getRawMessage("main_gui_button_previous_page")));
                    }
                    gui.setItem(cfgManager.getButtonSlot(guiKey, "back_to_main_auctions", 49), // Clave de botón para volver
                                ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "back_to_main_auctions", "NETHER_STAR"),
                                                         msgManager.getRawMessage("my_auctions_gui_button_back_to_main"))); // Reutilizar mensaje
                    if (currentPage < totalPages - 1) {
                        gui.setItem(cfgManager.getButtonSlot(guiKey, "next_page", 53),
                                    ItemUtil.createItemStack(cfgManager.getButtonMaterial(guiKey, "next_page", "ARROW"),
                                                             msgManager.getRawMessage("main_gui_button_next_page")));
                    }

                    Material decoMat = cfgManager.getMainDecorativePaneMaterial(); // gui.general_appearance.main_decorative_pane
                    ItemStack decorativePane = ItemUtil.createItemStack(decoMat, msgManager.getRawMessage("main_gui_decorative_pane_name"));
                    for (int i = 0; i < gui.getSize(); i++) {
                        if (gui.getItem(i) == null) {
                            gui.setItem(i, decorativePane.clone());
                        }
                    }
                    player.openInventory(gui);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error SQL al obtener historial para el jugador " + player.getName(), e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    msgManager.sendMessage(player, "database_error_general");
                });
            }
        });
    }
}
