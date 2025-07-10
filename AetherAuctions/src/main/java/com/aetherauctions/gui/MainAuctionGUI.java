package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor; // Asegurar esta importación

import java.util.ArrayList;
import java.util.HashMap; // Asegurar esta importación
import java.util.List;
import java.util.Map; // Asegurar esta importación
import java.util.UUID; // Asegurar esta importación
import java.sql.SQLException; // Asegurar esta importación
import java.util.logging.Level; // Asegurar esta importación


public class MainAuctionGUI {

    // Las constantes de slot ya no son necesarias aquí si se leen de config.yml
    // public static final int AUCTION_ITEMS_START_SLOT = 0; // Implícito
    // public static final int PREVIOUS_PAGE_SLOT = 45; // Configurable
    // public static final int MY_AUCTIONS_SLOT = 47; // Configurable
    // public static final int CLOSE_GUI_SLOT = 49; // Configurable
    // public static final int HISTORY_SLOT = 51; // Configurable
    // public static final int REWARDS_BUTTON_SLOT = 52; // Configurable
    // public static final int NEXT_PAGE_SLOT = 53; // Configurable

    public static void open(Player player, int page) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        AuctionManager auctionManager = plugin.getAuctionManager();

        List<Auction> activeAuctions = auctionManager.getActiveAuctions();

        int itemsPerPage = cfgManager.getGuiItemsPerPage(); // Ruta: gui.general_appearance.items_per_page
        int totalItems = activeAuctions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        final int finalPage = Math.max(0, Math.min(page, totalPages - 1));

        String title = msgManager.getMessage("main_gui_title_prefix") +
                       (totalPages > 1 ? " &7(Pág. " + (finalPage + 1) + "/" + totalPages + ")" : "");
        Inventory gui = Bukkit.createInventory(player, 54, title);

        Map<Integer, UUID> visibleAuctionsMap = new HashMap<>();
        int startIndex = finalPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        if (activeAuctions.isEmpty()) {
            ItemStack noAuctionsItem = InventoryUtil.createGuiItem(
                cfgManager.getNoResultsItemMaterial(), // Ruta: gui.general_appearance.no_results_item
                msgManager.getMessage("main_gui_no_auctions")
            );
            gui.setItem(22, noAuctionsItem); // Slot 22 para "no hay subastas"
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Auction auction = activeAuctions.get(i);
                int guiSlot = i - startIndex; // Slots 0 hasta itemsPerPage-1
                // La comprobación de si guiSlot >= PREVIOUS_PAGE_SLOT ya no es necesaria aquí
                // porque los items de subasta no deberían llegar a esos slots.
                if (guiSlot >= itemsPerPage) break;

                ItemStack displayItem = createAuctionDisplayItem(auction, plugin);
                gui.setItem(guiSlot, displayItem);
                visibleAuctionsMap.put(guiSlot, UUID.fromString(auction.getId()));
            }
        }

        // Botones de Navegación y Acción desde config.yml
        String guiKey = "main_auction_house"; // Clave base para esta GUI en config

        if (finalPage > 0) {
            gui.setItem(cfgManager.getButtonSlot(guiKey, "previous_page", 45),
                        InventoryUtil.createGuiItem(cfgManager.getButtonMaterial(guiKey, "previous_page", "ARROW"),
                                                    msgManager.getMessage("main_gui_button_previous_page")));
        }
        gui.setItem(cfgManager.getButtonSlot(guiKey, "close", 49),
                    InventoryUtil.createGuiItem(cfgManager.getButtonMaterial(guiKey, "close", "BARRIER"),
                                                msgManager.getMessage("main_gui_button_close")));
        if (finalPage < totalPages - 1) {
            gui.setItem(cfgManager.getButtonSlot(guiKey, "next_page", 53),
                        InventoryUtil.createGuiItem(cfgManager.getButtonMaterial(guiKey, "next_page", "ARROW"),
                                                    msgManager.getMessage("main_gui_button_next_page")));
        }

        final Map<Integer, UUID> finalVisibleAuctionsMap = new HashMap<>(visibleAuctionsMap);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int pendingRewardCount = 0;
            try {
                 pendingRewardCount = plugin.getAuctionStorage().getPendingRewardsByOwner(player.getUniqueId())
                                    .stream().filter(r -> !r.isDelivered()).toList().size();
            } catch (Exception e){
                 plugin.getLogger().log(Level.SEVERE, "Error al obtener conteo de recompensas pendientes para GUI: " + player.getName(), e);
            }
            final int finalPendingRewardCount = pendingRewardCount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> rewardsLore = new ArrayList<>();
                rewardsLore.add(msgManager.getRawMessage("main_gui_rewards_button_lore_count", "%pending_count%", String.valueOf(finalPendingRewardCount)));
                rewardsLore.add(msgManager.getRawMessage("main_gui_rewards_button_lore_action"));
                gui.setItem(cfgManager.getButtonSlot(guiKey, "claim_rewards", 52),
                            InventoryUtil.createGuiItem(cfgManager.getButtonMaterial(guiKey, "claim_rewards", "CHEST"),
                                                        msgManager.getRawMessage("main_gui_rewards_button_name"), rewardsLore));

                if (cfgManager.isMyAuctionsGuiIntegrationEnabled()) { // Usar nuevo getter
                    gui.setItem(cfgManager.getButtonSlot(guiKey, "my_auctions", 47),
                                InventoryUtil.createGuiItem(cfgManager.getButtonMaterial(guiKey, "my_auctions", "WRITABLE_BOOK"),
                                                            msgManager.getRawMessage("main_gui_my_auctions_button_name"),
                                                            msgManager.getStringList("main_gui_my_auctions_button_lore")));
                }
                if (cfgManager.isHistoryEnabled()) {
                    gui.setItem(cfgManager.getButtonSlot(guiKey, "player_history", 51),
                                InventoryUtil.createGuiItem(cfgManager.getButtonMaterial(guiKey, "player_history", "CLOCK"),
                                                            msgManager.getRawMessage("main_gui_history_button_name"),
                                                            msgManager.getStringList("main_gui_history_button_lore")));
                }

                Material decoMat = cfgManager.getMainDecorativePaneMaterial(); // Ruta: gui.general_appearance.main_decorative_pane
                String decoName = msgManager.getMessage("main_gui_decorative_pane_name");
                ItemStack decorativePane = InventoryUtil.createGuiItem(decoMat, decoName);
                for (int i = 0; i < gui.getSize(); i++) {
                    if (gui.getItem(i) == null) {
                        gui.setItem(i, decorativePane.clone());
                    }
                }
                player.openInventory(gui);
                plugin.getOpenGUIManager().playerOpenedGUI(player, null, gui, finalPage, "MainAuctionGUI", finalVisibleAuctionsMap, "default");
            });
        });
    }

    public static ItemStack createAuctionDisplayItem(Auction auction, AetherAuctions plugin) {
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        ItemStack displayItem = null; // Inicializar a null
        ItemMeta meta = null; // Inicializar a null
        List<String> lore = new ArrayList<>();

        if (auction.isMystery()) {
            displayItem = new ItemStack(Material.ENDER_CHEST);
            meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(msgManager.getRawMessage("mystery_auction_gui_item_name"));
                lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_description", "%description%", auction.getMysteryDescription()));
                try {
                    int itemCount = plugin.getAuctionStorage().getMysteryAuctionContentsCount(UUID.fromString(auction.getId()));
                     lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_item_count", "%count%", String.valueOf(itemCount)));
                } catch (SQLException e) { // Asegurar que SQLException está importada
                     plugin.getLogger().log(Level.WARNING, "Could not get item count for mystery auction " + auction.getId() + " for GUI display.", e);
                     lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_item_count_error"));
                }
            }
        } else {
            if (auction.getItemStack() == null || auction.getItemStack().getType() == Material.AIR) {
                displayItem = new ItemStack(Material.BARRIER);
                meta = displayItem.getItemMeta();
                if (meta != null) meta.setDisplayName(ChatColor.RED + "Error: Ítem no disponible");
            } else {
                displayItem = auction.getItemStack().clone();
                meta = displayItem.getItemMeta();
            }
            if (meta == null && displayItem != null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

            if (meta != null) {
                String itemName = meta.hasDisplayName() ? meta.getDisplayName() : InventoryUtil.formatMaterialName(displayItem.getType());
                meta.setDisplayName(msgManager.getRawMessage("main_gui_item_name_format", "%item_name%", itemName));
                lore.add(msgManager.getRawMessage("main_gui_lore_item_material", "%material%", displayItem.getType().toString()));
            }
        }

        if (meta != null) { // Si meta no es null (es decir, tenemos un ítem válido para trabajar)
            lore.add(msgManager.getRawMessage("main_gui_lore_seller", "%seller%", auction.getSellerName()));
            lore.add(msgManager.getRawMessage("main_gui_lore_price",
                "%price%", String.format("%.2f", auction.getCurrentBid()),
                "%currency%", cfgManager.getCurrencySymbol()
            ));
            if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) {
                lore.add(msgManager.getRawMessage("main_gui_lore_buy_now",
                    "%buy_now_price%", String.format("%.2f", auction.getBuyNowPrice()),
                    "%currency%", cfgManager.getCurrencySymbol()
                ));
            } else {
                lore.add(msgManager.getRawMessage("main_gui_lore_buy_now_not_available"));
            }
            lore.add(msgManager.getRawMessage("main_gui_lore_time_remaining", "%time%", InventoryUtil.formatTime(auction.getRemainingTimeMillis())));
            lore.add(msgManager.getRawMessage("main_gui_lore_id", "%id%", auction.getId().toString()));
            lore.add(msgManager.getRawMessage("main_gui_lore_instruction_details"));
            meta.setLore(lore);
            if(displayItem != null) displayItem.setItemMeta(meta); // Aplicar meta solo si displayItem no es null
        }

        if (displayItem == null) { // Si después de todo displayItem sigue siendo null (caso extremo)
            displayItem = new ItemStack(Material.BARRIER);
            meta = displayItem.getItemMeta();
            if (meta != null) meta.setDisplayName(ChatColor.RED + "Error al mostrar ítem");
            if(displayItem != null) displayItem.setItemMeta(meta);
        }
        return displayItem;
    }
}
