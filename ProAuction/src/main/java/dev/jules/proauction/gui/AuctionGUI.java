package dev.jules.proauction.gui;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.model.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Added import
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AuctionGUI implements InventoryHolder {

    private final ProAuction plugin;
    private final Map<UUID, Integer> playerOpenAuctionPage = new HashMap<>(); // Tracks current page for main auction list
    // Map<PlayerUUID, Map<Slot, AuctionUUID>> for main list item clicks
    private final Map<UUID, Map<Integer, UUID>> playerGUIAuctionSlots = new HashMap<>();
    // Tracks the auction a player is currently viewing in a bid confirmation GUI
    private final Map<UUID, UUID> playerViewingBidConfirmation = new HashMap<>();
    // Tracks players expected to make a bid via chat
    private final Map<UUID, UUID> playerPendingChatBid = new HashMap<>();

    public static final int AUCTIONS_PER_PAGE = 45; // 5 rows of 9 slots for auctions
    // Title prefixes and button names will be handled by LanguageManager

    public AuctionGUI(ProAuction plugin) {
        this.plugin = plugin;
    }

    public void openMainAuctionPage(Player player, int pageNumber) {
        List<Auction> activeAuctions = new ArrayList<>(plugin.getActiveAuctions().values());
        activeAuctions.removeIf(auction -> !auction.isActive()); // Ensure only active ones are shown

        int totalAuctions = activeAuctions.size();
        int totalPages = (int) Math.ceil((double) totalAuctions / AUCTIONS_PER_PAGE);
        if (totalPages == 0) totalPages = 1; // Always at least one page, even if empty

        pageNumber = Math.max(0, Math.min(pageNumber, totalPages - 1)); // Clamp page number

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("page", String.valueOf(pageNumber + 1));
        titlePlaceholders.put("total_pages", String.valueOf(totalPages));
        Inventory gui = Bukkit.createInventory(this, 54, plugin.getLanguageManager().getMessage("gui.title.main", titlePlaceholders));

        Map<Integer, UUID> auctionSlots = new HashMap<>();
        playerViewingBidConfirmation.remove(player.getUniqueId()); // Clear bid confirmation state
        int startIndex = pageNumber * AUCTIONS_PER_PAGE;
        for (int i = 0; i < AUCTIONS_PER_PAGE; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex < totalAuctions) {
                Auction auction = activeAuctions.get(auctionIndex);
                gui.setItem(i, this.createDisplayItem(auction)); // Explicit this. for clarity, though not strictly needed
                auctionSlots.put(i, auction.getAuctionId());
            } else {
                break; // No more auctions to display on this page
            }
        }

        // Navigation items
        if (pageNumber > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.prevpage"));
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage); // Bottom left
        }

        if (pageNumber < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.nextpage"));
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage); // Bottom right
        }

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.close"));
        closeButton.setItemMeta(closeMeta);
        gui.setItem(49, closeButton); // Bottom middle

        playerOpenAuctionPage.put(player.getUniqueId(), pageNumber);
        playerGUIAuctionSlots.put(player.getUniqueId(), auctionSlots);
        player.openInventory(gui);
    }

    public void openBidConfirmationGUI(Player player, Auction auction) {
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, "gui.status.auctionunavailable");
            openMainAuctionPage(player, playerOpenAuctionPage.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        Inventory bidGui = Bukkit.createInventory(this, 27, plugin.getLanguageManager().getMessage("gui.title.bidconfirm", "item_name", getItemNamePlain(auction.getItem())));

        // Display Item (Slot 4 - center of top row)
        bidGui.setItem(4, auction.getItem().clone());

        // Information Items
        ItemMeta tempMeta;
        Map<String, String> placeholders = new HashMap<>();

        ItemStack sellerInfo = new ItemStack(Material.PLAYER_HEAD);
        tempMeta = sellerInfo.getItemMeta();
        placeholders.clear();
        placeholders.put("name", auction.getSellerName());
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.bidconfirm.sellerinfo", placeholders));
        sellerInfo.setItemMeta(tempMeta);
        bidGui.setItem(10, sellerInfo);

        ItemStack currentBidInfo = new ItemStack(Material.GOLD_NUGGET);
        tempMeta = currentBidInfo.getItemMeta();
        placeholders.clear();
        placeholders.put("amount", ProAuction.format(auction.getCurrentBid()));
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.bidconfirm.currentbidinfo", placeholders));
        List<String> bidLore = new ArrayList<>();
        if (auction.getHighestBidderUuid() != null) {
            placeholders.clear();
            placeholders.put("name", auction.getHighestBidderName());
            bidLore.add(plugin.getLanguageManager().getMessage("gui.bidconfirm.currentbidinfo.by", placeholders));
        } else {
            bidLore.add(plugin.getLanguageManager().getMessage("gui.bidconfirm.currentbidinfo.nobids"));
        }
        tempMeta.setLore(bidLore);
        currentBidInfo.setItemMeta(tempMeta);
        bidGui.setItem(11, currentBidInfo);

        double nextMinBid = auction.getCurrentBid() == auction.getStartingPrice() && auction.getHighestBidderUuid() == null ? auction.getStartingPrice() : auction.getCurrentBid() + auction.getMinIncrement();
        ItemStack minNextBidInfo = new ItemStack(Material.COMPARATOR);
        tempMeta = minNextBidInfo.getItemMeta();
        placeholders.clear();
        placeholders.put("amount", ProAuction.format(nextMinBid));
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.bidconfirm.minnextbidinfo", placeholders));
        minNextBidInfo.setItemMeta(tempMeta);
        bidGui.setItem(12, minNextBidInfo);

        ItemStack timeInfo = new ItemStack(Material.CLOCK);
        tempMeta = timeInfo.getItemMeta();
        placeholders.clear();
        placeholders.put("time", formatTimeRemaining(auction.getEndTimeMillis() - System.currentTimeMillis()));
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.bidconfirm.timeinfo", placeholders));
        timeInfo.setItemMeta(tempMeta);
        bidGui.setItem(13, timeInfo);

        ItemStack balanceInfo = new ItemStack(Material.EMERALD);
        tempMeta = balanceInfo.getItemMeta();
        placeholders.clear();
        placeholders.put("amount", ProAuction.format(ProAuction.getEconomy().getBalance(player)));
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.bidconfirm.balanceinfo", placeholders));
        balanceInfo.setItemMeta(tempMeta);
        bidGui.setItem(14, balanceInfo);

        // Action Buttons
        ItemStack bidMinimum = new ItemStack(Material.GREEN_WOOL);
        tempMeta = bidMinimum.getItemMeta();
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.bidminimum"));
        placeholders.clear();
        placeholders.put("amount", ProAuction.format(nextMinBid));
        tempMeta.setLore(List.of(plugin.getLanguageManager().getMessage("gui.bidconfirm.bidminimum.lore", placeholders)));
        bidMinimum.setItemMeta(tempMeta);
        bidGui.setItem(20, bidMinimum);

        ItemStack customBid = new ItemStack(Material.GOLD_INGOT);
        tempMeta = customBid.getItemMeta();
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.bidcustom"));
        tempMeta.setLore(List.of(plugin.getLanguageManager().getMessage("gui.bidconfirm.custombid.lore")));
        customBid.setItemMeta(tempMeta);
        bidGui.setItem(22, customBid);

        if (auction.getBuyNowPrice() > 0) {
            ItemStack buyNowButton = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta buyNowMeta = buyNowButton.getItemMeta();
            List<String> buyNowLoreList = new ArrayList<>();
            buyNowMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.buynow"));

            placeholders.clear();
            placeholders.put("amount", ProAuction.format(auction.getBuyNowPrice()));
            buyNowLoreList.add(plugin.getLanguageManager().getMessage("gui.bidconfirm.buynow.loreprice", placeholders));

            double salesTaxPercentage = plugin.getSalesTaxPercentage();
            if (salesTaxPercentage > 0) {
                 placeholders.clear();
                 placeholders.put("tax_percentage", String.valueOf(salesTaxPercentage));
                 buyNowLoreList.add(plugin.getLanguageManager().getMessage("gui.lore.buynow.sellernote.salestax", placeholders));
            }
            buyNowMeta.setLore(buyNowLoreList);

            buyNowButton.setItemMeta(buyNowMeta);
            bidGui.setItem(24, buyNowButton);
        }

        ItemStack backButton = new ItemStack(Material.ARROW);
        tempMeta = backButton.getItemMeta();
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.backtoauctions"));
        backButton.setItemMeta(tempMeta);
        bidGui.setItem(18, backButton);

        ItemStack closeInvButton = new ItemStack(Material.BARRIER);
        tempMeta = closeInvButton.getItemMeta();
        tempMeta.setDisplayName(plugin.getLanguageManager().getMessage("gui.button.close"));
        closeInvButton.setItemMeta(tempMeta);
        bidGui.setItem(26, closeInvButton);

        playerViewingBidConfirmation.put(player.getUniqueId(), auction.getAuctionId());
        player.openInventory(bidGui);
    }


    public ItemStack createDisplayItem(Auction auction) { // Made non-static
        ItemStack display = auction.getItem().clone(); // Get a clone of the actual item
        ItemMeta meta = display.getItemMeta();
        if (meta == null) { // Should not happen for normal items, but good practice
            meta = Bukkit.getItemFactory().getItemMeta(display.getType());
        }

        List<String> lore = new ArrayList<>(); // Single declaration
        // if (meta.hasLore()) { lore.addAll(meta.getLore()); lore.add(""); }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", auction.getSellerName());
        lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.seller", placeholders));

        if (auction.getHighestBidderUuid() != null) {
            placeholders.clear();
            placeholders.put("amount", ProAuction.format(auction.getCurrentBid()));
            lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.highestbid", placeholders));
            placeholders.clear();
            placeholders.put("name", auction.getHighestBidderName());
            lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.highestbidder", placeholders));
        } else {
            placeholders.clear();
            placeholders.put("amount", ProAuction.format(auction.getStartingPrice()));
            lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.startingprice", placeholders));
        }
        placeholders.clear();
        placeholders.put("amount", ProAuction.format(auction.getMinIncrement()));
        lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.minincrement", placeholders));

        if (auction.getBuyNowPrice() > 0) {
            placeholders.clear();
            placeholders.put("amount", ProAuction.format(auction.getBuyNowPrice()));
            lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.buynowprice", placeholders));
        }
        placeholders.clear();
        placeholders.put("time", formatTimeRemaining(auction.getEndTimeMillis() - System.currentTimeMillis()));
        lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.timeremaining", placeholders));

        lore.add(""); // Separator
        placeholders.clear();
        placeholders.put("id", auction.getAuctionId().toString().substring(0, 8));
        lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.id", placeholders));
        lore.add(plugin.getLanguageManager().getMessage("gui.itemdisplay.clickformore"));

        double salesTaxPercentage = plugin.getSalesTaxPercentage();
        if (salesTaxPercentage > 0) {
            lore.add(""); // Add a separator line
            placeholders.clear();
            placeholders.put("tax_percentage", String.valueOf(salesTaxPercentage));
            lore.add(plugin.getLanguageManager().getMessage("gui.lore.sellernote.salestax", placeholders));
        }

        meta.setLore(lore);
        // Display name should remain the item's actual name, possibly colored by other means if needed.
        // For now, using a simple colored version of its plain name.
        meta.setDisplayName(org.bukkit.ChatColor.AQUA + getItemNamePlain(display));

        display.setItemMeta(meta);
        return display;
    }

    private String getItemNamePlain(ItemStack item) { // Made non-static
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        String typeName = item.getType().toString().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
    }

    private String formatTimeRemaining(long millis) { // Made non-static
        if (millis < 0) return ChatColor.RED + "Ended";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else if (seconds > 0){
            return String.format("%ds", seconds);
        } else {
            return ChatColor.RED + "Ending soon!";
        }
    }

    @Override
    public Inventory getInventory() {
        // This method is required by InventoryHolder.
        // It's generally used if the GUI itself is the inventory, but here we create inventories dynamically.
        // Returning null is acceptable if not used, or an empty/default inventory.
        return null;
    }

    public Map<UUID, Integer> getPlayerOpenAuctionPageMap() {
        return playerOpenAuctionPage;
    }

    public Map<UUID, Map<Integer, UUID>> getPlayerGUIAuctionSlotsMap() {
        return playerGUIAuctionSlots;
    }

    public Map<UUID, UUID> getPlayerViewingBidConfirmationMap() {
        return playerViewingBidConfirmation;
    }

    public Map<UUID, UUID> getPlayerPendingChatBidMap() {
        return playerPendingChatBid;
    }
}
