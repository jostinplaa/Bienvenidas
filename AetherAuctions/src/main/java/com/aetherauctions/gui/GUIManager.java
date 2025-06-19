package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.config.ConfigManager; // Import ConfigManager
import com.aetherauctions.config.MessageManager; // Import MessageManager
import com.aetherauctions.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // Explicit import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GUIManager {

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final ConfigManager configManager; // Add ConfigManager
    private final MessageManager messageManager; // Add MessageManager
    private final Map<UUID, Integer> openInventoriesPage;
    private final Map<UUID, ItemStack> playerItemToAuction = new HashMap<>();
    private final Map<UUID, Long> playerAuctionDuration = new HashMap<>();
    private final Map<UUID, Double> playerAuctionStartPrice = new HashMap<>();
    private final Map<UUID, Double> playerAuctionBuyNowPrice = new HashMap<>();

    // Static titles are not needed here anymore as they come from MessageManager

    // Helper method to format duration
    private String formatDuration(long millis) {
        if (millis < 0) return messageManager.getMessage("time_ended", "Error"); // Should not happen with active auctions
        if (millis == 0) return messageManager.getMessage("time_ended", "Finalizada");

        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) { // Show seconds if it's the only unit or if total time is < 1 min
            sb.append(seconds).append("s");
        }

        String result = sb.toString().trim();
        // If the auction is very short (e.g., a few seconds) and formatDuration results in an empty string
        // or if only seconds are left and they are, for example, less than a threshold for "ending soon"
        if (result.isEmpty() && millis > 0) { // Should not be empty if millis > 0 due to seconds append logic
             return millis / 1000 + "s"; // Fallback to raw seconds
        }
        if (millis > 0 && millis < 60000 && !result.contains("s")) { // Less than a minute, ensure seconds are shown
            // This case might be covered by "seconds > 0 || sb.length() == 0" already
        }
        return result.isEmpty() ? messageManager.getMessage("time_ended", "Finalizada") : result;
    }


    public GUIManager(AetherAuctions plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.configManager = plugin.getConfigManager(); // Get ConfigManager
        this.messageManager = plugin.getMessageManager(); // Get MessageManager
        this.openInventoriesPage = new HashMap<>();
    }

    public void openMainAuctionGui(Player player, int page) {
        openInventoriesPage.put(player.getUniqueId(), page);
        List<AuctionItem> activeAuctions = new ArrayList<>(auctionManager.getActiveAuctionsMap().values().stream()
                .filter(auc -> auc.getStatus() == AuctionStatus.ACTIVE)
                .sorted((a1, a2) -> Long.compare(a1.getStartTime() + a1.getDuration(), a2.getStartTime() + a2.getDuration()))
                .collect(Collectors.toList()));

        int itemsPerPage = 45;
        int totalItems = activeAuctions.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(1, Math.min(page, maxPages));

        String title = messageManager.getMessage("main_gui_title", "%page%", String.valueOf(page), "%max_pages%", String.valueOf(maxPages));
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem auction = activeAuctions.get(i);
            ItemStack displayItem = auction.getItemStack().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta == null) { // Should not happen with a cloned item, but good practice
                meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
            }

            // Set display name using the new format
            String originalItemName = displayItem.hasItemMeta() && displayItem.getItemMeta().hasDisplayName()
                                    ? displayItem.getItemMeta().getDisplayName()
                                    : auction.getItemStack().getType().name().replace("_", " "); // Fallback to material name
            meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_name%", originalItemName));

            List<String> lore = new ArrayList<>();
            lore.add(messageManager.getMessage("lore_auction_id", "%id%", String.valueOf(auction.getId())));
            lore.add(messageManager.getMessage("lore_seller", "%player_name%", auction.getSellerName()));

            if (configManager.isBuyNowAllowed() && auction.getBuyNowPrice() > 0) {
                lore.add(messageManager.getMessage("lore_buyout_price",
                                                "%price%", String.format("%.2f", auction.getBuyNowPrice()),
                                                "%currency%", configManager.getCurrencySymbol()));
            }

            // Using getCurrentBid() for minimum/current bid display as per new key
            lore.add(messageManager.getMessage("lore_minimum_bid",
                                            "%price%", String.format("%.2f", auction.getCurrentBid()),
                                            "%currency%", configManager.getCurrencySymbol()));

            lore.add(messageManager.getMessage("lore_time_remaining", "%time%", formatDuration(auction.getStartTime() + auction.getDuration() - System.currentTimeMillis())));
            lore.add(" "); // Separator
            lore.add(messageManager.getMessage("lore_instruction_bid"));
            lore.add(messageManager.getMessage("lore_instruction_details"));
            // The old item_lore_id was visible, the new one is also visible by default.
            // If it needs to be "hidden" (e.g., color codes to make it unreadable), that should be in messages.yml

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
            gui.setItem(i - startIndex, displayItem);
        }

        // Navigation and action buttons
        if (page > 1) {
            gui.setItem(45, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_previous_page")));
        } else {
            gui.setItem(45, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        gui.setItem(46, InventoryUtil.createGuiItem(Material.OAK_SIGN, messageManager.getMessage("button_search_items"), false, messageManager.getMessage("lore_search_items_coming_soon")));
        // Slot 47 is now empty (or can be a glass pane)
        gui.setItem(47, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        gui.setItem(48, InventoryUtil.createGuiItem(Material.ANVIL, messageManager.getMessage("button_create_auction")));
        // Slot 49 is now the Close button
        gui.setItem(49, InventoryUtil.createGuiItem(Material.BARRIER, messageManager.getMessage("button_close_gui"))); // Assuming "button_close_gui" key
        gui.setItem(50, InventoryUtil.createGuiItem(Material.CHEST, messageManager.getMessage("button_my_auctions")));
        gui.setItem(51, InventoryUtil.createGuiItem(Material.BOOK, messageManager.getMessage("button_history")));
        gui.setItem(52, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        if (page < maxPages) {
            gui.setItem(53, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_next_page")));
        } else {
            gui.setItem(53, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        player.openInventory(gui);
    }

    public void openCreateAuctionGui(Player player) {
        openInventoriesPage.remove(player.getUniqueId());
        String title = messageManager.getMessage("auction_create_gui_title");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        String noValue = messageManager.getRaw("status_not_set"); // Assuming a key like "status_not_set: No establecido"

        String selectedDurationStr = playerAuctionDuration.containsKey(player.getUniqueId()) ? InventoryUtil.formatTime(playerAuctionDuration.get(player.getUniqueId())) : noValue;
        String startPriceStr = playerAuctionStartPrice.containsKey(player.getUniqueId()) ? String.format("%.2f %s", playerAuctionStartPrice.get(player.getUniqueId()), configManager.getCurrencySymbol()) : noValue;
        String buyNowPriceStr = playerAuctionBuyNowPrice.containsKey(player.getUniqueId()) ? String.format("%.2f %s", playerAuctionBuyNowPrice.get(player.getUniqueId()), configManager.getCurrencySymbol()) : noValue + " (Opcional)";
        ItemStack itemToAuction = playerItemToAuction.get(player.getUniqueId());

        gui.setItem(10, InventoryUtil.createGuiItem(Material.CLOCK, messageManager.getMessage("button_set_duration"), false, messageManager.getMessage("lore_current_duration", "%duration%", selectedDurationStr), messageManager.getRaw("lore_click_to_change")));
        gui.setItem(12, InventoryUtil.createGuiItem(Material.GOLD_INGOT, messageManager.getMessage("button_set_start_price"), false, messageManager.getMessage("lore_current_start_price", "%price%", startPriceStr), messageManager.getRaw("lore_click_to_change")));

        if (itemToAuction == null) {
            gui.setItem(13, InventoryUtil.createGuiItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, messageManager.getMessage("item_slot_placeholder_name"), false, messageManager.getMessage("item_slot_placeholder_lore")));
        } else {
            gui.setItem(13, itemToAuction);
        }

        if(configManager.isBuyNowAllowed()){
            gui.setItem(14, InventoryUtil.createGuiItem(Material.EMERALD, messageManager.getMessage("button_set_buy_now_price"), false, messageManager.getMessage("lore_current_buy_now_price", "%price%", buyNowPriceStr), messageManager.getRaw("lore_click_to_change_or_remove")));
        } else {
            gui.setItem(14, InventoryUtil.createGuiItem(Material.BARRIER, messageManager.getMessage("button_set_buy_now_price_disabled"), false, messageManager.getMessage("lore_buy_now_disabled_admin")));
        }

        boolean canConfirm = itemToAuction != null && playerAuctionDuration.containsKey(player.getUniqueId()) && playerAuctionStartPrice.containsKey(player.getUniqueId());
        if (canConfirm) {
            gui.setItem(16, InventoryUtil.createGuiItem(Material.GREEN_STAINED_GLASS_PANE, messageManager.getMessage("button_confirm_create"), true, messageManager.getMessage("lore_confirm_create_review")));
        } else {
            List<String> missingDetailsList = new ArrayList<>();
            if(itemToAuction == null) missingDetailsList.add(messageManager.getRaw("missing_detail_item")); // Example new keys
            if(!playerAuctionDuration.containsKey(player.getUniqueId())) missingDetailsList.add(messageManager.getRaw("missing_detail_duration"));
            if(!playerAuctionStartPrice.containsKey(player.getUniqueId())) missingDetailsList.add(messageManager.getRaw("missing_detail_start_price"));
            String missingDetails = String.join(", ", missingDetailsList);
            gui.setItem(16, InventoryUtil.createGuiItem(Material.RED_STAINED_GLASS_PANE, messageManager.getMessage("button_confirm_create"), false, messageManager.getMessage("lore_confirm_create_missing_details", "%details%", missingDetails)));
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        player.openInventory(gui);
    }

    public void openAuctionInfoGui(Player player, AuctionItem auctionItem) {
        openInventoriesPage.remove(player.getUniqueId());
        String title = messageManager.getMessage("auction_info_gui_title");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        ItemStack displayItem = auctionItem.getItemStack().clone();
        ItemMeta currentMeta = displayItem.getItemMeta();
        if (currentMeta != null && !currentMeta.hasDisplayName()) {
            currentMeta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_type%", displayItem.getType().name().replace("_", " ").toLowerCase()));
            displayItem.setItemMeta(currentMeta);
        }
        gui.setItem(4, displayItem);

        List<String> bidLore = new ArrayList<>();
        bidLore.add(messageManager.getMessage("lore_place_bid_current", "%bid%", String.format("%.2f", auctionItem.getCurrentBid()), "%currency%", configManager.getCurrencySymbol()));
        double nextBid = auctionItem.getCurrentBid() + configManager.getMinBidIncrementAmount();
        if (auctionItem.getHighestBidderUUID() == null) nextBid = auctionItem.getStartPrice(); // First bid must be at least start price
        bidLore.add(messageManager.getMessage("lore_place_bid_your_bid", "%next_bid%", String.format("%.2f", nextBid), "%currency%", configManager.getCurrencySymbol()));
        gui.setItem(11, InventoryUtil.createGuiItem(Material.GOLD_NUGGET, messageManager.getMessage("button_place_bid"), bidLore));

        List<String> infoLore = new ArrayList<>();
        infoLore.add(messageManager.getMessage("item_lore_seller", "%seller%", auctionItem.getSellerName()));
        infoLore.add(messageManager.getMessage("item_lore_start_price", "%price%", String.format("%.2f", auctionItem.getStartPrice()), "%currency%", configManager.getCurrencySymbol()));
        if (auctionItem.getHighestBidderName() != null) {
            infoLore.add(messageManager.getMessage("item_lore_current_bid", "%bid%", String.format("%.2f", auctionItem.getCurrentBid()), "%currency%", configManager.getCurrencySymbol(), "%bidder%", auctionItem.getHighestBidderName()));
        } else {
            infoLore.add(messageManager.getMessage("item_lore_no_bids"));
        }
        infoLore.add(messageManager.getMessage("item_lore_time_left", "%time%", InventoryUtil.formatTime(auctionItem.getStartTime() + auctionItem.getDuration() - System.currentTimeMillis())));
        infoLore.add(messageManager.getMessage("item_lore_id", "%id%", String.valueOf(auctionItem.getId()))); // Add hidden ID here too for consistency with click listener
        gui.setItem(13, InventoryUtil.createGuiItem(Material.PAPER, messageManager.getMessage("info_item_title"), infoLore));

        if (configManager.isBuyNowAllowed() && auctionItem.getBuyNowPrice() > 0) {
            gui.setItem(15, InventoryUtil.createGuiItem(Material.DIAMOND, messageManager.getMessage("button_buy_now"), true, messageManager.getMessage("lore_buy_now_price", "%price%", String.format("%.2f", auctionItem.getBuyNowPrice()), "%currency%", configManager.getCurrencySymbol())));
        } else {
            gui.setItem(15, InventoryUtil.createGuiItem(Material.BARRIER, messageManager.getMessage("item_lore_buy_now_not_available")));
        }

        gui.setItem(22, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_back_to_auctions")));

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        player.openInventory(gui);
    }

    public void openMyAuctionsGui(Player player, int page) {
        openInventoriesPage.put(player.getUniqueId(), page);
        List<AuctionItem> myAuctions;
        try {
            myAuctions = new ArrayList<>(plugin.getDatabaseManager().getAuctionsByPlayer(player.getUniqueId().toString()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading 'My Auctions' for " + player.getName(), e);
            messageManager.sendMessage(player, "internal_error_my_auctions"); // New message key
            myAuctions = new ArrayList<>();
        }

        myAuctions.sort((a1, a2) -> {
            if (a1.getStatus() == AuctionStatus.ACTIVE && a2.getStatus() != AuctionStatus.ACTIVE) return -1;
            if (a1.getStatus() != AuctionStatus.ACTIVE && a2.getStatus() == AuctionStatus.ACTIVE) return 1;
            return Long.compare(a2.getStartTime() + a2.getDuration(), a1.getStartTime() + a1.getDuration());
        });

        int itemsPerPage = 45;
        int totalItems = myAuctions.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(1, Math.min(page, maxPages));

        String title = messageManager.getMessage("my_auctions_gui_title", "%page%", String.valueOf(page), "%max_pages%", String.valueOf(maxPages));
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem auction = myAuctions.get(i);
            ItemStack displayItem = auction.getItemStack().clone();
            ItemMeta meta = displayItem.getItemMeta();
             if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

            List<String> lore = new ArrayList<>();
            lore.add(messageManager.getMessage("item_lore_status", "%status%", auction.getStatus().toString())); // Consider localizing status names
            lore.add(messageManager.getMessage("item_lore_start_price", "%price%", String.format("%.2f", auction.getStartPrice()), "%currency%", configManager.getCurrencySymbol()));

            if (auction.getStatus() == AuctionStatus.SOLD_VIA_BID || auction.getStatus() == AuctionStatus.SOLD_VIA_BUYOUT) {
                 lore.add(messageManager.getMessage("lore_my_auction_status_sold",
                    "%buyer%", auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "N/A",
                    "%price%", String.format("%.2f", auction.getCurrentBid()),
                    "%currency%", configManager.getCurrencySymbol()));
            } else if (auction.getHighestBidderName() != null) {
                 lore.add(messageManager.getMessage("item_lore_current_bid", "%bid%", String.format("%.2f", auction.getCurrentBid()), "%currency%", configManager.getCurrencySymbol(), "%bidder%", auction.getHighestBidderName()));
            }
            if (configManager.isBuyNowAllowed() && auction.getBuyNowPrice() > 0) {
                lore.add(messageManager.getMessage("item_lore_buy_now_price", "%price%", String.format("%.2f", auction.getBuyNowPrice()), "%currency%", configManager.getCurrencySymbol()));
            }
            if (auction.getStatus() == AuctionStatus.ACTIVE) {
                lore.add(messageManager.getMessage("item_lore_time_left", "%time%", InventoryUtil.formatTime(auction.getStartTime() + auction.getDuration() - System.currentTimeMillis())));
                lore.add(" ");
                lore.add(messageManager.getMessage("lore_my_auction_click_to_cancel"));
            } else if (auction.getStatus() == AuctionStatus.EXPIRED && auction.getHighestBidderUUID() == null) {
                lore.add(messageManager.getMessage("lore_my_auction_status_expired_unclaimed")); // Assumes it goes to claim
            } else if (auction.getStatus() == AuctionStatus.CANCELLED) {
                 lore.add(messageManager.getMessage("lore_my_auction_status_cancelled"));
            }
            lore.add(messageManager.getMessage("item_lore_id", "%id%", String.valueOf(auction.getId())));

            if (!meta.hasDisplayName()) meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_type%", auction.getItemStack().getType().name().replace("_", " ").toLowerCase()));
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
            gui.setItem(i - startIndex, displayItem);
        }

        if (page > 1) gui.setItem(45, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_previous_page")));
        else gui.setItem(45, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        gui.setItem(48, InventoryUtil.createGuiItem(Material.ENDER_PEARL, messageManager.getMessage("button_back_to_main_auctions"))); // New message key
        gui.setItem(49, InventoryUtil.createGuiItem(Material.SUNFLOWER, messageManager.getMessage("button_refresh")));

        if (page < maxPages) gui.setItem(53, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_next_page")));
        else gui.setItem(53, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        for (int j = 45; j < 54; j++) {
            if (gui.getItem(j) == null) gui.setItem(j, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        player.openInventory(gui);
    }

    public void openAuctionHistoryGui(Player player, int page) {
        openInventoriesPage.put(player.getUniqueId(), page);
        List<AuctionItem> pastAuctions;
        try {
            pastAuctions = plugin.getDatabaseManager().getAuctionsByPlayer(player.getUniqueId().toString()).stream()
                .filter(auc -> auc.getStatus() != AuctionStatus.ACTIVE) // Simple filter for now
                .sorted((a1, a2) -> Long.compare(a2.getStartTime() + a2.getDuration(), a1.getStartTime() + a1.getDuration()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading 'Auction History' for " + player.getName(), e);
            messageManager.sendMessage(player, "internal_error_history"); // New message key
            pastAuctions = new ArrayList<>();
        }

        int itemsPerPage = 45;
        int totalItems = pastAuctions.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(1, Math.min(page, maxPages));

        String title = messageManager.getMessage("auction_history_gui_title", "%page%", String.valueOf(page), "%max_pages%", String.valueOf(maxPages));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(configManager.getDateFormat());

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem auction = pastAuctions.get(i);
            ItemStack displayItem = auction.getItemStack().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

            List<String> lore = new ArrayList<>();
            lore.add(messageManager.getMessage("item_lore_status", "%status%", auction.getStatus().toString()));
            lore.add(messageManager.getMessage("item_lore_seller", "%seller%", auction.getSellerName()));
            if (auction.getStatus() == AuctionStatus.SOLD_VIA_BID || auction.getStatus() == AuctionStatus.SOLD_VIA_BUYOUT) {
                lore.add(messageManager.getMessage("lore_history_buyer", "%buyer%", auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "N/A")); // New key
                lore.add(messageManager.getMessage("lore_history_final_price", "%price%", String.format("%.2f", auction.getCurrentBid()), "%currency%", configManager.getCurrencySymbol())); // New key
            } else if (auction.getStatus() == AuctionStatus.EXPIRED) {
                lore.add(messageManager.getMessage("lore_history_expired_no_sale")); // New key
            } else if (auction.getStatus() == AuctionStatus.CANCELLED) {
                lore.add(messageManager.getMessage("lore_history_cancelled")); // New key
            }
            lore.add(messageManager.getMessage("lore_history_item_ended_date", "%date%", sdf.format(new java.util.Date(auction.getStartTime() + auction.getDuration()))));
            lore.add(messageManager.getMessage("item_lore_id", "%id%", String.valueOf(auction.getId())));

            if (!meta.hasDisplayName()) meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_type%", auction.getItemStack().getType().name().replace("_", " ").toLowerCase()));
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
            gui.setItem(i - startIndex, displayItem);
        }

        if (page > 1) gui.setItem(45, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_previous_page")));
        else gui.setItem(45, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        gui.setItem(48, InventoryUtil.createGuiItem(Material.ENDER_PEARL, messageManager.getMessage("button_back_to_main_auctions")));
        gui.setItem(49, InventoryUtil.createGuiItem(Material.SUNFLOWER, messageManager.getMessage("button_refresh")));

        if (page < maxPages) gui.setItem(53, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_next_page")));
        else gui.setItem(53, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        for (int j = 45; j < 54; j++) {
            if (gui.getItem(j) == null) gui.setItem(j, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        player.openInventory(gui);
    }

    public int getPlayerCurrentPage(UUID playerUUID) {
        return openInventoriesPage.getOrDefault(playerUUID, 1);
    }

    public void removePlayerPage(UUID playerUUID) {
        openInventoriesPage.remove(playerUUID);
    }

    // Methods for Create Auction GUI state
    public void setItemToAuction(UUID playerUUID, ItemStack item) {
        if (item == null) playerItemToAuction.remove(playerUUID);
        else playerItemToAuction.put(playerUUID, item);
    }
    public ItemStack getItemToAuction(UUID playerUUID) { return playerItemToAuction.get(playerUUID); }

    public void setAuctionDuration(UUID playerUUID, long duration) { playerAuctionDuration.put(playerUUID, duration); }
    public Long getAuctionDuration(UUID playerUUID) { return playerAuctionDuration.get(playerUUID); }

    public void setAuctionStartPrice(UUID playerUUID, double price) { playerAuctionStartPrice.put(playerUUID, price); }
    public Double getAuctionStartPrice(UUID playerUUID) { return playerAuctionStartPrice.get(playerUUID); }

    public void setAuctionBuyNowPrice(UUID playerUUID, Double price) { // Double (nullable) for removal
        if (price == null || price <=0) playerAuctionBuyNowPrice.remove(playerUUID);
        else playerAuctionBuyNowPrice.put(playerUUID, price);
    }
    public Double getAuctionBuyNowPrice(UUID playerUUID) { return playerAuctionBuyNowPrice.get(playerUUID); }

    public void clearCreateAuctionData(UUID playerUUID) {
        playerItemToAuction.remove(playerUUID);
        playerAuctionDuration.remove(playerUUID);
        playerAuctionStartPrice.remove(playerUUID);
        playerAuctionBuyNowPrice.remove(playerUUID);
    }

    public void refreshOpenAuctionGuis(AuctionItem affectedAuction, boolean forOwnerOnly, Player actionTaker) {
        String mainGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("main_gui_title").split("\\(")[0]).trim();
        String myAuctionsBaseTitle = messageManager.stripColors(messageManager.getRaw("my_auctions_gui_title").split("\\(")[0]).trim();
        String infoGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("auction_info_gui_title")); // No page numbers

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
                continue;
            }
            String openInventoryTitle = messageManager.stripColors(player.getOpenInventory().getTitle());

            if (openInventoryTitle.startsWith(mainGuiBaseTitle)) {
                if (!forOwnerOnly || player.equals(actionTaker)) { // Refresh main for everyone unless specifically for owner
                    openMainAuctionGui(player, getPlayerCurrentPage(player.getUniqueId()));
                }
            } else if (openInventoryTitle.startsWith(myAuctionsBaseTitle)) {
                if (affectedAuction != null && player.getUniqueId().toString().equals(affectedAuction.getSellerUUID())) {
                    openMyAuctionsGui(player, getPlayerCurrentPage(player.getUniqueId()));
                } else if (!forOwnerOnly && actionTaker.equals(player)) { // e.g. player created an auction, refresh their "my auctions"
                     openMyAuctionsGui(player, getPlayerCurrentPage(player.getUniqueId()));
                }
            } else if (openInventoryTitle.equals(infoGuiBaseTitle)) {
                // Check if the info GUI is for the affected auction
                // This requires parsing the ID from the GUI content (e.g., from a lore line on an item)
                // For simplicity, if an auction is affected, and a player has *any* info GUI open,
                // we could close it or refresh their main GUI. A more targeted refresh is complex.
                // Let's try to refresh if the item ID matches.
                Inventory openInv = player.getOpenInventory().getTopInventory();
                ItemStack itemInSlot4 = openInv.getItem(4); // Expected slot for auction item in info GUI
                if (itemInSlot4 != null && itemInSlot4.hasItemMeta() && itemInSlot4.getItemMeta().hasLore()) {
                    List<String> lore = itemInSlot4.getItemMeta().getLore();
                    String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith("ID:")).findFirst().orElse(null);
                    if (idStringLore != null) {
                        try {
                            int openAuctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1)));
                            if (affectedAuction != null && openAuctionId == affectedAuction.getId()) {
                                if (affectedAuction.getStatus() == AuctionStatus.ACTIVE) {
                                    openAuctionInfoGui(player, affectedAuction); // Refresh with new data
                                } else {
                                    player.closeInventory(); // Auction no longer active, close info
                                    messageManager.sendMessage(player, "auction_ended_info_closed");
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Not a valid auction ID in lore, do nothing
                        }
                    }
                }
            }
        }
    }
}
