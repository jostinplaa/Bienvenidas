package dev.jules.proauction.command;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.util.TaxFeeCalculator;
import dev.jules.proauction.model.Auction;
// import org.bukkit.ChatColor; // No longer needed for direct use here
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.OfflinePlayer;

import java.util.HashMap; // Added for placeholders
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List; // Added for command list in sendHelp
import java.util.ArrayList; // Added for command list in sendHelp
import dev.jules.proauction.util.LanguageManager; // Added for sendHelp

public class AuctionCommand implements CommandExecutor {

    private final ProAuction plugin;

    public AuctionCommand(ProAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
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
            case "help": // Explicitly add a help subcommand
                sendHelp(sender);
                break;
            case "reload":
                if (sender.hasPermission("proauction.reload")) {
                    plugin.performReload();
                    plugin.sendMessage(sender, "auction.reload.success");
                } else {
                    plugin.sendMessage(sender, "auction.reload.nopermission");
                }
                break;
            default:
                sendHelp(sender); // For invalid subcommands
                break;
        }
        return true;
    }

    private void handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "error.playeronly");
            return;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("proauction.sell")) {
            plugin.sendMessage(player, "error.nopermission");
            return;
        }

        // Usage: /auction sell <starting_price> [min_increment] [duration_minutes] [buy_now_price]
        if (args.length < 2) {
            // Assuming a key like "auction.sell.usage" exists or will be added to messages_xx.yml
            // For now, let's use a generic approach or assume the help command is the primary source for usage.
            // To keep it simple for this refactor, we can use the existing help key for sell.
            plugin.sendMessage(player, "auction.help.sell");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            plugin.sendMessage(player, "error.mustbeholdingitem");
            return;
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "error.invalidprice");
            return;
        }

        double minIncrement = 1.0;
        if (args.length >= 3) {
            try {
                minIncrement = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "error.invalidincrement");
                return;
            }
        }

        int durationMinutes = 60;
        if (args.length >= 4) { // Index 3 for duration
            try {
                durationMinutes = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "error.invalidduration", "duration", args[3]);
                return;
            }
        }

        double buyNowPrice = -1.0; // Default to not set
        if (args.length >= 5) { // Index 4 for buy_now_price
            try {
                buyNowPrice = Double.parseDouble(args[4]);
                if (buyNowPrice <= 0) {
                    plugin.sendMessage(player, "error.buynowpricenotpositive");
                    return;
                }
                if (buyNowPrice <= startingPrice) {
                    plugin.sendMessage(player, "error.buynowtoolow");
                    return;
                }
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "error.invalidbuynowprice", "price", args[4]);
                return;
            }
        }

        if (startingPrice <= 0 || minIncrement <= 0 || durationMinutes <= 0) {
            plugin.sendMessage(player, "error.pricenotpositive");
            return;
        }

        // Listing Fee Logic
        double listingFeePercentage = plugin.getListingFeePercentage();
        String serverAccountName = plugin.getServerAccountName();

        if (listingFeePercentage > 0) {
            double commissionAmount = TaxFeeCalculator.calculateListingFee(startingPrice, listingFeePercentage).feeAmount;

            // The TaxFeeCalculator now handles the logic of ensuring the fee is at least 0.01 if it was positive but smaller, and rounding.
            // The specific logging for adjustment to 0.01, if done by the calculator, would be inside it.
            // We just use the calculated commissionAmount.
            if (commissionAmount > 0) { // Only proceed if the fee is actually greater than 0 after calculation.
                if (!ProAuction.hasEnough(player, commissionAmount)) {
                    plugin.sendMessage(player, "error.notenoughmoney.listingfee", "fee", ProAuction.format(commissionAmount));
                    return;
                }
                if (!ProAuction.withdrawMoney(player, commissionAmount)) {
                    plugin.sendMessage(player, "error.failedcharge.listingfee", "fee", ProAuction.format(commissionAmount));
                    return;
                }
                plugin.sendMessage(player, "auction.sell.chargedlistingfee", "fee", ProAuction.format(commissionAmount));

                if (serverAccountName != null && !serverAccountName.isEmpty()) {
                    // Using getOfflinePlayer(String) is deprecated but often used for server accounts by name.
                    // A check like serverAccount.hasPlayedBefore() or econ.hasAccount(serverAccountName) is crucial.
                    OfflinePlayer serverAccount = plugin.getServer().getOfflinePlayer(serverAccountName);

                    // Check if the economy plugin knows this account OR if the player has played before.
                    // The latter is a fallback as some economy plugins create accounts on first transaction.
                    boolean accountExistsOrCanBeCreated = ProAuction.getEconomy().hasAccount(serverAccountName) || serverAccount.hasPlayedBefore();

                    if (accountExistsOrCanBeCreated) {
                        if (ProAuction.depositMoney(serverAccount, commissionAmount)) { // serverAccount might work even if hasPlayedBefore is false if econ supports it
                            plugin.logInfo("Deposited listing fee of " + ProAuction.format(commissionAmount) + " to server account " + serverAccountName + " from player " + player.getName());
                        } else {
                            plugin.logWarning("Failed to deposit listing fee of " + ProAuction.format(commissionAmount) + " to server account " + serverAccountName + " from player " + player.getName() + ". The fee was still charged to the player.");
                        }
                    } else {
                        plugin.logWarning("Server account '" + serverAccountName + "' for listing fees not found or has not played before (and no existing economy account). Fee collected from " + player.getName() + " but not deposited to server account.");
                    }
                }
            }
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

        Map<String, String> sellSuccessPlaceholders = new HashMap<>();
        sellSuccessPlaceholders.put("item_name", getItemName(itemInHand));
        sellSuccessPlaceholders.put("auction_id", auctionId.toString().substring(0, 8));
        plugin.sendMessage(player, "auction.sell.success", sellSuccessPlaceholders);

        Map<String, String> sellBroadcastPlaceholders = new HashMap<>();
        sellBroadcastPlaceholders.put("player_name", player.getName());
        sellBroadcastPlaceholders.put("item_name", getItemName(itemInHand));
        plugin.broadcastMessage("auction.sell.broadcast", sellBroadcastPlaceholders);
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("proauction.list")) {
            plugin.sendMessage(sender, "error.nopermission");
            return;
        }

        double salesTaxPercentage = plugin.getSalesTaxPercentage();
        if (salesTaxPercentage > 0) {
            plugin.sendMessage(sender, "auction.help.list.info.salestax", "tax_percentage", String.valueOf(salesTaxPercentage));
        }
        double listingFeePercentage = plugin.getListingFeePercentage();
        if (listingFeePercentage > 0) {
            plugin.sendMessage(sender, "auction.help.list.info.listingfee", "fee_percentage", String.valueOf(listingFeePercentage));
        }
        // Add a blank line for spacing if messages were sent
        if (salesTaxPercentage > 0 || listingFeePercentage > 0) {
             plugin.sendMessage(sender, " "); // Send a space to ensure the line isn't empty after prefix
        }

        Map<UUID, Auction> auctions = plugin.getActiveAuctions();
        if (auctions.isEmpty()) {
            plugin.sendMessage(sender, "auction.list.noauctions");
            return;
        }

        plugin.sendMessage(sender, "auction.list.header"); // New key for "--- Active Auctions ---"
        for (Auction auction : auctions.values()) {
            if (auction.isActive()) {
                Map<String, String> itemFormatPlaceholders = new HashMap<>();
                itemFormatPlaceholders.put("short_id", auction.getAuctionId().toString().substring(0, 8));
                itemFormatPlaceholders.put("item_name", getItemName(auction.getItem()));
                itemFormatPlaceholders.put("item_amount", String.valueOf(auction.getItem().getAmount()));
                itemFormatPlaceholders.put("seller_name", auction.getSellerName());
                itemFormatPlaceholders.put("current_bid", ProAuction.format(auction.getCurrentBid()));
                itemFormatPlaceholders.put("time_remaining", formatTimeRemaining(auction.getEndTimeMillis() - System.currentTimeMillis()));
                // plugin.sendMessage will add the prefix.
                plugin.sendMessage(sender, "auction.list.itemformat", itemFormatPlaceholders);
            }
        }
    }

    private void handleBid(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "error.playeronly");
            return;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("proauction.bid")) {
            plugin.sendMessage(player, "error.nopermission");
            return;
        }

        // /auction bid <auction_id> <bid_amount>
        if (args.length < 3) {
            plugin.sendMessage(player, "auction.bid.usage"); // New key: "auction.bid.usage", value: "&cUsage: /auction bid <auction_id> <bid_amount>"
            return;
        }

        UUID auctionId;
        try {
            auctionId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            String shortId = args[1];
            Auction foundAuction = plugin.getActiveAuctions().values().stream()
                    .filter(auc -> auc.getAuctionId().toString().substring(0, 8).equalsIgnoreCase(shortId))
                    .findFirst().orElse(null);
            if (foundAuction == null) {
                plugin.sendMessage(player, "error.auctionidinvalid");
                return;
            }
            auctionId = foundAuction.getAuctionId();
        }

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "error.invalidbidamount"); // New key: "error.invalidbidamount", value: "&cInvalid bid amount."
            return;
        }

        Auction auction = plugin.getAuction(auctionId);
        if (auction == null || !auction.isActive()) {
            plugin.sendMessage(player, "error.auctionnotfound");
            return;
        }

        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "error.cannotbidown");
            return;
        }

        if (bidAmount <= auction.getCurrentBid() && auction.getHighestBidderUuid() != null) { // Check only if there's an existing bid
             plugin.sendMessage(player, "error.bidtoolow", "current_bid", ProAuction.format(auction.getCurrentBid()));
            return;
        }

        double minNextBid = auction.getCurrentBid() == auction.getStartingPrice() && auction.getHighestBidderUuid() == null ? auction.getStartingPrice() : auction.getCurrentBid() + auction.getMinIncrement();
        if (bidAmount < minNextBid) {
             plugin.sendMessage(player, "error.bidnotenough", "min_next_bid", ProAuction.format(minNextBid));
            return;
        }

        if (!ProAuction.hasEnough(player, bidAmount)) {
            plugin.sendMessage(player, "error.notenoughmoney", "amount", ProAuction.format(bidAmount));
            plugin.sendMessage(player, "auction.bid.tobidder.info");
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
        plugin.addAuction(auction);

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
    }

    private void sendHelp(CommandSender sender) {
        LanguageManager lang = plugin.getLanguageManager();

        plugin.sendMessage(sender, "auction.help.header");
        plugin.sendMessage(sender, "auction.help.availablecommands");
        plugin.sendMessage(sender, " "); // Blank line for spacing

        // Define the commands to show help for
        List<String> commands = new ArrayList<>();
        commands.add("sell");
        commands.add("list");
        commands.add("bid");
        commands.add("ah");
        commands.add("reload"); // Added reload to help

        for (String cmd : commands) {
            String usageKey = "auction.help.detailed." + cmd + ".usage";
            String descriptionKey = "auction.help.detailed." + cmd + ".description";

            // Send usage - plugin.sendMessage handles the prefix and language lookup by key
            plugin.sendMessage(sender, usageKey);

            // Send description
            plugin.sendMessage(sender, descriptionKey);

            // Handle specific notes or additional info for commands
            if (cmd.equals("sell")) {
                plugin.sendMessage(sender, "auction.help.detailed.sell.note");
            } else if (cmd.equals("list")) {
                double salesTaxPercentage = plugin.getSalesTaxPercentage();
                double listingFeePercentage = plugin.getListingFeePercentage();
                if (salesTaxPercentage > 0) {
                    // Send message using key and placeholder
                    plugin.sendMessage(sender, "auction.help.list.info.salestax", "tax_percentage", String.valueOf(salesTaxPercentage));
                }
                if (listingFeePercentage > 0) {
                     plugin.sendMessage(sender, "auction.help.list.info.listingfee", "fee_percentage", String.valueOf(listingFeePercentage));
                }
            }
            plugin.sendMessage(sender, " "); // Blank line after each command's help
        }
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
