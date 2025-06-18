package com.julesmc.subastas.listeners;

import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.gui.GuiManager;
import com.julesmc.subastas.gui.helpers.AnvilInputHelper;
import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.gui.GuiManager;
import com.julesmc.subastas.gui.helpers.AnvilInputHelper;
import com.julesmc.subastas.managers.EconomyManager;
import com.julesmc.subastas.managers.LocaleManager;
import com.julesmc.subastas.objects.AuctionItem;
import org.bukkit.Bukkit; // Added import
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import java.util.UUID; // Added import
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class InventoryClickListener implements Listener {

    private final SubastasPlugin plugin;
    private final GuiManager guiManager;
    private final LocaleManager localeManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    public InventoryClickListener(SubastasPlugin plugin, GuiManager guiManager, LocaleManager localeManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.localeManager = localeManager;
        this.databaseManager = plugin.getDatabaseManager(); // Get instance from plugin
        this.economyManager = plugin.getEconomyManager();   // Get instance from plugin
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        // Check if the clicked inventory is one of our GUIs
        // Method 1: Check by title (less robust if titles change frequently or are complex)
        String inventoryTitle = event.getView().getTitle();
        if (inventoryTitle.startsWith(GuiManager.AUCTION_GUI_TITLE_PREFIX)) {
             event.setCancelled(true); // Prevent taking items

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
                // Likely an auction item, handle later for bidding/buying
                return;
            }

            String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()); // Strip colors for comparison
            String rawPrevPage = ChatColor.stripColor(localeManager.getRawMessage("auction.gui.previous-page", "Página Anterior"));
            String rawNextPage = ChatColor.stripColor(localeManager.getRawMessage("auction.gui.next-page", "Página Siguiente"));
            String rawRefresh = ChatColor.stripColor(localeManager.getRawMessage("auction.gui.refresh", "Actualizar"));

            int currentPage = guiManager.getPlayerCurrentPage(player);

            if (displayName.equals(rawPrevPage)) {
                if (currentPage > 1) {
                    guiManager.openActiveAuctionsGUI(player, currentPage - 1);
                }
            } else if (displayName.equals(rawNextPage)) {
                guiManager.openActiveAuctionsGUI(player, currentPage + 1);
            } else if (displayName.equals(rawRefresh)) {
                guiManager.openActiveAuctionsGUI(player, currentPage);
            }
            // If none of the above, it's not a control item we explicitly named,
            // so it could be an auction item or an empty slot.
            // The initial event.setCancelled(true) handles preventing item pickup.
            // Auction item clicks are handled below.

        } else if (inventoryTitle.startsWith(GuiManager.AUCTION_GUI_TITLE_PREFIX) && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            // This is a click on an auction item (since it didn't match control items by display name)
            // Used event.getCurrentItem() instead of potentially out-of-scope clickedItem
            event.setCancelled(true);
            Integer auctionId = guiManager.getAuctionIdForSlot(player.getUniqueId(), event.getRawSlot());

            if (auctionId == null) {
                // This shouldn't happen if GUI is populated correctly and maps are synced
                player.sendMessage(ChatColor.RED + "Error: No se pudo identificar la subasta.");
                return;
            }

            AuctionItem auction = databaseManager.getAuctionById(auctionId);
            if (auction == null || !auction.getStatus().equals("ACTIVE") || auction.getEndTime() < System.currentTimeMillis()) {
                player.sendMessage(localeManager.getMessage("auction.error.not-active-or-expired"));
                guiManager.openActiveAuctionsGUI(player, guiManager.getPlayerCurrentPage(player)); // Refresh GUI
                return;
            }

            // Handle bid (Left click)
            if (event.isLeftClick()) {
                handleBidAttempt(player, auction);
            } else if (event.isRightClick()) { // Handle Buy Now on Right Click
                handleBuyNowAttempt(player, auction);
            }
        }
    }

    private void handleBuyNowAttempt(Player player, AuctionItem auction) {
        if (auction.getBuyNowPrice() == null || auction.getBuyNowPrice() <= 0) {
            player.sendMessage(localeManager.getMessage("auction.error.buy-now-not-available"));
            return;
        }
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(localeManager.getMessage("auction.error.cannot-buy-own"));
            return;
        }

        double buyNowPrice = auction.getBuyNowPrice();
        if (!economyManager.hasEnough(player, buyNowPrice)) {
            player.sendMessage(localeManager.getMessage("auction.not-enough-funds-bid")); // Re-use "not enough funds" for buy now too
            return;
        }

        // Concurrency: Re-fetch auction to ensure it's still available
        AuctionItem freshAuction = databaseManager.getAuctionById(auction.getId());
        if (freshAuction == null || !freshAuction.getStatus().equals("ACTIVE") || freshAuction.getEndTime() < System.currentTimeMillis()) {
            player.sendMessage(localeManager.getMessage("auction.error.not-active-or-expired"));
            Bukkit.getScheduler().runTask(plugin, () -> guiManager.openActiveAuctionsGUI(player, guiManager.getPlayerCurrentPage(player)));
            return;
        }
        // Ensure buy now is still valid on the fresh item
        if (freshAuction.getBuyNowPrice() == null || freshAuction.getBuyNowPrice() <= 0) {
             player.sendMessage(localeManager.getMessage("auction.error.buy-now-not-available")); // Someone might have bid it up or it changed
            Bukkit.getScheduler().runTask(plugin, () -> guiManager.openActiveAuctionsGUI(player, guiManager.getPlayerCurrentPage(player)));
            return;
        }


        if (economyManager.withdrawPlayer(player, buyNowPrice)) {
            double commissionRate = plugin.getConfigManager().getDouble("auction-settings.commission.sales-tax-percentage", 0.0);
            double commission = 0;
            if (commissionRate > 0) {
                commission = buyNowPrice * commissionRate;
            }
            double amountToSeller = buyNowPrice - commission;

            economyManager.depositPlayer(Bukkit.getOfflinePlayer(freshAuction.getSellerUuid()), amountToSeller);
            databaseManager.updateAuctionStatus(freshAuction.getId(), "SOLD_BUYNOW"); // Or just "SOLD"

            player.sendMessage(localeManager.getMessage("auction.bought-directly-notification-buyer", "item", freshAuction.getItemName(), "price", String.format("%,.2f", buyNowPrice)));

            Player sellerOnline = Bukkit.getPlayer(freshAuction.getSellerUuid());
            if (sellerOnline != null && sellerOnline.isOnline()) {
                sellerOnline.sendMessage(localeManager.getMessage("auction.sold-directly-notification-seller", "item", freshAuction.getItemName(), "price", String.format("%,.2f", amountToSeller), "buyer", player.getName()));
            }

            // Notify highest bidder if they were outbid by buyout
            if (freshAuction.getHighestBidderUuid() != null && !freshAuction.getHighestBidderUuid().equals(player.getUniqueId())) {
                OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(freshAuction.getHighestBidderUuid());
                if (previousBidder.isOnline()) {
                    ((Player) previousBidder).sendMessage(localeManager.getMessage("auction.outbid-by-buyout-notification", "item", freshAuction.getItemName(), "buyer", player.getName()));
                }
            }

            plugin.getLogger().info("Auction ID " + freshAuction.getId() + " (" + freshAuction.getItemName() + ") bought directly by " + player.getName() + " for " + buyNowPrice);
            player.closeInventory(); // Close GUI after purchase
            // Consider a global GUI refresh for all viewers, or remove the item from other GUIs if possible
            // For now, other players' GUIs will show the item until they refresh or it naturally expires/is processed by task.

        } else {
            player.sendMessage(localeManager.getMessage("auction.payment-failed-notification-buyer", "item", freshAuction.getItemName()));
        }
    }


    private void handleBidAttempt(Player player, AuctionItem auction) {
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(localeManager.getMessage("auction.cannot-bid-on-own"));
            return;
        }

        if (auction.getHighestBidderUuid() != null && auction.getHighestBidderUuid().equals(player.getUniqueId())) {
            player.sendMessage(localeManager.getMessage("auction.already-highest-bidder"));
            return;
        }

        String anvilTitle = localeManager.getRawMessage("auction.enter-bid-amount", "Ingresa tu puja");
        AnvilInputHelper anvilHelper = new AnvilInputHelper(plugin, player, anvilTitle, bidText -> {
            try {
                double bidAmount = Double.parseDouble(bidText);

                double currentEffectiveBid = (auction.getCurrentBid() != null && auction.getCurrentBid() > 0) ? auction.getCurrentBid() : auction.getInitialPrice();
                // Optional: Minimum bid increment
                // double minIncrement = plugin.getConfigManager().getDouble("auction-settings.min-bid-increment", 1.0);
                // if (bidAmount < currentEffectiveBid + minIncrement && bidAmount != auction.getInitialPrice()) { // Allow bidding initial price if no bids yet
                //    player.sendMessage(localeManager.getMessage("auction.bid-increment-too-small", "amount", String.valueOf(minIncrement)));
                //    return;
                // }


                if (bidAmount <= currentEffectiveBid) {
                    player.sendMessage(localeManager.getMessage("auction.bid-too-low"));
                    return;
                }

                // Check if bid is higher than buy now price if it exists
                if (auction.getBuyNowPrice() != null && auction.getBuyNowPrice() > 0 && bidAmount >= auction.getBuyNowPrice()){
                    player.sendMessage(localeManager.getMessage("auction.bid-higher-than-buynow"));
                    // Potentially trigger buy now logic or just inform player
                    return;
                }

                if (!economyManager.hasEnough(player, bidAmount)) {
                    player.sendMessage(localeManager.getMessage("auction.not-enough-funds-bid"));
                    return;
                }

                // Concurrency check: Re-fetch auction data before updating
                AuctionItem freshAuction = databaseManager.getAuctionById(auction.getId());
                if (freshAuction == null || !freshAuction.getStatus().equals("ACTIVE") || freshAuction.getEndTime() < System.currentTimeMillis()) {
                     player.sendMessage(localeManager.getMessage("auction.error.not-active-or-expired"));
                     Bukkit.getScheduler().runTask(plugin, () -> guiManager.openActiveAuctionsGUI(player, guiManager.getPlayerCurrentPage(player)));
                    return;
                }

                double freshCurrentEffectiveBid = (freshAuction.getCurrentBid() != null && freshAuction.getCurrentBid() > 0) ? freshAuction.getCurrentBid() : freshAuction.getInitialPrice();
                if (bidAmount <= freshCurrentEffectiveBid) {
                    player.sendMessage(localeManager.getMessage("auction.bid-too-low-concurrent")); // Another player bid higher while you were typing
                    Bukkit.getScheduler().runTask(plugin, () -> guiManager.openActiveAuctionsGUI(player, guiManager.getPlayerCurrentPage(player)));
                    return;
                }


                UUID previousBidderUUID = freshAuction.getHighestBidderUuid();
                // double previousBidAmount = freshAuction.getCurrentBid() != null ? freshAuction.getCurrentBid() : 0;

                boolean success = databaseManager.updateAuctionBid(auction.getId(), bidAmount, player.getUniqueId(), player.getName());

                if (success) {
                    player.sendMessage(localeManager.getMessage("auction.bid-accepted", "amount", String.valueOf(bidAmount), "item", freshAuction.getItemName()));

                    // Notify seller
                    Player seller = Bukkit.getPlayer(freshAuction.getSellerUuid());
                    if (seller != null && seller.isOnline()) {
                        seller.sendMessage(localeManager.getMessage("auction.new-bid-notification", "player", player.getName(), "amount", String.valueOf(bidAmount), "item", freshAuction.getItemName()));
                    }

                    // Notify previous bidder (if different from current)
                    if (previousBidderUUID != null && !previousBidderUUID.equals(player.getUniqueId())) {
                        OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(previousBidderUUID);
                        if (previousBidder.isOnline()) {
                            ((Player) previousBidder).sendMessage(localeManager.getMessage("auction.outbid-notification", "item", freshAuction.getItemName(), "new_bid", String.valueOf(bidAmount)));
                        }
                        // Here, you would typically refund the previous bidder if money was held.
                        // Since we are not holding money on bid, no refund needed here.
                    }

                    // Refresh GUI for the player who bid
                    Bukkit.getScheduler().runTask(plugin, () -> guiManager.openActiveAuctionsGUI(player, guiManager.getPlayerCurrentPage(player)));

                } else {
                    player.sendMessage(localeManager.getMessage("error.generic")); // Generic error if DB update fails
                }

            } catch (NumberFormatException ex) {
                player.sendMessage(localeManager.getMessage("auction.invalid-bid-amount"));
            }
        });
        anvilHelper.openAnvil();
    }
}
