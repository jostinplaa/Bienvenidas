package dev.jules.proauction.gui;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.model.Auction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    public static final String AUCTION_LIST_TITLE_PREFIX = ChatColor.DARK_BLUE + "Auction House - Page ";
    public static final String BID_CONFIRM_TITLE_PREFIX = ChatColor.DARK_BLUE + "Bid on: ";

    public static final String NEXT_PAGE_NAME = ChatColor.GREEN + "Next Page";
    public static final String PREV_PAGE_NAME = ChatColor.GREEN + "Previous Page";
    public static final String CLOSE_BUTTON_NAME = ChatColor.RED + "Close";
    public static final String BACK_BUTTON_NAME = ChatColor.YELLOW + "Back to Auctions";
    public static final String BID_MINIMUM_NAME = ChatColor.GREEN + "Bid Minimum";
    public static final String BID_CUSTOM_NAME = ChatColor.GOLD + "Custom Bid";


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

        Inventory gui = Bukkit.createInventory(this, 54, AUCTION_LIST_TITLE_PREFIX + (pageNumber + 1) + "/" + totalPages);

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
            prevMeta.setDisplayName(PREV_PAGE_NAME);
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage); // Bottom left
        }

        if (pageNumber < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(NEXT_PAGE_NAME);
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage); // Bottom right
        }

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(CLOSE_BUTTON_NAME);
        closeButton.setItemMeta(closeMeta);
        gui.setItem(49, closeButton); // Bottom middle

        playerOpenAuctionPage.put(player.getUniqueId(), pageNumber);
        playerGUIAuctionSlots.put(player.getUniqueId(), auctionSlots);
        player.openInventory(gui);
    }

    public void openBidConfirmationGUI(Player player, Auction auction) {
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, ChatColor.RED + "This auction is no longer available.");
            openMainAuctionPage(player, playerOpenAuctionPage.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        Inventory bidGui = Bukkit.createInventory(this, 27, BID_CONFIRM_TITLE_PREFIX + getItemNamePlain(auction.getItem()));

        // Display Item (Slot 4 - center of top row)
        bidGui.setItem(4, auction.getItem().clone());

        // Information Items
        ItemMeta tempMeta;

        ItemStack sellerInfo = new ItemStack(Material.PLAYER_HEAD); // Or other suitable material
        tempMeta = sellerInfo.getItemMeta();
        tempMeta.setDisplayName(ChatColor.GOLD + "Seller: " + ChatColor.GRAY + auction.getSellerName());
        sellerInfo.setItemMeta(tempMeta);
        bidGui.setItem(10, sellerInfo);

        ItemStack currentBidInfo = new ItemStack(Material.GOLD_NUGGET);
        tempMeta = currentBidInfo.getItemMeta();
        tempMeta.setDisplayName(ChatColor.GOLD + "Current Bid: " + ChatColor.GREEN + ProAuction.format(auction.getCurrentBid()));
        List<String> bidLore = new ArrayList<>();
        if (auction.getHighestBidderUuid() != null) {
            bidLore.add(ChatColor.GRAY + "By: " + auction.getHighestBidderName());
        } else {
            bidLore.add(ChatColor.GRAY + "No bids yet (Starting Price)");
        }
        tempMeta.setLore(bidLore);
        currentBidInfo.setItemMeta(tempMeta);
        bidGui.setItem(11, currentBidInfo);

        double nextMinBid = auction.getCurrentBid() == auction.getStartingPrice() && auction.getHighestBidderUuid() == null ? auction.getStartingPrice() : auction.getCurrentBid() + auction.getMinIncrement();
        ItemStack minNextBidInfo = new ItemStack(Material.COMPARATOR);
        tempMeta = minNextBidInfo.getItemMeta();
        tempMeta.setDisplayName(ChatColor.GOLD + "Minimum Next Bid: " + ChatColor.GREEN + ProAuction.format(nextMinBid));
        minNextBidInfo.setItemMeta(tempMeta);
        bidGui.setItem(12, minNextBidInfo);

        ItemStack timeInfo = new ItemStack(Material.CLOCK);
        tempMeta = timeInfo.getItemMeta();
        tempMeta.setDisplayName(ChatColor.GOLD + "Time Remaining: " + ChatColor.RED + formatTimeRemaining(auction.getEndTimeMillis() - System.currentTimeMillis()));
        timeInfo.setItemMeta(tempMeta);
        bidGui.setItem(13, timeInfo);

        ItemStack balanceInfo = new ItemStack(Material.EMERALD);
        tempMeta = balanceInfo.getItemMeta();
        tempMeta.setDisplayName(ChatColor.GOLD + "Your Balance: " + ChatColor.GREEN + ProAuction.format(ProAuction.getEconomy().getBalance(player)));
        balanceInfo.setItemMeta(tempMeta);
        bidGui.setItem(14, balanceInfo);

        // Action Buttons
        ItemStack bidMinimum = new ItemStack(Material.GREEN_WOOL);
        tempMeta = bidMinimum.getItemMeta();
        tempMeta.setDisplayName(BID_MINIMUM_NAME);
        tempMeta.setLore(List.of(ChatColor.GRAY + "Bid " + ProAuction.format(nextMinBid)));
        bidMinimum.setItemMeta(tempMeta);
        bidGui.setItem(20, bidMinimum);

        ItemStack customBid = new ItemStack(Material.GOLD_INGOT);
        tempMeta = customBid.getItemMeta();
        tempMeta.setDisplayName(BID_CUSTOM_NAME);
        tempMeta.setLore(List.of(ChatColor.GRAY + "Enter a custom bid amount in chat."));
        customBid.setItemMeta(tempMeta);
        bidGui.setItem(22, customBid);

        if (auction.getBuyNowPrice() > 0) {
            ItemStack buyNowButton = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta buyNowMeta = buyNowButton.getItemMeta();
            List<String> buyNowLore = new ArrayList<>(); // Initialize new list
            buyNowMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Buy Now");
            buyNowLore.add(ChatColor.GREEN + "Price: " + ProAuction.format(auction.getBuyNowPrice())); // Add initial lore

            // Add tax info if applicable
            double salesTaxPercentage = plugin.getSalesTaxPercentage();
            if (salesTaxPercentage > 0) {
                buyNowLore.add(ChatColor.GRAY + "Seller receives price minus " + salesTaxPercentage + "% tax.");
            }
            buyNowMeta.setLore(buyNowLore); // Set the potentially modified lore

            buyNowButton.setItemMeta(buyNowMeta);
            bidGui.setItem(24, buyNowButton); // Example slot, adjust as needed
        }

        ItemStack backButton = new ItemStack(Material.ARROW);
        tempMeta = backButton.getItemMeta();
        tempMeta.setDisplayName(BACK_BUTTON_NAME);
        backButton.setItemMeta(tempMeta);
        bidGui.setItem(18, backButton); // Bottom left for bid GUI

        ItemStack closeInvButton = new ItemStack(Material.BARRIER);
        tempMeta = closeInvButton.getItemMeta();
        tempMeta.setDisplayName(CLOSE_BUTTON_NAME);
        closeInvButton.setItemMeta(tempMeta);
        bidGui.setItem(26, closeInvButton); // Bottom right for bid GUI

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
        // if (meta.hasLore()) { lore.addAll(meta.getLore()); lore.add(""); } // Example of preserving original lore

        lore.add(ChatColor.GOLD + "Seller: " + ChatColor.GRAY + auction.getSellerName());
        if (auction.getHighestBidderUuid() != null) {
            lore.add(ChatColor.GOLD + "Highest Bid: " + ChatColor.GREEN + ProAuction.format(auction.getCurrentBid()));
            lore.add(ChatColor.GOLD + "Highest Bidder: " + ChatColor.GRAY + auction.getHighestBidderName());
        } else {
            lore.add(ChatColor.GOLD + "Starting Price: " + ChatColor.GREEN + ProAuction.format(auction.getStartingPrice()));
        }
        lore.add(ChatColor.GOLD + "Min Increment: " + ChatColor.GREEN + ProAuction.format(auction.getMinIncrement()));
        if (auction.getBuyNowPrice() > 0) {
            lore.add(ChatColor.GOLD + "Buy Now: " + ChatColor.AQUA + ProAuction.format(auction.getBuyNowPrice()));
        }
        lore.add(ChatColor.GOLD + "Time Remaining: " + ChatColor.RED + formatTimeRemaining(auction.getEndTimeMillis() - System.currentTimeMillis()));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "ID: " + auction.getAuctionId().toString().substring(0, 8));
        lore.add(ChatColor.BLUE + "" + ChatColor.ITALIC + "Click for more options!");

        // Add sales tax information if applicable
        double salesTaxPercentage = plugin.getSalesTaxPercentage();
        if (salesTaxPercentage > 0) {
            lore.add(""); // Add a separator line
            lore.add(ChatColor.DARK_AQUA + "Note: Sale price for seller is subject to a " + salesTaxPercentage + "% tax.");
        }

        meta.setLore(lore); // Set the complete lore
        meta.setDisplayName(ChatColor.AQUA + getItemNamePlain(display)); // Set display name

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
