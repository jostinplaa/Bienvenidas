package dev.jules.proauction.listener;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.gui.AuctionGUI;
import dev.jules.proauction.model.Auction;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer; // Added missing import from processBid
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent; // Corrected import location
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class GuiListener implements Listener {

    private final ProAuction plugin;
    private final AuctionGUI auctionGUI;

    public GuiListener(ProAuction plugin, AuctionGUI auctionGUI) {
        this.plugin = plugin;
        this.auctionGUI = auctionGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof AuctionGUI)) {
            return; // Not our GUI
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();
        String inventoryTitle = event.getView().getTitle();

        if (inventoryTitle.startsWith(AuctionGUI.AUCTION_LIST_TITLE_PREFIX)) {
            handleMainAuctionListClick(player, displayName, event.getSlot());
        } else if (inventoryTitle.startsWith(AuctionGUI.BID_CONFIRM_TITLE_PREFIX)) {
            handleBidConfirmationClick(player, displayName);
        }
    }

    private void handleMainAuctionListClick(Player player, String clickedItemName, int slot) {
        Map<UUID, Integer> openPages = auctionGUI.getPlayerOpenAuctionPageMap();
        int currentPage = openPages.getOrDefault(player.getUniqueId(), 0);

        if (clickedItemName.equals(AuctionGUI.NEXT_PAGE_NAME)) {
            auctionGUI.openMainAuctionPage(player, currentPage + 1);
        } else if (clickedItemName.equals(AuctionGUI.PREV_PAGE_NAME)) {
            auctionGUI.openMainAuctionPage(player, currentPage - 1);
        } else if (clickedItemName.equals(AuctionGUI.CLOSE_BUTTON_NAME)) {
            player.closeInventory();
        } else {
            Map<Integer, UUID> auctionSlots = auctionGUI.getPlayerGUIAuctionSlotsMap().get(player.getUniqueId());
            if (auctionSlots != null && auctionSlots.containsKey(slot)) {
                UUID auctionId = auctionSlots.get(slot);
                Auction auction = plugin.getAuction(auctionId);
                if (auction != null && auction.isActive()) {
                    auctionGUI.openBidConfirmationGUI(player, auction);
                } else {
                    player.sendMessage(ChatColor.RED + "This auction is no longer available.");
                    auctionGUI.openMainAuctionPage(player, currentPage); // Refresh view
                }
            }
        }
    }

    private void handleBidConfirmationClick(Player player, String clickedItemName) {
        UUID auctionId = auctionGUI.getPlayerViewingBidConfirmationMap().get(player.getUniqueId());
        if (auctionId == null) {
            player.closeInventory();
            plugin.sendMessage(player, ChatColor.RED + "Error: Could not find auction context. Please reopen the auction house.");
            return;
        }
        Auction auction = plugin.getAuction(auctionId);
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, ChatColor.RED + "This auction is no longer available.");
            auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0));
            return;
        }

        if (clickedItemName.equals(AuctionGUI.BID_MINIMUM_NAME)) {
            double minNextBid = auction.getCurrentBid() == auction.getStartingPrice() && auction.getHighestBidderUuid() == null ? auction.getStartingPrice() : auction.getCurrentBid() + auction.getMinIncrement();
            processBid(player, auction, minNextBid);
        } else if (clickedItemName.equals(AuctionGUI.BID_CUSTOM_NAME)) {
            auctionGUI.getPlayerPendingChatBidMap().put(player.getUniqueId(), auctionId);
            player.closeInventory();
            plugin.sendMessage(player, ChatColor.GOLD + "Please type your bid amount in chat for auction: " + ChatColor.AQUA + getItemName(auction.getItem()));
            plugin.sendMessage(player, ChatColor.GRAY + "(Auction ID: " + auction.getAuctionId().toString().substring(0,8) + ")");
        } else if (clickedItemName.equals(AuctionGUI.BACK_BUTTON_NAME)) {
            auctionGUI.getPlayerViewingBidConfirmationMap().remove(player.getUniqueId()); // Clear context before going back
            auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0));
        } else if (clickedItemName.equals(AuctionGUI.CLOSE_BUTTON_NAME)) {
            player.closeInventory();
        } else if (clickedItemName.equals(ChatColor.GOLD + "" + ChatColor.BOLD + "Buy Now")) { // Handle Buy Now
            handleBuyNow(player, auction);
        }
    }

    private void handleBuyNow(Player player, Auction auction) {
        if (!player.hasPermission("proauction.bid")) { // Or a new proauction.buynow permission
            plugin.sendMessage(player, ChatColor.RED + "You don't have permission to buy items.");
            return;
        }
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, ChatColor.RED + "You cannot buy your own auction item.");
            return;
        }
        if (auction.getBuyNowPrice() <= 0) {
            plugin.sendMessage(player, ChatColor.RED + "This item is not available for Buy Now.");
            return;
        }

        if (!ProAuction.hasEnough(player, auction.getBuyNowPrice())) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have enough money to buy this item for " + ProAuction.format(auction.getBuyNowPrice()) + ".");
            return;
        }

        // Attempt withdrawal from buyer
        if (!ProAuction.withdrawMoney(player, auction.getBuyNowPrice())) {
            plugin.sendMessage(player, ChatColor.RED + "Payment failed. Could not withdraw funds.");
            return;
        }

        // Attempt deposit to seller
        OfflinePlayer seller = plugin.getServer().getOfflinePlayer(auction.getSellerUuid());
        if (!ProAuction.depositMoney(seller, auction.getBuyNowPrice())) {
            plugin.logSevere("CRITICAL: Failed to deposit BuyNow funds to seller " + seller.getName() + " for auction " + auction.getAuctionId() + ". Refunding buyer " + player.getName());
            ProAuction.depositMoney(player, auction.getBuyNowPrice()); // Attempt to refund buyer
            plugin.sendMessage(player, ChatColor.RED + "Payment to seller failed. Your money has been refunded. Please report this to an admin.");
            return;
        }

        // Give item to player
        if (!player.getInventory().addItem(auction.getItem().clone()).isEmpty()) {
            plugin.sendMessage(player, ChatColor.RED + "Your inventory is full! The item has been dropped at your feet.");
            player.getWorld().dropItemNaturally(player.getLocation(), auction.getItem().clone());
            // Critical: money already exchanged. Item drop is last resort.
            // Could also try to put it in a temporary "delivery box" or similar for later.
        } else {
            plugin.sendMessage(player, ChatColor.GREEN + "You purchased " + getItemName(auction.getItem()) + " for " + ProAuction.format(auction.getBuyNowPrice()) + "!");
        }

        auction.setActive(false);
        plugin.updateAuctionInStorage(auction); // Save change to inactive
        plugin.removeAuction(auction.getAuctionId()); // Remove from active list & storage file after processing

        if (seller.isOnline() && seller.getPlayer() != null) {
            plugin.sendMessage(seller.getPlayer(), ChatColor.GREEN + "Your auction for " + getItemName(auction.getItem()) + " was bought by " + player.getName() + " for " + ProAuction.format(auction.getBuyNowPrice()) + "!");
        } else {
            // Optionally, log that the seller was offline and couldn't be notified immediately
            plugin.logInfo("Seller " + seller.getName() + " was offline and could not be notified of Buy Now for auction " + auction.getAuctionId());
        }
        plugin.broadcastMessage(ChatColor.YELLOW + player.getName() + " bought " + getItemName(auction.getItem()) + " from " + auction.getSellerName() + " for " + ProAuction.format(auction.getBuyNowPrice()) + " using Buy Now!");

        player.closeInventory();
        // Consider refreshing the main AH view for other players or the current player if they reopen
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID auctionId = auctionGUI.getPlayerPendingChatBidMap().get(player.getUniqueId());

        if (auctionId == null) {
            return;
        }
        event.setCancelled(true);
        auctionGUI.getPlayerPendingChatBidMap().remove(player.getUniqueId());

        Auction auction = plugin.getAuction(auctionId);
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, ChatColor.RED + "That auction is no longer available.");
            plugin.getServer().getScheduler().runTask(plugin, () -> auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0)));
            return;
        }

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(event.getMessage());
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, ChatColor.RED + "Invalid bid amount: " + ChatColor.WHITE + event.getMessage());
            plugin.sendMessage(player, ChatColor.YELLOW + "Please try bidding again via the GUI.");
            plugin.getServer().getScheduler().runTask(plugin, () -> auctionGUI.openBidConfirmationGUI(player, auction));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> processBid(player, auction, bidAmount));
    }

    private void processBid(Player player, Auction auction, double bidAmount) {
        if (!auction.isActive()){ // Re-check auction status in sync task
            plugin.sendMessage(player, ChatColor.RED + "This auction is no longer available.");
            auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0));
            return;
        }

        if (!player.hasPermission("proauction.bid")) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have permission to bid.");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, ChatColor.RED + "You cannot bid on your own auction.");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }

        double requiredBid;
        if (auction.getHighestBidderUuid() == null) { // No bids yet
            requiredBid = auction.getStartingPrice();
        } else { // There is an existing bid
            requiredBid = auction.getCurrentBid() + auction.getMinIncrement();
        }
        // Ensure bid is at least the starting price if no bids, or current bid + increment if there are bids
         if (bidAmount < requiredBid) {
            plugin.sendMessage(player, ChatColor.RED + "Your bid of " + ProAuction.format(bidAmount) + " must be at least " + ProAuction.format(requiredBid) + ".");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }
        // Additional check to ensure it's strictly greater than current if there is a current bid
        if (auction.getHighestBidderUuid() != null && bidAmount <= auction.getCurrentBid()) {
             plugin.sendMessage(player, ChatColor.RED + "Your bid must be higher than the current bid (" + ProAuction.format(auction.getCurrentBid()) + ").");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }


        if (!ProAuction.hasEnough(player, bidAmount)) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have enough money. You need " + ProAuction.format(bidAmount) + ".");
            plugin.sendMessage(player, ChatColor.GRAY + "(Funds will be taken if you win at the end of the auction)");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }

        if (auction.getHighestBidderUuid() != null && !auction.getHighestBidderUuid().equals(player.getUniqueId())) {
            OfflinePlayer previousOfflineBidder = plugin.getServer().getOfflinePlayer(auction.getHighestBidderUuid());
            if (previousOfflineBidder.isOnline() && previousOfflineBidder.getPlayer() != null) {
                plugin.sendMessage(previousOfflineBidder.getPlayer(), ChatColor.YELLOW + "You have been outbid on " + getItemName(auction.getItem()) + " by " + player.getName() + ".");
            }
        }

        auction.setHighestBid(bidAmount, player.getUniqueId(), player.getName());
        plugin.updateAuctionInStorage(auction);

        plugin.sendMessage(player, ChatColor.GREEN + "You successfully bid " + ProAuction.format(bidAmount) + " on " + getItemName(auction.getItem()) + "!");

        Player seller = plugin.getServer().getPlayer(auction.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            plugin.sendMessage(seller, ChatColor.AQUA + player.getName() + " has bid " + ProAuction.format(bidAmount) + " on your auction for " + getItemName(auction.getItem()) + ".");
        }

        player.closeInventory();
        auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(),0));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();

        if(!auctionGUI.getPlayerPendingChatBidMap().containsKey(player.getUniqueId())) {
             auctionGUI.getPlayerViewingBidConfirmationMap().remove(player.getUniqueId());
        }

        if (event.getInventory().getHolder() instanceof AuctionGUI &&
            !auctionGUI.getPlayerViewingBidConfirmationMap().containsKey(player.getUniqueId()) &&
            !auctionGUI.getPlayerPendingChatBidMap().containsKey(player.getUniqueId())) {

            auctionGUI.getPlayerOpenAuctionPageMap().remove(player.getUniqueId());
            auctionGUI.getPlayerGUIAuctionSlotsMap().remove(player.getUniqueId());
        }
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName() + ChatColor.RESET;
        }
        String typeName = item.getType().toString().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
    }
}
