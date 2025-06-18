package dev.jules.proauction.listener;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.util.TaxFeeCalculator;
import dev.jules.proauction.util.TaxFeeCalculator.TaxCalculationResult;
import dev.jules.proauction.gui.AuctionGUI;
import dev.jules.proauction.model.Auction;
import org.bukkit.ChatColor; // Ensure this is present for getItemName
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent; // Corrected import location
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap; // Added for placeholders
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

        // Get the base titles (without placeholders) from LanguageManager
        String mainTitlePrefix = plugin.getLanguageManager().getMessage("gui.title.main.prefix");
        // For comparison, we might need to strip color codes if the title from event includes them but prefix from lang file doesn't after ChatColor.translate.
        // However, getMessage already translates color codes, so inventoryTitle should be compared as is.
        // Let's assume inventoryTitle is already color-translated by Bukkit when we get it.

        String bidConfirmTitlePrefix = plugin.getLanguageManager().getMessage("gui.title.bidconfirm.prefix");

        if (inventoryTitle.startsWith(mainTitlePrefix)) {
            handleMainAuctionListClick(player, displayName, event.getSlot());
        } else if (inventoryTitle.startsWith(bidConfirmTitlePrefix)) {
            handleBidConfirmationClick(player, displayName);
        }
    }

    private void handleMainAuctionListClick(Player player, String clickedItemName, int slot) {
        Map<UUID, Integer> openPages = auctionGUI.getPlayerOpenAuctionPageMap();
        int currentPage = openPages.getOrDefault(player.getUniqueId(), 0);

        if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.nextpage"))) {
            auctionGUI.openMainAuctionPage(player, currentPage + 1);
        } else if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.prevpage"))) {
            auctionGUI.openMainAuctionPage(player, currentPage - 1);
        } else if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.close"))) {
            player.closeInventory();
        } else {
            Map<Integer, UUID> auctionSlots = auctionGUI.getPlayerGUIAuctionSlotsMap().get(player.getUniqueId());
            if (auctionSlots != null && auctionSlots.containsKey(slot)) {
                UUID auctionId = auctionSlots.get(slot);
                Auction auction = plugin.getAuction(auctionId);
                if (auction != null && auction.isActive()) {
                    auctionGUI.openBidConfirmationGUI(player, auction);
                } else {
                    plugin.sendMessage(player, "gui.status.auctionunavailable");
                    auctionGUI.openMainAuctionPage(player, currentPage); // Refresh view
                }
            }
        }
    }

    private void handleBidConfirmationClick(Player player, String clickedItemName) {
        UUID auctionId = auctionGUI.getPlayerViewingBidConfirmationMap().get(player.getUniqueId());
        if (auctionId == null) {
            player.closeInventory();
            plugin.sendMessage(player, "gui.status.bidconfirm.errorcontext");
            return;
        }
        Auction auction = plugin.getAuction(auctionId);
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, "gui.status.auctionunavailable");
            auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0));
            return;
        }

        // IMPORTANT: Comparing clickedItemName with AuctionGUI constants (like AuctionGUI.BID_MINIMUM_NAME)
        // will FAIL if those constants are hardcoded English strings and clickedItemName is translated.
        // This part of the logic needs to compare with translated strings from LanguageManager:
        // e.g., if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.bidminimum")))
        // For this refactor, we are focusing on plugin.sendMessage, assuming this comparison logic is handled or will be.

        if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.bidminimum"))) {
            double minNextBid = auction.getCurrentBid() == auction.getStartingPrice() && auction.getHighestBidderUuid() == null ? auction.getStartingPrice() : auction.getCurrentBid() + auction.getMinIncrement();
            processBid(player, auction, minNextBid);
        } else if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.bidcustom"))) {
            auctionGUI.getPlayerPendingChatBidMap().put(player.getUniqueId(), auctionId);
            player.closeInventory();
            Map<String, String> customBidPromptPlaceholders = new HashMap<>();
            customBidPromptPlaceholders.put("item_name", getItemName(auction.getItem()));
            plugin.sendMessage(player, "gui.status.custombid.prompt", customBidPromptPlaceholders);
            plugin.sendMessage(player, "gui.status.custombid.prompt.id", "auction_id", auction.getAuctionId().toString().substring(0,8) );

        } else if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.backtoauctions"))) {
            auctionGUI.getPlayerViewingBidConfirmationMap().remove(player.getUniqueId());
            auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0));
        } else if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.close"))) {
            player.closeInventory();
        } else if (clickedItemName.equals(plugin.getLanguageManager().getMessage("gui.button.buynow"))) {
            handleBuyNow(player, auction);
        }
    }

    private void handleBuyNow(Player player, Auction auction) {
        if (!player.hasPermission("proauction.bid")) { // Consider a specific "proauction.buynow" permission
            plugin.sendMessage(player, "error.nopermission"); // Using generic no permission
            return;
        }
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "error.cannotbuyown");
            return;
        }
        if (auction.getBuyNowPrice() <= 0) {
            plugin.sendMessage(player, "buynow.unavailable");
            return;
        }

        if (!ProAuction.hasEnough(player, auction.getBuyNowPrice())) {
            plugin.sendMessage(player, "error.notenoughmoney", "amount", ProAuction.format(auction.getBuyNowPrice()));
            return;
        }

        // Attempt withdrawal from buyer
        if (!ProAuction.withdrawMoney(player, auction.getBuyNowPrice())) {
            plugin.sendMessage(player, "error.paymentfailed");
            return;
        }

        // Sales Tax Logic for Buy Now
        double salesTaxPercentage = plugin.getSalesTaxPercentage();
        String serverAccountName = plugin.getServerAccountName(); // Retain for deposit logic
        double buyNowPrice = auction.getBuyNowPrice(); // Original amount paid by buyer

        TaxCalculationResult taxResult = TaxFeeCalculator.calculateSalesTax(buyNowPrice, salesTaxPercentage);
        double taxAmount = taxResult.taxAmount;
        double amountForSeller = taxResult.netAmountForSeller;

        if (taxAmount > 0) { // Only log and attempt deposit if tax was actually calculated and applied
            plugin.logInfo("Sales tax for Buy Now auction " + auction.getAuctionId().toString().substring(0,8) + ": " + ProAuction.format(taxAmount) + " (" + salesTaxPercentage + "% of " + ProAuction.format(buyNowPrice) + "). Seller receives " + ProAuction.format(amountForSeller));
            if (serverAccountName != null && !serverAccountName.isEmpty()) {
                OfflinePlayer serverAccount = plugin.getServer().getOfflinePlayer(serverAccountName);
                boolean accountExistsOrCanBeCreated = ProAuction.getEconomy().hasAccount(serverAccountName) || serverAccount.hasPlayedBefore();

                if (accountExistsOrCanBeCreated) {
                    if (ProAuction.depositMoney(serverAccount, taxAmount)) {
                        plugin.logInfo("Deposited sales tax (Buy Now) of " + ProAuction.format(taxAmount) + " to " + serverAccountName + " for auction " + auction.getAuctionId().toString().substring(0,8));
                    } else {
                        plugin.logWarning("Failed to deposit sales tax (Buy Now) of " + ProAuction.format(taxAmount) + " to " + serverAccountName + " for auction " + auction.getAuctionId().toString().substring(0,8) + ". Tax was still deducted from seller.");
                    }
                } else {
                    plugin.logWarning("Server account '" + serverAccountName + "' for sales tax (Buy Now) not found/accessible. Tax of " + ProAuction.format(taxAmount) + " deducted from seller but not deposited. Auction: " + auction.getAuctionId().toString().substring(0,8));
                }
            }
        }
        // If taxAmount is 0, amountForSeller is buyNowPrice, and no specific tax logging or deposit is needed here.

        // Attempt deposit to seller
        OfflinePlayer seller = plugin.getServer().getOfflinePlayer(auction.getSellerUuid());
        if (!ProAuction.depositMoney(seller, amountForSeller)) {
            plugin.logSevere("CRITICAL: Failed to deposit BuyNow funds (" + ProAuction.format(amountForSeller) + " after tax) to seller " + seller.getName() + " for auction " + auction.getAuctionId().toString().substring(0,8) + ". Original price: " + ProAuction.format(buyNowPrice) + ". Refunding buyer full price.");
            // Refund the full buyNowPrice to the buyer as the transaction with seller failed.
            if (!ProAuction.depositMoney(player, buyNowPrice)) {
                 plugin.logSevere("CRITICAL: FAILED TO REFUND BUYER " + player.getName() + " for BuyNow auction " + auction.getAuctionId().toString().substring(0,8) + " after seller deposit failed. MANUAL INTERVENTION NEEDED.");
            }
            plugin.sendMessage(player, "error.paytochestfailed"); // Changed key, assuming 'chest' was a typo for 'seller'
            return;
        }

        // Give item to player
        if (!player.getInventory().addItem(auction.getItem().clone()).isEmpty()) {
            plugin.sendMessage(player, "error.inventoryfull.itemdropped");
            player.getWorld().dropItemNaturally(player.getLocation(), auction.getItem().clone());
        } else {
            Map<String, String> purchasePlaceholders = new HashMap<>();
            purchasePlaceholders.put("item_name", getItemName(auction.getItem()));
            purchasePlaceholders.put("price", ProAuction.format(buyNowPrice));
            plugin.sendMessage(player, "buynow.success.buyer", purchasePlaceholders);
        }

        auction.setActive(false);
        plugin.updateAuctionInStorage(auction); // Save change to inactive
        plugin.removeAuction(auction.getAuctionId()); // Remove from active list & storage file after processing

        if (seller.isOnline() && seller.getPlayer() != null) {
            Map<String, String> sellerPlaceholders = new HashMap<>();
            sellerPlaceholders.put("item_name", getItemName(auction.getItem()));
            sellerPlaceholders.put("player_name", player.getName());
            sellerPlaceholders.put("price", ProAuction.format(buyNowPrice));
            if (taxAmount > 0) {
                sellerPlaceholders.put("tax_percentage", String.valueOf(salesTaxPercentage));
                sellerPlaceholders.put("tax_amount", ProAuction.format(taxAmount));
                sellerPlaceholders.put("net_amount", ProAuction.format(amountForSeller));
                plugin.sendMessage(seller.getPlayer(), "buynow.success.seller.withtax", sellerPlaceholders);
            } else {
                // Ensure net_amount is available even if tax is zero for consistency if key uses it
                sellerPlaceholders.put("net_amount", ProAuction.format(amountForSeller));
                plugin.sendMessage(seller.getPlayer(), "buynow.success.seller", sellerPlaceholders);
            }
        } else {
            plugin.logInfo("Seller " + seller.getName() + " was offline and could not be notified of Buy Now for auction " + auction.getAuctionId().toString().substring(0,8) + ". Sale processed, seller received " + ProAuction.format(amountForSeller));
        }

        Map<String, String> broadcastPlaceholders = new HashMap<>();
        broadcastPlaceholders.put("player_name", player.getName());
        broadcastPlaceholders.put("item_name", getItemName(auction.getItem()));
        broadcastPlaceholders.put("seller_name", auction.getSellerName());
        broadcastPlaceholders.put("price", ProAuction.format(buyNowPrice));
        plugin.broadcastMessage("buynow.broadcast", broadcastPlaceholders);

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
            plugin.sendMessage(player, "gui.status.auctionunavailable");
            plugin.getServer().getScheduler().runTask(plugin, () -> auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0)));
            return;
        }

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(event.getMessage());
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "gui.status.custombid.invalidamount", "amount", event.getMessage());
            plugin.sendMessage(player, "gui.status.custombid.tryagain");
            plugin.getServer().getScheduler().runTask(plugin, () -> auctionGUI.openBidConfirmationGUI(player, auction));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> processBid(player, auction, bidAmount));
    }

    private void processBid(Player player, Auction auction, double bidAmount) {
        if (!auction.isActive()){
            plugin.sendMessage(player, "gui.status.auctionunavailable");
            auctionGUI.openMainAuctionPage(player, auctionGUI.getPlayerOpenAuctionPageMap().getOrDefault(player.getUniqueId(), 0));
            return;
        }

        if (!player.hasPermission("proauction.bid")) {
            plugin.sendMessage(player, "error.nopermission");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "error.cannotbidown");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }

        double requiredBid;
        if (auction.getHighestBidderUuid() == null) {
            requiredBid = auction.getStartingPrice();
        } else {
            requiredBid = auction.getCurrentBid() + auction.getMinIncrement();
        }

        if (bidAmount < requiredBid) {
            plugin.sendMessage(player, "error.bidnotenough", "min_next_bid", ProAuction.format(requiredBid));
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }
        if (auction.getHighestBidderUuid() != null && bidAmount <= auction.getCurrentBid()) {
             plugin.sendMessage(player, "error.bidtoolow", "current_bid", ProAuction.format(auction.getCurrentBid()));
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }

        if (!ProAuction.hasEnough(player, bidAmount)) {
            plugin.sendMessage(player, "error.notenoughmoney", "amount", ProAuction.format(bidAmount));
            plugin.sendMessage(player, "auction.bid.tobidder.info");
            auctionGUI.openBidConfirmationGUI(player, auction);
            return;
        }

        if (auction.getHighestBidderUuid() != null && !auction.getHighestBidderUuid().equals(player.getUniqueId())) {
            OfflinePlayer previousOfflineBidder = plugin.getServer().getOfflinePlayer(auction.getHighestBidderUuid());
            if (previousOfflineBidder.isOnline() && previousOfflineBidder.getPlayer() != null) {
                Map<String, String> outbidPlaceholders = new HashMap<>();
                outbidPlaceholders.put("item_name", getItemName(auction.getItem()));
                outbidPlaceholders.put("new_bidder_name", player.getName());
                plugin.sendMessage(previousOfflineBidder.getPlayer(), "auction.bid.outbid", outbidPlaceholders);
            }
        }

        auction.setHighestBid(bidAmount, player.getUniqueId(), player.getName());
        plugin.updateAuctionInStorage(auction);

        Map<String, String> bidSuccessPlaceholders = new HashMap<>();
        bidSuccessPlaceholders.put("item_name", getItemName(auction.getItem()));
        bidSuccessPlaceholders.put("bid_amount", ProAuction.format(bidAmount));
        plugin.sendMessage(player, "auction.bid.success", bidSuccessPlaceholders);

        Player seller = plugin.getServer().getPlayer(auction.getSellerUuid());
        if (seller != null && seller.isOnline()) {
             Map<String, String> sellerUpdatePlaceholders = new HashMap<>();
            sellerUpdatePlaceholders.put("bidder_name", player.getName());
            sellerUpdatePlaceholders.put("bid_amount", ProAuction.format(bidAmount));
            sellerUpdatePlaceholders.put("item_name", getItemName(auction.getItem()));
            plugin.sendMessage(seller, "auction.bid.toseller.update", sellerUpdatePlaceholders);
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
