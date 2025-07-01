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

import java.util.ArrayList;
import java.util.List;
// import java.util.stream.Collectors; // Not strictly needed for this version

public class MainAuctionGUI {

    public static final int AUCTION_ITEMS_START_SLOT = 0;
    // AUCTION_ITEMS_END_SLOT will depend on items_per_page from config
    public static final int PREVIOUS_PAGE_SLOT = 45; // Bottom left
    public static final int MY_AUCTIONS_SLOT = 47;   // Bottom row, second from left
    public static final int CLOSE_GUI_SLOT = 49;     // Bottom center
    public static final int HISTORY_SLOT = 51;       // Bottom row, second from right
    public static final int REWARDS_BUTTON_SLOT = 52; // Bottom row, penultimate right
    public static final int NEXT_PAGE_SLOT = 53;     // Bottom right
    // public static final int REFRESH_BUTTON_SLOT = 48; // Example if added

    public static void open(Player player, int page) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        AuctionManager auctionManager = plugin.getAuctionManager();

        List<Auction> activeAuctions = auctionManager.getActiveAuctions();

        int itemsPerPage = cfgManager.getGuiItemsPerPage();
        int totalItems = activeAuctions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = msgManager.getMessage("main_gui_title_prefix") +
                       (totalPages > 1 ? " &7(Pág. " + (page + 1) + "/" + totalPages + ")" : "");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // --- Auction Items ---
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        if (activeAuctions.isEmpty()) {
            ItemStack noAuctionsItem = InventoryUtil.createGuiItem(
                Material.GLASS_BOTTLE,
                msgManager.getMessage("main_gui_no_auctions")
            );
            gui.setItem(22, noAuctionsItem); // Central slot if no auctions
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Auction auction = activeAuctions.get(i);
                int guiSlot = i - startIndex;
                if (guiSlot >= itemsPerPage || guiSlot >= MainAuctionGUI.PREVIOUS_PAGE_SLOT) break; // Prevent overriding buttons if itemsPerPage is too large for 6 rows

                ItemStack displayItem = auction.getItemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();
                if (meta == null) {
                    meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
                }

                String itemName = meta.hasDisplayName() ? meta.getDisplayName() : InventoryUtil.formatMaterialName(displayItem.getType());
                meta.setDisplayName(msgManager.getMessage("main_gui_item_name_format", "%item_name%", itemName));

                List<String> lore = new ArrayList<>();
                lore.add(msgManager.getMessage("main_gui_lore_seller", "%seller%", auction.getSellerName()));
                lore.add(msgManager.getMessage("main_gui_lore_price",
                    "%price%", String.format("%.2f", auction.getCurrentBid()),
                    "%currency%", cfgManager.getCurrencySymbol()
                ));
                if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed()) {
                    lore.add(msgManager.getMessage("main_gui_lore_buy_now",
                        "%buy_now_price%", String.format("%.2f", auction.getBuyNowPrice()),
                        "%currency%", cfgManager.getCurrencySymbol()
                    ));
                } else {
                    lore.add(msgManager.getMessage("main_gui_lore_buy_now_not_available"));
                }
                lore.add(msgManager.getMessage("main_gui_lore_time_remaining", "%time%", InventoryUtil.formatTime(auction.getRemainingTimeMillis())));
                // Example for bid count, ensure getBidHistory() is efficient or cache size
                // lore.add(msgManager.getMessage("main_gui_lore_bids", "%bid_count%", String.valueOf(auction.getBidHistory().size())));
                lore.add(msgManager.getMessage("main_gui_lore_id", "%id%", auction.getId().toString())); // Usar ID completo
                lore.add(msgManager.getMessage("main_gui_lore_instruction_details"));

                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                gui.setItem(guiSlot, displayItem);
            }
        }

        // --- Navigation Buttons ---
        if (page > 0) {
            gui.setItem(PREVIOUS_PAGE_SLOT, InventoryUtil.createGuiItem(Material.ARROW, msgManager.getMessage("main_gui_button_previous_page")));
        }
        gui.setItem(CLOSE_GUI_SLOT, InventoryUtil.createGuiItem(Material.BARRIER, msgManager.getMessage("main_gui_button_close")));
        if (page < totalPages - 1) {
            gui.setItem(NEXT_PAGE_SLOT, InventoryUtil.createGuiItem(Material.ARROW, msgManager.getMessage("main_gui_button_next_page")));
        }

        // --- Rewards Button ---
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int pendingRewardCount = plugin.getAuctionStorage().getPendingRewardsByOwner(player.getUniqueId())
                                    .stream().filter(r -> !r.isDelivered()).toList().size();
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> rewardsLore = new ArrayList<>();
                rewardsLore.add(msgManager.getRawMessage("main_gui_rewards_button_lore_count", "%pending_count%", String.valueOf(pendingRewardCount)));
                rewardsLore.add(msgManager.getRawMessage("main_gui_rewards_button_lore_action"));
                gui.setItem(REWARDS_BUTTON_SLOT, InventoryUtil.createGuiItem(Material.CHEST, msgManager.getRawMessage("main_gui_rewards_button_name"), rewardsLore));

                // --- My Auctions Button ---
                if (cfgManager.isMyAuctionsGuiEnabled()) {
                    gui.setItem(MY_AUCTIONS_SLOT, InventoryUtil.createGuiItem(Material.WRITABLE_BOOK,
                        msgManager.getRawMessage("main_gui_my_auctions_button_name"), // Nueva clave
                        msgManager.getStringList("main_gui_my_auctions_button_lore") // Nueva clave
                    ));
                }

                // --- History Button ---
                if (cfgManager.isHistoryEnabled()) {
                    gui.setItem(HISTORY_SLOT, InventoryUtil.createGuiItem(Material.CLOCK,
                        msgManager.getRawMessage("main_gui_history_button_name"), // Nueva clave
                        msgManager.getStringList("main_gui_history_button_lore") // Nueva clave
                    ));
                }

                // --- Fill empty slots ---
                Material decoMat = cfgManager.getMainDecorativePaneMaterial();
                String decoName = msgManager.getMessage("main_gui_decorative_pane_name");
                ItemStack decorativePane = InventoryUtil.createGuiItem(decoMat, decoName);
                for (int i = 0; i < gui.getSize(); i++) {
                    if (gui.getItem(i) == null) {
                        gui.setItem(i, decorativePane.clone());
                    }
                }
                player.openInventory(gui);
            });
        });
    }
}
