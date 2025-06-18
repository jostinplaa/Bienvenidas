package com.example.aetherauctions.gui;

import com.example.aetherauctions.AetherAuctions;
import com.example.aetherauctions.auction.Auction;
import com.example.aetherauctions.auction.AuctionStatus;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainAuctionGui extends BaseGui {

    private int currentPage;
    private final int AUCTIONS_PER_PAGE = 28; // 4 rows of 7 items for auctions (4*7=28)
    private List<Auction> currentAuctions;
    private final NamespacedKey auctionIdKey;

    public MainAuctionGui(AetherAuctions plugin, Player player, int page) {
        super(plugin, player);
        this.currentPage = page;
        this.auctionIdKey = new NamespacedKey(plugin, "auction_id");
    }

    @Override
    public String getTitle() {
        // Assuming getMessages() returns a FileConfiguration for messages.yml
        return ChatColor.translateAlternateColorCodes('&', plugin.getMessages().getString("gui-main-title", "&1&lActive Auctions")
                + " - Page " + (currentPage + 1));
    }

    @Override
    public int getSize() {
        return 54; // 9x6 inventory
    }

    @Override
    protected void setupItems() {
        inventory.clear();

        // Fetch auctions for the current page
        // Note: AuctionManager.getActiveAuctions might need adjustment if it's not 0-indexed for pages
        // Or DatabaseManager.getActiveAuctions needs to be 1-indexed for page if manager expects that.
        // For now, assuming page is 0-indexed for manager/db.
        List<Auction> allActive = plugin.getAuctionManager().getActiveAuctions(this.currentPage + 1, AUCTIONS_PER_PAGE);
        this.currentAuctions = allActive;


        // Populate auction items (slots 0-35, first 4 rows)
        // Slots 0-6, 9-15, 18-24, 27-33
        int itemSlot = 0;
        for (int i = 0; i < AUCTIONS_PER_PAGE; i++) {
            if (i < currentAuctions.size()) {
                Auction auction = currentAuctions.get(i);
                ItemStack displayItem = auction.getItemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();

                if (meta != null) {
                    List<String> lore = new ArrayList<>();
                    String sellerLore = plugin.getMessages().getString("lore-seller", "&7Seller: &e%player%")
                            .replace("%player%", auction.getSellerName());
                    lore.add(ChatColor.translateAlternateColorCodes('&', sellerLore));

                    if (auction.getCurrentBid() > 0) {
                        String currentBidLore = plugin.getMessages().getString("lore-current-bid", "&7Current Bid: &6%bid%")
                                .replace("%bid%", plugin.getAuctionManager().getEconomy().format(auction.getCurrentBid())); // Assuming Economy access
                        lore.add(ChatColor.translateAlternateColorCodes('&', currentBidLore));
                    } else {
                        String noBidsLore = plugin.getMessages().getString("lore-no-bids", "&7Current Bid: &6No bids");
                        lore.add(ChatColor.translateAlternateColorCodes('&', noBidsLore));
                    }
                     if (auction.isBuyNowAvailable()) {
                        String buyNowLore = plugin.getMessages().getString("lore-buy-now-price", "&7Buy Now: &a%price%")
                                .replace("%price%", plugin.getAuctionManager().getEconomy().format(auction.getBuyNowPrice()));
                        lore.add(ChatColor.translateAlternateColorCodes('&', buyNowLore));
                    }

                    String timeLeftLore = plugin.getMessages().getString("lore-time-left", "&7Time Left: &c%time%")
                            .replace("%time%", auction.getTimeLeft());
                    lore.add(ChatColor.translateAlternateColorCodes('&', timeLeftLore));

                    lore.add(ChatColor.translateAlternateColorCodes('&', "&eLeft-click to bid/view."));
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&bRight-click to Buy Now (if available)."));


                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(auctionIdKey, PersistentDataType.INTEGER, auction.getId());
                    displayItem.setItemMeta(meta);
                }
                // Calculate slot: Skips every 8th and 9th slot in a row for borders if desired
                // For now, simple linear fill in the top 4 rows (0-35)
                inventory.setItem(itemSlot++, displayItem);
            } else {
                 // Fill empty auction slots with placeholders if desired, or leave empty
                // inventory.setItem(itemSlot++, GuiUtils.createDisplayItem(Material.GRAY_STAINED_GLASS_PANE, " "));
                itemSlot++; // still increment to maintain layout
            }
        }

        // Fill border slots (optional, for aesthetics)
        // Example: Fill slots 36-44 (row 5) and 46,47,48,50,51,52 (parts of row 6) with glass panes
        ItemStack BORDER_PANE = GuiUtils.createDisplayItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for(int i = AUCTIONS_PER_PAGE; i < 45; i++) { // Fill remaining space in item area + first row of controls
            inventory.setItem(i, BORDER_PANE);
        }


        // Control Buttons (slots 45-53 - bottom row)
        if (currentPage > 0) {
            inventory.setItem(45, GuiUtils.createDisplayItem(Material.ARROW, plugin.getMessages().getString("button-prev-page", "&aPrevious Page")));
        } else {
            inventory.setItem(45, BORDER_PANE);
        }

        inventory.setItem(47, GuiUtils.createDisplayItem(Material.BOOK, plugin.getMessages().getString("button-own-auctions", "&6My Auctions")));
        inventory.setItem(48, GuiUtils.createDisplayItem(Material.EMERALD, plugin.getMessages().getString("button-create-auction", "&aCreate Auction")));
        inventory.setItem(49, GuiUtils.createDisplayItem(Material.SUNFLOWER, plugin.getMessages().getString("button-refresh", "&bRefresh")));
        inventory.setItem(50, GuiUtils.createDisplayItem(Material.WRITABLE_BOOK, plugin.getMessages().getString("button-history", "&7History")));
        // Search and Filter can be added later
        inventory.setItem(51, BORDER_PANE); // Placeholder for Search

        // Check if there's a next page
        // This logic might need refinement based on how total active auctions are counted vs. pageSize
        boolean hasNextPage = plugin.getAuctionManager().getActiveAuctions(this.currentPage + 2, AUCTIONS_PER_PAGE).size() > 0;
        if (hasNextPage) {
             inventory.setItem(53, GuiUtils.createDisplayItem(Material.ARROW, plugin.getMessages().getString("button-next-page", "&aNext Page")));
        } else {
            inventory.setItem(53, BORDER_PANE);
        }

        // Fill remaining control slots with border
        inventory.setItem(46, BORDER_PANE);
        inventory.setItem(52, BORDER_PANE);

    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        String displayName = meta.getDisplayName();

        // Check control buttons first by display name (can be brittle, PDC on buttons is better)
        if (displayName.equals(ChatColor.translateAlternateColorCodes('&',plugin.getMessages().getString("button-prev-page", "&aPrevious Page")))) {
            if (currentPage > 0) {
                new MainAuctionGui(plugin, player, currentPage - 1).open();
            }
        } else if (displayName.equals(ChatColor.translateAlternateColorCodes('&',plugin.getMessages().getString("button-next-page", "&aNext Page")))) {
            // Re-check hasNextPage condition or assume button presence means it's valid
             new MainAuctionGui(plugin, player, currentPage + 1).open();
        } else if (displayName.equals(ChatColor.translateAlternateColorCodes('&',plugin.getMessages().getString("button-refresh", "&bRefresh")))) {
            new MainAuctionGui(plugin, player, currentPage).open();
        } else if (displayName.equals(ChatColor.translateAlternateColorCodes('&',plugin.getMessages().getString("button-create-auction", "&aCreate Auction")))) {
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Placeholder: Use /subasta crear <precio> [compra_directa] while holding an item.");
            // Later: new CreateAuctionGui(plugin, player).open();
        } else if (displayName.equals(ChatColor.translateAlternateColorCodes('&',plugin.getMessages().getString("button-own-auctions", "&6My Auctions")))) {
             player.sendMessage(ChatColor.GOLD + "Placeholder: My Auctions GUI will open here.");
            // Later: new PlayerAuctionsGui(plugin, player, 0).open();
        } else if (displayName.equals(ChatColor.translateAlternateColorCodes('&',plugin.getMessages().getString("button-history", "&7History")))) {
             player.sendMessage(ChatColor.GOLD + "Placeholder: Auction History GUI will open here.");
            // Later: new PlayerHistoryGui(plugin, player, 0).open();
        } else {
            // Check if it's an auction item via PersistentDataContainer
            if (meta.getPersistentDataContainer().has(auctionIdKey, PersistentDataType.INTEGER)) {
                int auctionId = meta.getPersistentDataContainer().get(auctionIdKey, PersistentDataType.INTEGER);
                Auction clickedAuction = plugin.getAuctionManager().getAuction(auctionId); // Fetch fresh auction data

                if(clickedAuction == null || clickedAuction.getStatus() != AuctionStatus.ACTIVE) {
                    player.sendMessage(ChatColor.RED + "This auction is no longer available.");
                    new MainAuctionGui(plugin, player, currentPage).open(); // Refresh
                    return;
                }

                if (event.isLeftClick()) {
                    // Placeholder for Bid GUI or direct bid command
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "You want to bid on auction #" + auctionId + " for item: " + clickedAuction.getItemName());
                    player.sendMessage(ChatColor.YELLOW + "Current bid: " + plugin.getAuctionManager().getEconomy().format(clickedAuction.getCurrentBid()) + ". Min next bid approx: " + plugin.getAuctionManager().getEconomy().format(clickedAuction.getCurrentBid() + plugin.getConfig().getDouble("minimum-bid-increment")));
                    player.sendMessage(ChatColor.YELLOW + "Type /subasta pujar " + auctionId + " <amount>");
                    // Later: new BidGui(plugin, player, clickedAuction).open();
                } else if (event.isRightClick()) {
                    if (clickedAuction.isBuyNowAvailable()) {
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "You want to Buy Now auction #" + auctionId + " for " + plugin.getAuctionManager().getEconomy().format(clickedAuction.getBuyNowPrice()));
                         player.sendMessage(ChatColor.GREEN + "Type /subasta comprar " + auctionId);
                        // Later: new ConfirmPurchaseGui(plugin, player, clickedAuction).open();
                    } else {
                        player.sendMessage(ChatColor.RED + "This item is not available for Buy Now.");
                    }
                }
            }
        }
    }
     // Helper to get an auction by its ID from the currently loaded list (if needed, but usually fetch fresh)
    private Auction getAuctionByIdFromCurrentList(int id) {
        if (currentAuctions == null) return null;
        return currentAuctions.stream().filter(a -> a.getId() == id).findFirst().orElse(null);
    }
}
