package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.logging.Level; // Added import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// import java.util.logging.Level; // Not used currently, can be removed
import java.util.stream.Collectors;

public class GUIManager {

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<UUID, Integer> openInventoriesPage;
    private final Map<UUID, ItemStack> playerItemToAuction = new HashMap<>();
    private final Map<UUID, Long> playerAuctionDuration = new HashMap<>();
    private final Map<UUID, Double> playerAuctionStartPrice = new HashMap<>();
    private final Map<UUID, Double> playerAuctionBuyNowPrice = new HashMap<>();
    private final Map<UUID, Integer> playerViewingAuctionId = new HashMap<>();
    private final Map<UUID, Integer> playerPendingBidAuctionId = new HashMap<>();

    private String formatDuration(long millis) {
        if (millis < 0) return messageManager.getMessage("time_ended", "Error");
        if (millis == 0) return messageManager.getMessage("time_ended", "Finalizada");
        long seconds = millis / 1000;
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        String result = sb.toString().trim();
        return result.isEmpty() ? messageManager.getMessage("time_ended", "Finalizada") : result;
    }

    public GUIManager(AetherAuctions plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.openInventoriesPage = new HashMap<>();
    }

    public void openMainAuctionGui_DEPRECATED(Player player, int page) {
        plugin.getLogger().info("Attempted to open DEPRECATED main auction GUI for " + player.getName());
    }

    public String getNewMainAuctionGuiTitlePrefix() {
        return messageManager.getMessage("new_main_gui_title_prefix");
    }

    public String getNewMainAuctionGuiFullTitle(Player player, int page, int totalPages) {
         String prefix = messageManager.getMessage("new_main_gui_title_prefix");
         if (totalPages > 1) {
             return prefix + " &7(Pág. " + (page + 1) + "/" + totalPages + ")";
         }
         return prefix;
    }

    public String getAuctionDetailsGuiTitle() {
        return messageManager.getMessage("auction_details_gui_title");
    }

    public void openNewMainAuctionGui(Player player, int page) {
        List<AuctionItem> activeAuctions = new ArrayList<>(auctionManager.getActiveAuctionsMap().values().stream()
                .filter(auc -> auc.getStatus() == AuctionStatus.ACTIVE)
                .sorted((a1, a2) -> Long.compare(a1.getStartTime() + a1.getDuration(), a2.getStartTime() + a2.getDuration()))
                .collect(Collectors.toList()));
        int itemsPerPage = 45;
        int totalItems = activeAuctions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        openInventoriesPage.put(player.getUniqueId(), page);
        String title = getNewMainAuctionGuiFullTitle(player, page, totalPages);
        Inventory gui = Bukkit.createInventory(null, 54, title);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem auction = activeAuctions.get(i);
            ItemStack displayItem = auction.getItemStack().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
            String originalItemName = displayItem.hasItemMeta() && displayItem.getItemMeta().hasDisplayName()
                                    ? displayItem.getItemMeta().getDisplayName()
                                    : auction.getItemStack().getType().name().replace("_", " ");
            meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_name%", originalItemName));
            List<String> lore = new ArrayList<>();
            lore.add(messageManager.getMessage("lore_auction_id", "%id%", String.valueOf(auction.getId())));
            lore.add(messageManager.getMessage("lore_seller", "%player_name%", auction.getSellerName()));
            if (configManager.isBuyNowAllowed() && auction.getBuyNowPrice() > 0) {
                lore.add(messageManager.getMessage("lore_buyout_price", "%price%", String.format("%.2f", auction.getBuyNowPrice()), "%currency%", configManager.getCurrencySymbol()));
            }
            lore.add(messageManager.getMessage("lore_minimum_bid", "%price%", String.format("%.2f", auction.getCurrentBid()), "%currency%", configManager.getCurrencySymbol()));
            lore.add(messageManager.getMessage("lore_time_remaining", "%time%", formatDuration(auction.getStartTime() + auction.getDuration() - System.currentTimeMillis())));
            lore.add(" ");
            lore.add(messageManager.getMessage("lore_instruction_bid"));
            lore.add(messageManager.getMessage("lore_instruction_details"));
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
            gui.setItem(i - startIndex, displayItem);
        }
        ItemStack placeholder = InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) gui.setItem(i, placeholder);
        if (page > 0) gui.setItem(48, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_previous_page")));
        gui.setItem(49, InventoryUtil.createGuiItem(Material.BARRIER, messageManager.getMessage("button_close_gui")));
        if (page < totalPages - 1) gui.setItem(50, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_next_page")));
        player.openInventory(gui);
    }

    public void openAuctionInfoGui(Player player, AuctionItem auctionItem) {
        // openInventoriesPage.remove(player.getUniqueId()); // Removed to preserve main GUI page history
        playerViewingAuctionId.put(player.getUniqueId(), auctionItem.getId());

        String title = getAuctionDetailsGuiTitle();
        Inventory gui = Bukkit.createInventory(null, 36, title);

        ItemStack displayItem = auctionItem.getItemStack().clone();
        ItemMeta displayMeta = displayItem.getItemMeta();
        if (displayMeta != null) {
            String originalItemName = displayMeta.hasDisplayName() ? displayMeta.getDisplayName() : displayItem.getType().name().replace("_", " ");
            displayMeta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_name%", originalItemName));
            displayItem.setItemMeta(displayMeta);
        }
        gui.setItem(4, displayItem);

        gui.setItem(19, InventoryUtil.createGuiItem(Material.PAPER, messageManager.getMessage("details_auction_id", "%id%", String.valueOf(auctionItem.getId()))));
        gui.setItem(20, InventoryUtil.createGuiItem(Material.PLAYER_HEAD, messageManager.getMessage("details_seller", "%name%", auctionItem.getSellerName())));
        gui.setItem(21, InventoryUtil.createGuiItem(Material.GOLD_NUGGET, messageManager.getMessage("details_current_bid", "%price%", plugin.getEconomy().format(auctionItem.getCurrentBid()))));
        if (auctionItem.getBuyNowPrice() > 0 && auctionItem.getStatus() == AuctionStatus.ACTIVE) {
            gui.setItem(22, InventoryUtil.createGuiItem(Material.GOLD_INGOT, messageManager.getMessage("details_buyout_price", "%price%", plugin.getEconomy().format(auctionItem.getBuyNowPrice()))));
        } else {
            gui.setItem(22, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        gui.setItem(23, InventoryUtil.createGuiItem(Material.CLOCK, messageManager.getMessage("details_time_remaining", "%time%", formatDuration(auctionItem.getStartTime() + auctionItem.getDuration() - System.currentTimeMillis()))));

        gui.setItem(30, InventoryUtil.createGuiItem(Material.GREEN_WOOL, messageManager.getMessage("button_bid")));
        if (auctionItem.getBuyNowPrice() > 0 && auctionItem.getStatus() == AuctionStatus.ACTIVE) {
            gui.setItem(31, InventoryUtil.createGuiItem(Material.EMERALD_BLOCK, messageManager.getMessage("button_buy_now")));
        } else {
            gui.setItem(31, InventoryUtil.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        gui.setItem(32, InventoryUtil.createGuiItem(Material.RED_WOOL, messageManager.getMessage("button_back_to_main_auctions")));

        ItemStack placeholder = InventoryUtil.createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, placeholder);
            }
        }
        player.openInventory(gui);
    }

    public Integer getPlayerViewingAuctionId(UUID playerUUID) {
        return playerViewingAuctionId.get(playerUUID);
    }

    public void removePlayerViewingAuctionId(UUID playerUUID) {
        playerViewingAuctionId.remove(playerUUID);
    }

    public void openCreateAuctionGui(Player player) {
        openInventoriesPage.remove(player.getUniqueId());
        String title = messageManager.getMessage("auction_create_gui_title");
        Inventory gui = Bukkit.createInventory(null, 27, title);
        String noValue = messageManager.getRaw("status_not_set");
        String selectedDurationStr = playerAuctionDuration.containsKey(player.getUniqueId()) ? formatDuration(playerAuctionDuration.get(player.getUniqueId())) : noValue;
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
            if(itemToAuction == null) missingDetailsList.add(messageManager.getRaw("missing_detail_item"));
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

    public void openMyAuctionsGui(Player player, int page) {
        openInventoriesPage.put(player.getUniqueId(), page);
        List<AuctionItem> myAuctions;
        try {
            myAuctions = new ArrayList<>(plugin.getDatabaseManager().getAuctionsByPlayer(player.getUniqueId().toString()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading 'My Auctions' for " + player.getName(), e);
            messageManager.sendMessage(player, "internal_error_my_auctions");
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
            String originalItemNameMy = displayItem.hasItemMeta() && displayItem.getItemMeta().hasDisplayName()
                                    ? displayItem.getItemMeta().getDisplayName()
                                    : auction.getItemStack().getType().name().replace("_", " ");
            meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_name%", originalItemNameMy));
            List<String> lore = new ArrayList<>();
            lore.add(messageManager.getMessage("item_lore_status", "%status%", auction.getStatus().toString()));
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
                lore.add(messageManager.getMessage("lore_time_remaining", "%time%", formatDuration(auction.getStartTime() + auction.getDuration() - System.currentTimeMillis())));
                lore.add(" ");
                lore.add(messageManager.getMessage("lore_my_auction_click_to_cancel"));
            } else if (auction.getStatus() == AuctionStatus.EXPIRED && auction.getHighestBidderUUID() == null) {
                lore.add(messageManager.getMessage("lore_my_auction_status_expired_unclaimed"));
            } else if (auction.getStatus() == AuctionStatus.CANCELLED) {
                 lore.add(messageManager.getMessage("lore_my_auction_status_cancelled"));
            }
            lore.add(messageManager.getMessage("item_lore_id", "%id%", String.valueOf(auction.getId())));
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

    public void openAuctionHistoryGui(Player player, int page) {
        openInventoriesPage.put(player.getUniqueId(), page);
        List<AuctionItem> pastAuctions;
        try {
            pastAuctions = plugin.getDatabaseManager().getAuctionsByPlayer(player.getUniqueId().toString()).stream()
                .filter(auc -> auc.getStatus() != AuctionStatus.ACTIVE)
                .sorted((a1, a2) -> Long.compare(a2.getStartTime() + a2.getDuration(), a1.getStartTime() + a1.getDuration()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading 'Auction History' for " + player.getName(), e);
            messageManager.sendMessage(player, "internal_error_history");
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
            String originalItemNameHist = displayItem.hasItemMeta() && displayItem.getItemMeta().hasDisplayName()
                                    ? displayItem.getItemMeta().getDisplayName()
                                    : auction.getItemStack().getType().name().replace("_", " ");
            meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_name%", originalItemNameHist));
            List<String> lore = new ArrayList<>();
            lore.add(messageManager.getMessage("item_lore_status", "%status%", auction.getStatus().toString()));
            lore.add(messageManager.getMessage("lore_seller", "%player_name%", auction.getSellerName()));
            if (auction.getStatus() == AuctionStatus.SOLD_VIA_BID || auction.getStatus() == AuctionStatus.SOLD_VIA_BUYOUT) {
                lore.add(messageManager.getMessage("lore_history_buyer", "%buyer%", auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "N/A"));
                lore.add(messageManager.getMessage("lore_history_final_price", "%price%", String.format("%.2f", auction.getCurrentBid()), "%currency%", configManager.getCurrencySymbol()));
            } else if (auction.getStatus() == AuctionStatus.EXPIRED) {
                lore.add(messageManager.getMessage("lore_history_expired_no_sale"));
            } else if (auction.getStatus() == AuctionStatus.CANCELLED) {
                lore.add(messageManager.getMessage("lore_history_cancelled"));
            }
            lore.add(messageManager.getMessage("lore_history_item_ended_date", "%date%", sdf.format(new java.util.Date(auction.getStartTime() + auction.getDuration()))));
            lore.add(messageManager.getMessage("item_lore_id", "%id%", String.valueOf(auction.getId())));
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
        return openInventoriesPage.getOrDefault(playerUUID, 0);
    }

    public void removePlayerPage(UUID playerUUID) {
        openInventoriesPage.remove(playerUUID);
    }

    public void setItemToAuction(UUID playerUUID, ItemStack item) {
        if (item == null) playerItemToAuction.remove(playerUUID);
        else playerItemToAuction.put(playerUUID, item);
    }
    public ItemStack getItemToAuction(UUID playerUUID) { return playerItemToAuction.get(playerUUID); }

    public void setAuctionDuration(UUID playerUUID, long duration) { playerAuctionDuration.put(playerUUID, duration); }
    public Long getAuctionDuration(UUID playerUUID) { return playerAuctionDuration.get(playerUUID); }

    public void setAuctionStartPrice(UUID playerUUID, double price) { playerAuctionStartPrice.put(playerUUID, price); }
    public Double getAuctionStartPrice(UUID playerUUID) { return playerAuctionStartPrice.get(playerUUID); }

    public void setAuctionBuyNowPrice(UUID playerUUID, Double price) {
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
        String oldMainGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("main_gui_title").split("\\(")[0]).trim();
        String newMainGuiActualTitlePrefix = getNewMainAuctionGuiTitlePrefix();
        String myAuctionsBaseTitle = messageManager.stripColors(messageManager.getRaw("my_auctions_gui_title").split("\\(")[0]).trim();
        String infoGuiBaseTitle = messageManager.stripColors(messageManager.getRaw("auction_info_gui_title"));  // Old info GUI title
        String auctionDetailsGuiTitle = getAuctionDetailsGuiTitle(); // New details GUI title

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
                continue;
            }
            String openInventoryTitle = messageManager.stripColors(player.getOpenInventory().getTitle());

            if (openInventoryTitle.startsWith(newMainGuiActualTitlePrefix)) {
                if (!forOwnerOnly || (actionTaker != null && player.getUniqueId().equals(actionTaker.getUniqueId())) || (affectedAuction != null && player.getUniqueId().toString().equals(affectedAuction.getSellerUUID())) ) {
                    openNewMainAuctionGui(player, getPlayerCurrentPage(player.getUniqueId()));
                }
            } else if (openInventoryTitle.startsWith(oldMainGuiBaseTitle)) {
                 if (!forOwnerOnly || (actionTaker != null && player.getUniqueId().equals(actionTaker.getUniqueId())) || (affectedAuction != null && player.getUniqueId().toString().equals(affectedAuction.getSellerUUID())) ) {
                    openNewMainAuctionGui(player, 0);
                }
            }

            else if (openInventoryTitle.startsWith(myAuctionsBaseTitle)) {
                if (affectedAuction != null && player.getUniqueId().toString().equals(affectedAuction.getSellerUUID())) {
                    openMyAuctionsGui(player, getPlayerCurrentPage(player.getUniqueId()));
                } else if (!forOwnerOnly && actionTaker != null && actionTaker.equals(player)) {
                     openMyAuctionsGui(player, getPlayerCurrentPage(player.getUniqueId()));
                }
            } else if (openInventoryTitle.equals(infoGuiBaseTitle) || openInventoryTitle.equals(auctionDetailsGuiTitle)) { // Check both old and new info titles
                Inventory openInv = player.getOpenInventory().getTopInventory();
                ItemStack itemInSlot = openInv.getItem(4);
                if (itemInSlot != null && itemInSlot.hasItemMeta() && itemInSlot.getItemMeta().hasLore()) {
                    List<String> lore = itemInSlot.getItemMeta().getLore();
                    String idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith("ID de Subasta:")).findFirst().orElse(null);
                    if (idStringLore == null) {
                         idStringLore = lore.stream().filter(s -> messageManager.stripColors(s).startsWith(messageManager.stripColors(messageManager.getRaw("lore_auction_id").split(":")[0] + ":"))).findFirst().orElse(null);
                    }

                    if (idStringLore != null) {
                        try {
                            int openAuctionId = Integer.parseInt(messageManager.stripColors(idStringLore.substring(idStringLore.indexOf(":") + 1).trim()));
                            if (affectedAuction != null && openAuctionId == affectedAuction.getId()) {
                                if (affectedAuction.getStatus() == AuctionStatus.ACTIVE) {
                                    openAuctionInfoGui(player, affectedAuction);
                                } else {
                                    player.closeInventory();
                                    messageManager.sendMessage(player, "auction_ended_info_closed");
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Not a valid auction ID in lore
                        }
                    }
                }
            }
        }
    }

    // Methods for player pending bid state
    public void setPlayerPendingBid(UUID playerId, int auctionId) {
        playerPendingBidAuctionId.put(playerId, auctionId);
    }

    public boolean isPlayerPendingBid(UUID playerId) {
        return playerPendingBidAuctionId.containsKey(playerId);
    }

    public Integer getAndRemovePlayerPendingBid(UUID playerId) { // Return Integer to handle if not found (null)
        return playerPendingBidAuctionId.remove(playerId);
    }
}
