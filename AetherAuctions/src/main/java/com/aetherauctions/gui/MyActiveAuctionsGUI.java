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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MyActiveAuctionsGUI {

    // Similar a MainAuctionGUI pero filtrado
    public static final int ITEMS_PER_PAGE_CONFIGURABLE = 36; // Podría leerse de config si se desea diferente a Main GUI
    public static final int PREVIOUS_PAGE_SLOT = 45;
    public static final int CLOSE_GUI_SLOT = 49; // O un botón de "Volver a Subastas"
    public static final int NEXT_PAGE_SLOT = 53;
    public static final int HISTORY_BUTTON_SLOT = 51; // Si se decide añadir acceso directo al historial

    public static void open(Player player, int page) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        AuctionManager auctionManager = plugin.getAuctionManager();

        List<Auction> playerActiveAuctions = auctionManager.getPlayerActiveAuctions(player.getUniqueId());

        int itemsPerPage = cfgManager.getGuiItemsPerPage(); // Usar la misma config que Main GUI o una nueva
        int totalItems = playerActiveAuctions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = msgManager.getRawMessage("my_auctions_gui_title", "%player_name%", player.getName()) +
                       (totalPages > 1 ? " &7(Pág. " + (page + 1) + "/" + totalPages + ")" : "");
        Inventory gui = Bukkit.createInventory(player, 54, title);

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        if (playerActiveAuctions.isEmpty()) {
            gui.setItem(22, ItemUtil.createItemStack(Material.GLASS_BOTTLE, msgManager.getRawMessage("my_auctions_gui_no_auctions")));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Auction auction = playerActiveAuctions.get(i);
                int guiSlot = i - startIndex;
                 if (guiSlot >= itemsPerPage || guiSlot >= MyActiveAuctionsGUI.PREVIOUS_PAGE_SLOT) break;


                ItemStack displayItem = auction.getItemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();
                if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

                String itemName = meta.hasDisplayName() ? meta.getDisplayName() : InventoryUtil.formatMaterialName(displayItem.getType());
                meta.setDisplayName(msgManager.getRawMessage("my_auctions_gui_item_name_format", "%item_name%", itemName)); // Puede reutilizar o tener formato propio

                List<String> lore = new ArrayList<>();
                lore.add(msgManager.getRawMessage("my_auctions_gui_lore_status", "%status%", auction.getStatus().getDisplayName())); // Nueva clave para estado
                lore.add(msgManager.getRawMessage("my_auctions_gui_lore_price", // Reutilizar o clave nueva
                    "%price%", String.format("%.2f", auction.getCurrentBid()),
                    "%currency%", cfgManager.getCurrencySymbol()
                ));
                if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) {
                    lore.add(msgManager.getRawMessage("my_auctions_gui_lore_buy_now", // Reutilizar o clave nueva
                        "%buy_now_price%", String.format("%.2f", auction.getBuyNowPrice()),
                        "%currency%", cfgManager.getCurrencySymbol()
                    ));
                }
                lore.add(msgManager.getRawMessage("my_auctions_gui_lore_time_remaining", "%time%", InventoryUtil.formatTime(auction.getRemainingTimeMillis())));
                lore.add(msgManager.getRawMessage("my_auctions_gui_lore_id", "%id%", auction.getId().toString()));
                lore.add(msgManager.getRawMessage("my_auctions_gui_lore_instruction_cancel")); // Nueva clave: "&cClic para intentar cancelar"

                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                gui.setItem(guiSlot, displayItem);
            }
        }

        if (page > 0) {
            gui.setItem(PREVIOUS_PAGE_SLOT, ItemUtil.createItemStack(Material.ARROW, msgManager.getRawMessage("main_gui_button_previous_page")));
        }

        // Botón para volver a la GUI Principal de Subastas en lugar de "Cerrar"
        gui.setItem(CLOSE_GUI_SLOT, ItemUtil.createItemStack(Material.NETHER_STAR, msgManager.getRawMessage("my_auctions_gui_button_back_to_main"))); // Nueva Clave

        if (page < totalPages - 1) {
            gui.setItem(NEXT_PAGE_SLOT, ItemUtil.createItemStack(Material.ARROW, msgManager.getRawMessage("main_gui_button_next_page")));
        }

        // Botón Historial (opcional, si se quiere acceso directo desde aquí también)
        if (cfgManager.isHistoryEnabled()) {
            gui.setItem(HISTORY_BUTTON_SLOT, ItemUtil.createItemStack(Material.CLOCK,
                msgManager.getRawMessage("main_gui_history_button_name"), // Reutilizar clave
                msgManager.getStringList("main_gui_history_button_lore")  // Reutilizar clave
            ));
        }

        Material decoMat = cfgManager.getMainDecorativePaneMaterial(); // Podría tener su propio material decorativo
        ItemStack decorativePane = ItemUtil.createItemStack(decoMat, msgManager.getRawMessage("main_gui_decorative_pane_name"));
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, decorativePane.clone());
            }
        }
        player.openInventory(gui);
    }
}
