package dev.jules.proauction.command;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.model.Auction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AuctionCommand implements CommandExecutor {

    private final ProAuction plugin;

    public AuctionCommand(ProAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender); // Uses plugin.sendMessage internally now
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "sell":
                handleSell(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "bid":
                handleBid(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, ChatColor.RED + "Only players can sell items.");
            return;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("proauction.sell")) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have permission to sell items.");
            return;
        }

        // /auction sell <starting_price> [min_increment] [duration_minutes] [buy_now_price]
        if (args.length < 2) {
            plugin.sendMessage(player, ChatColor.RED + "Usage: /auction sell <starting_price> [min_increment] [duration_minutes] [buy_now_price]");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            plugin.sendMessage(player, ChatColor.RED + "You must be holding an item to auction it.");
            return;
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, ChatColor.RED + "Invalid starting price.");
            return;
        }

        double minIncrement = 1.0;
        if (args.length >= 3) {
            try {
                minIncrement = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, ChatColor.RED + "Invalid minimum increment.");
                return;
            }
        }

        int durationMinutes = 60;
        if (args.length >= 4) { // Index 3 for duration
            try {
                durationMinutes = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, ChatColor.RED + "Invalid duration: " + args[3]);
                return;
            }
        }

        double buyNowPrice = -1.0; // Default to not set
        if (args.length >= 5) { // Index 4 for buy_now_price
            try {
                buyNowPrice = Double.parseDouble(args[4]);
                if (buyNowPrice <= 0) {
                    plugin.sendMessage(player, ChatColor.RED + "Buy Now price must be positive if set.");
                    return;
                }
                if (buyNowPrice <= startingPrice) {
                    plugin.sendMessage(player, ChatColor.RED + "Buy Now price must be greater than the starting price.");
                    return;
                }
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, ChatColor.RED + "Invalid Buy Now price: " + args[4]);
                return;
            }
        }

        if (startingPrice <= 0 || minIncrement <= 0 || durationMinutes <= 0) {
            plugin.sendMessage(player, ChatColor.RED + "Starting price, increment, and duration must be positive.");
            return;
        }

        // Listing Fee Logic
        double listingFee = plugin.getListingFee();
        if (listingFee > 0) {
            if (!ProAuction.hasEnough(player, listingFee)) {
                plugin.sendMessage(player, ChatColor.RED + "You do not have enough money to pay the listing fee of " + ProAuction.format(listingFee) + ".");
                return;
            }
            if (!ProAuction.withdrawMoney(player, listingFee)) {
                plugin.sendMessage(player, ChatColor.RED + "Failed to charge the listing fee of " + ProAuction.format(listingFee) + ". Please try again.");
                plugin.logWarning("Failed to withdraw listing fee " + ProAuction.format(listingFee) + " from " + player.getName() + " even after hasEnough check.");
                return;
            }
            plugin.sendMessage(player, ChatColor.YELLOW + "You have been charged a listing fee of " + ProAuction.format(listingFee) + ".");
        }

        UUID auctionId = UUID.randomUUID();
        long endTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);

        Auction auction = new Auction(
                auctionId,
                itemInHand.clone(), // Store a clone
                player.getUniqueId(),
                player.getName(),
                startingPrice,
                minIncrement,
                endTimeMillis,
                buyNowPrice // Added buyNowPrice
        );

        plugin.addAuction(auction);
        player.getInventory().setItemInMainHand(null); // Remove item from hand

        plugin.sendMessage(player, ChatColor.GREEN + "Auction started for " + getItemName(itemInHand) + " with ID: " + auctionId.toString().substring(0, 8));
        if (plugin.getSalesTaxPercentage() > 0 || plugin.getCommissionPercentage() > 0) {
            plugin.sendMessage(player, ChatColor.GRAY + "Note: If sold, applicable sales tax and commission will be deducted from the final sale price.");
        }
        plugin.broadcastMessage(ChatColor.YELLOW + player.getName() + " has started an auction for " + getItemName(itemInHand) + "!");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("proauction.list")) {
            plugin.sendMessage(sender, ChatColor.RED + "You don't have permission to list auctions.");
            return;
        }

        Map<UUID, Auction> auctions = plugin.getActiveAuctions();
        if (auctions.isEmpty()) {
            plugin.sendMessage(sender, ChatColor.YELLOW + "No active auctions.");
            return;
        }

        plugin.sendMessage(sender, ChatColor.GOLD + "--- Active Auctions ---"); // Prefix will be added by sendMessage
        for (Auction auction : auctions.values()) {
            if (auction.isActive()) {
                // Send message without prefix for list items, then prefix is applied by sendMessage
                sender.sendMessage(plugin.getMessagePrefix() + String.format(ChatColor.GRAY + "ID: %s | Item: %s (%d) | Seller: %s | Bid: %s | Ends in: %s",
                        auction.getAuctionId().toString().substring(0, 8),
                        getItemName(auction.getItem()),
                        auction.getItem().getAmount(),
                        auction.getSellerName(),
                        ProAuction.format(auction.getCurrentBid()),
                        formatTimeRemaining(auction.getEndTimeMillis() - System.currentTimeMillis())
                )); // Manual prefixing here for non-plugin.sendMessage
            }
        }
    }

    private void handleBid(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, ChatColor.RED + "Only players can bid on items.");
            return;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("proauction.bid")) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have permission to bid on auctions.");
            return;
        }

        // /auction bid <auction_id> <bid_amount>
        if (args.length < 3) {
            plugin.sendMessage(player, ChatColor.RED + "Usage: /auction bid <auction_id> <bid_amount>");
            return;
        }

        UUID auctionId;
        try {
            // Try to find full UUID first
            auctionId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            // Try to find by short ID
            String shortId = args[1];
            Auction foundAuction = plugin.getActiveAuctions().values().stream()
                    .filter(auc -> auc.getAuctionId().toString().substring(0, 8).equalsIgnoreCase(shortId))
                    .findFirst().orElse(null);
            if (foundAuction == null) {
                plugin.sendMessage(player, ChatColor.RED + "Invalid auction ID format or auction not found by short ID.");
                return;
            }
            auctionId = foundAuction.getAuctionId();
        }


        double bidAmount;
        try {
            bidAmount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, ChatColor.RED + "Invalid bid amount.");
            return;
        }

        Auction auction = plugin.getAuction(auctionId);
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, ChatColor.RED + "Auction not found or has ended.");
            return;
        }

        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, ChatColor.RED + "You cannot bid on your own auction.");
            return;
        }

        if (bidAmount <= auction.getCurrentBid()) {
             plugin.sendMessage(player, ChatColor.RED + "Your bid must be higher than the current bid (" + ProAuction.format(auction.getCurrentBid()) + ").");
            return;
        }

        double minNextBid = auction.getCurrentBid() == auction.getStartingPrice() && auction.getHighestBidderUuid() == null ? auction.getStartingPrice() : auction.getCurrentBid() + auction.getMinIncrement();
        if (bidAmount < minNextBid) {
            plugin.sendMessage(player, ChatColor.RED + "Your bid must be at least " + ProAuction.format(minNextBid) + " (current bid + min increment).");
            return;
        }


        if (!ProAuction.hasEnough(player, bidAmount)) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have enough money to place this bid. You need " + ProAuction.format(bidAmount) + ".");
            plugin.sendMessage(player, ChatColor.GRAY + "(Funds will be taken when the auction ends if you win)");
            return;
        }

        // Notify previous bidder
        if (auction.getHighestBidderUuid() != null && !auction.getHighestBidderUuid().equals(player.getUniqueId())) {
            OfflinePlayer previousOfflineBidder = plugin.getServer().getOfflinePlayer(auction.getHighestBidderUuid());
            if (previousOfflineBidder.isOnline() && previousOfflineBidder.getPlayer() != null) {
                // Not using plugin.sendMessage for other player's direct notification if prefix is sender-specific.
                // However, if prefix is global, this is fine. Assuming global for now.
                plugin.sendMessage(previousOfflineBidder.getPlayer(), ChatColor.YELLOW + "You have been outbid on the auction for " + getItemName(auction.getItem()) + " by " + player.getName() + ".");
            }
            // Note: Money is not refunded here. It's handled at the end or if the new bid fails at auction end.
        }

        auction.setHighestBid(bidAmount, player.getUniqueId(), player.getName());
        plugin.addAuction(auction); // Re-add to update the map entry, which also saves

        plugin.sendMessage(player, ChatColor.GREEN + "You are now the highest bidder for " + getItemName(auction.getItem()) + " with a bid of " + ProAuction.format(bidAmount) + "!");

        Player seller = plugin.getServer().getPlayer(auction.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            plugin.sendMessage(seller, ChatColor.AQUA + player.getName() + " has bid " + ProAuction.format(bidAmount) + " on your auction for " + getItemName(auction.getItem()) + ".");
        }
    }


    private void sendHelp(CommandSender sender) {
        // These are sent without prefix and then prefix is applied by sendMessage
        plugin.sendMessage(sender, ChatColor.GOLD + "--- ProAuction Help ---");
        plugin.sendMessage(sender, ChatColor.YELLOW + "/auction sell <price> [increment] [duration_minutes] " + ChatColor.GRAY + "- Sell the item in your hand.");
        plugin.sendMessage(sender, ChatColor.YELLOW + "/auction list " + ChatColor.GRAY + "- List active auctions.");
        plugin.sendMessage(sender, ChatColor.YELLOW + "/auction bid <id> <amount> " + ChatColor.GRAY + "- Bid on an auction.");
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().toString().toLowerCase().replace('_', ' ');
    }

    private String formatTimeRemaining(long millis) {
        if (millis < 0) return "Ended";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
