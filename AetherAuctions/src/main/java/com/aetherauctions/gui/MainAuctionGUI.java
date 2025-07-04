package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.Auction; // Corrected import
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

    public static final int AUCTION_ITEMS_START_SLOT = 0;
    public static final int PREVIOUS_PAGE_SLOT = 45;
    public static final int MY_AUCTIONS_SLOT = 47;
    public static final int CLOSE_GUI_SLOT = 49;
    public static final int HISTORY_SLOT = 51;
    public static final int REWARDS_BUTTON_SLOT = 52;
    public static final int NEXT_PAGE_SLOT = 53;

    public static void open(Player player, int page) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        AuctionManager auctionManager = plugin.getAuctionManager();

        List<Auction> activeAuctions = auctionManager.getActiveAuctions();

        int itemsPerPage = cfgManager.getGuiItemsPerPage();
        int totalItems = activeAuctions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        final int finalPage = Math.max(0, Math.min(page, totalPages - 1)); // Hacerla final para la lambda

        String title = msgManager.getMessage("main_gui_title_prefix") +
                       (totalPages > 1 ? " &7(Pág. " + (finalPage + 1) + "/" + totalPages + ")" : "");
        Inventory gui = Bukkit.createInventory(player, 54, title); // Usar el player como owner

        Map<Integer, UUID> visibleAuctionsMap = new HashMap<>();
        int startIndex = finalPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        if (activeAuctions.isEmpty()) {
            ItemStack noAuctionsItem = InventoryUtil.createGuiItem(
                Material.GLASS_BOTTLE,
                msgManager.getMessage("main_gui_no_auctions")
            );
            gui.setItem(22, noAuctionsItem);
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Auction auction = activeAuctions.get(i);
                int guiSlot = i - startIndex;
                if (guiSlot >= itemsPerPage || guiSlot >= MainAuctionGUI.PREVIOUS_PAGE_SLOT) break;

                ItemStack displayItem = createAuctionDisplayItem(auction, plugin);
                gui.setItem(guiSlot, displayItem);
                visibleAuctionsMap.put(guiSlot, auction.getId());
            }
        }

        if (finalPage > 0) {
            gui.setItem(PREVIOUS_PAGE_SLOT, InventoryUtil.createGuiItem(Material.ARROW, msgManager.getMessage("main_gui_button_previous_page")));
        }
        gui.setItem(CLOSE_GUI_SLOT, InventoryUtil.createGuiItem(Material.BARRIER, msgManager.getMessage("main_gui_button_close")));
        if (finalPage < totalPages - 1) {
            gui.setItem(NEXT_PAGE_SLOT, InventoryUtil.createGuiItem(Material.ARROW, msgManager.getMessage("main_gui_button_next_page")));
        }

        final Map<Integer, UUID> finalVisibleAuctionsMap = new HashMap<>(visibleAuctionsMap); // Copia final para la lambda

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int pendingRewardCount = 0;
            try {
                 pendingRewardCount = plugin.getAuctionStorage().getPendingRewardsByOwner(player.getUniqueId())
                                    .stream().filter(r -> !r.isDelivered()).toList().size();
            } catch (Exception e){ //SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Error al obtener conteo de recompensas pendientes para GUI: " + player.getName(), e);
            }
            final int finalPendingRewardCount = pendingRewardCount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> rewardsLore = new ArrayList<>();
                rewardsLore.add(msgManager.getRawMessage("main_gui_rewards_button_lore_count", "%pending_count%", String.valueOf(finalPendingRewardCount)));
                rewardsLore.add(msgManager.getRawMessage("main_gui_rewards_button_lore_action"));
                gui.setItem(REWARDS_BUTTON_SLOT, InventoryUtil.createGuiItem(Material.CHEST, msgManager.getRawMessage("main_gui_rewards_button_name"), rewardsLore));

                if (cfgManager.isMyAuctionsGuiEnabled()) {
                    gui.setItem(MY_AUCTIONS_SLOT, InventoryUtil.createGuiItem(Material.WRITABLE_BOOK,
                        msgManager.getRawMessage("main_gui_my_auctions_button_name"),
                        msgManager.getStringList("main_gui_my_auctions_button_lore")
                    ));
                }
                if (cfgManager.isHistoryEnabled()) {
                    gui.setItem(HISTORY_SLOT, InventoryUtil.createGuiItem(Material.CLOCK,
                        msgManager.getRawMessage("main_gui_history_button_name"),
                        msgManager.getStringList("main_gui_history_button_lore")
                    ));
                }

                Material decoMat = cfgManager.getMainDecorativePaneMaterial();
                String decoName = msgManager.getMessage("main_gui_decorative_pane_name");
                ItemStack decorativePane = InventoryUtil.createGuiItem(decoMat, decoName);
                for (int i = 0; i < gui.getSize(); i++) {
                    if (gui.getItem(i) == null) {
                        gui.setItem(i, decorativePane.clone());
                    }
                }
                player.openInventory(gui);
                plugin.getOpenGUIManager().playerOpenedGUI(player, gui, finalPage, "MainAuctionGUI", finalVisibleAuctionsMap);
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
                    int itemCount = plugin.getAuctionStorage().getMysteryAuctionContentsCount(auction.getId());
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
