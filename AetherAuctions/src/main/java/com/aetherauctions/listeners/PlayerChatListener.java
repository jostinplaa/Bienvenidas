package com.aetherauctions.listeners;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus; // Import AuctionStatus
import com.aetherauctions.gui.GUIManager;
// import com.aetherauctions.gui.rework.NewGUIManager; // Import Removed
import com.aetherauctions.config.MessageManager; // Assuming this path is correct
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.Bukkit; // For Bukkit.getScheduler().runTask

import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final GUIManager guiManager; // Old GUI Manager, for other input states
    private final com.aetherauctions.gui.rework.NewGUIInventoryListener newGuiInventoryListener; // Changed to actual listener
    private final MessageManager messageManager;

    public PlayerChatListener(AetherAuctions plugin) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager();
        this.guiManager = plugin.getGuiManager();
        this.newGuiInventoryListener = plugin.getNewGuiInventoryListener(); // Get instance from plugin
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler(priority = EventPriority.LOWEST) // Process before other chat plugins, and cancel if needed
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if this player is expected to provide bid input via NewGUIInventoryListener's map
        if (newGuiInventoryListener.isPlayerPendingBid(playerId)) {
            event.setCancelled(true);
            Integer auctionId = newGuiInventoryListener.getAndRemovePlayerPendingBidAuctionId(playerId);
            if (auctionId == null) {
                plugin.getLogger().warning("Player " + player.getName() + " was pending bid input (NewGUI), but no auction ID was found.");
                messageManager.sendMessage(player, "internal_error");
                return;
            }

            String bidAmountString = event.getMessage();
            double bidAmount;
            try {
                bidAmount = Double.parseDouble(bidAmountString);
            } catch (NumberFormatException e) {
                messageManager.sendMessage(player, "error_invalid_bid_input_nan", "%input%", bidAmountString);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                AuctionItem auctionItem = auctionManager.getAuction(auctionId); // auctionId would be from newGuiManager path
                if (auctionItem == null || auctionItem.getStatus() != AuctionStatus.ACTIVE) {
                    messageManager.sendMessage(player, "auction_ended_no_longer_exists");
                    return;
                }
                if (auctionItem.getSellerUUID().equals(player.getUniqueId().toString())) {
                    messageManager.sendMessage(player, "cannot_bid_on_own_auction");
                    return;
                }
                if (auctionItem.getBuyNowPrice() > 0 && plugin.getConfigManager().isBuyNowAllowed() && bidAmount >= auctionItem.getBuyNowPrice()) {
                    messageManager.sendMessage(player, "bid_equals_buy_now");
                    auctionManager.buyNow(player, auctionId, true);
                    return;
                }
                double minIncrement = plugin.getConfigManager().getMinBidIncrementAmount();
                double currentEffectiveBid = (auctionItem.getHighestBidderUUID() == null) ? auctionItem.getStartPrice() : auctionItem.getCurrentBid();
                double requiredBid = (auctionItem.getHighestBidderUUID() == null) ? auctionItem.getStartPrice() : currentEffectiveBid + minIncrement;
                if (auctionItem.getHighestBidderUUID() == null && bidAmount < auctionItem.getStartPrice()) {
                     messageManager.sendMessage(player, "bid_too_low_initial", "%min_bid%", String.format("%,.2f", auctionItem.getStartPrice()), "%currency%", plugin.getConfigManager().getCurrencySymbol());
                     return;
                } else if (auctionItem.getHighestBidderUUID() != null && bidAmount < requiredBid) {
                     messageManager.sendMessage(player, "bid_too_low_increment", "%min_bid%", String.format("%,.2f", requiredBid), "%currency%", plugin.getConfigManager().getCurrencySymbol(), "%increment%", String.format("%,.2f", minIncrement));
                     return;
                }
                auctionManager.placeBid(player, auctionItem, bidAmount);
            });
        }
        */
        // Handle other input states from the old GUIManager (e.g., for creating auctions via chat prompts)
        // This 'if' was previously an 'else if'
        if (guiManager.isPlayerPendingBid(playerId)) {
            event.setCancelled(true);
            Integer oldAuctionId = guiManager.getAndRemovePlayerPendingBid(playerId);
            if (oldAuctionId == null) {
                messageManager.sendMessage(player, "internal_error");
                return;
            }
            // The rest of this block referred to 'auctionId' and 'bidAmount' from the newGuiManager block.
            // It needs to use oldAuctionId and parse its own bid amount from event.getMessage().
            String bidAmountString = event.getMessage(); // Make sure this is used for this block
            double bidAmount; // Make sure this is used for this block
            try {
                bidAmount = Double.parseDouble(bidAmountString);
            } catch (NumberFormatException e) {
                messageManager.sendMessage(player, "error_invalid_bid_input_nan", "%input%", bidAmountString);
                return;
            }

            // The BukkitRunnable for old GUI's bid should use oldAuctionId and the local bidAmount
            final Integer finalOldAuctionId = oldAuctionId; // effectively final for lambda
            final double finalBidAmount = bidAmount; // effectively final for lambda

            Bukkit.getScheduler().runTask(plugin, () -> {
                AuctionItem auctionItem = auctionManager.getAuction(finalOldAuctionId); // Use finalOldAuctionId

                if (auctionItem == null || auctionItem.getStatus() != AuctionStatus.ACTIVE) {
                    messageManager.sendMessage(player, "auction_ended_no_longer_exists");
                    return;
                }

                // Apply necessary checks for the old GUI's bid process as well
                if (auctionItem.getSellerUUID().equals(player.getUniqueId().toString())) {
                    messageManager.sendMessage(player, "cannot_bid_on_own_auction");
                    return;
                }
                if (auctionItem.getBuyNowPrice() > 0 && plugin.getConfigManager().isBuyNowAllowed() && finalBidAmount >= auctionItem.getBuyNowPrice()) {
                     messageManager.sendMessage(player, "bid_equals_buy_now");
                     auctionManager.buyNow(player, finalOldAuctionId, true); // Use finalOldAuctionId
                     return;
                }
                double minIncrement = plugin.getConfigManager().getMinBidIncrementAmount();
                double currentEffectiveBid = (auctionItem.getHighestBidderUUID() == null) ? auctionItem.getStartPrice() : auctionItem.getCurrentBid();
                double requiredBid = (auctionItem.getHighestBidderUUID() == null) ? auctionItem.getStartPrice() : currentEffectiveBid + minIncrement;

                if (auctionItem.getHighestBidderUUID() == null && finalBidAmount < auctionItem.getStartPrice()) {
                     messageManager.sendMessage(player, "bid_too_low_initial", "%min_bid%", String.format("%,.2f", auctionItem.getStartPrice()), "%currency%", plugin.getConfigManager().getCurrencySymbol());
                     return;
                } else if (auctionItem.getHighestBidderUUID() != null && finalBidAmount < requiredBid) {
                     messageManager.sendMessage(player, "bid_too_low_increment", "%min_bid%", String.format("%,.2f", requiredBid), "%currency%", plugin.getConfigManager().getCurrencySymbol(), "%increment%", String.format("%,.2f", minIncrement));
                     return;
                }
                // If all validations pass
                auctionManager.placeBid(player, auctionItem, finalBidAmount); // Use finalBidAmount
            });
        } else if (plugin.getPlayerInputState().getOrDefault(playerId, AetherAuctions.PlayerInputState.NONE) != AetherAuctions.PlayerInputState.NONE) {
             // This part handles inputs for the old create auction GUI (duration, start price, buy now price)
            AetherAuctions.PlayerInputState state = plugin.getPlayerInputState().get(playerId);
            event.setCancelled(true);
            String chatMessage = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    switch (state) {
                        case AWAITING_DURATION:
                            long durationMillis = parseDurationString(chatMessage);
                            boolean isVip = player.hasPermission(plugin.getConfigManager().getVipPermission());
                            long maxPlayerDurationSeconds = isVip ? plugin.getConfigManager().getVipExtendedDurationSeconds() : plugin.getConfigManager().getMaxDurationSeconds();

                            if (durationMillis > 0 && durationMillis >= plugin.getConfigManager().getMinDurationSeconds() * 1000L && durationMillis <= maxPlayerDurationSeconds * 1000L) {
                                guiManager.setAuctionDuration(playerId, durationMillis);
                                messageManager.sendMessage(player, "duration_set_success", "%duration%", chatMessage);
                            } else {
                                if(durationMillis <=0 && !chatMessage.equals("0")) messageManager.sendMessage(player, "error_invalid_duration_format", "%value%", chatMessage);
                                else if (durationMillis < plugin.getConfigManager().getMinDurationSeconds() * 1000L) messageManager.sendMessage(player, "duration_too_short", "%duration%", formatDurationMillis(plugin.getConfigManager().getMinDurationSeconds() * 1000L));
                                else messageManager.sendMessage(player, "duration_too_long", "%duration%", formatDurationMillis(maxPlayerDurationSeconds * 1000L));
                            }
                            guiManager.openCreateAuctionGui(player);
                            break;

                        case AWAITING_START_PRICE:
                        case AWAITING_BUY_NOW_PRICE:
                            double price = Double.parseDouble(chatMessage.replace(",", "."));
                            if (price < 0 && !(state == AetherAuctions.PlayerInputState.AWAITING_BUY_NOW_PRICE && (price == 0 || price == -1))) {
                                messageManager.sendMessage(player, state == AetherAuctions.PlayerInputState.AWAITING_START_PRICE ? "error_invalid_bid_price" : "error_invalid_buyout_price", "%value%", chatMessage);
                                guiManager.openCreateAuctionGui(player);
                                break;
                            }

                            if (state == AetherAuctions.PlayerInputState.AWAITING_START_PRICE) {
                                if (price == 0) {
                                    messageManager.sendMessage(player, "start_price_cannot_be_zero");
                                } else if (price > plugin.getConfigManager().getMaxStartPrice()){
                                    messageManager.sendMessage(player, "start_price_too_high", "%max_price%", String.format("%.2f", plugin.getConfigManager().getMaxStartPrice()), "%currency%", plugin.getConfigManager().getCurrencySymbol());
                                }else {
                                    guiManager.setAuctionStartPrice(playerId, price);
                                    messageManager.sendMessage(player, "start_price_set_success", "%price%", String.format("%.2f", price), "%currency%", plugin.getConfigManager().getCurrencySymbol());
                                }
                            } else { // AWAITING_BUY_NOW_PRICE
                                Double startPrice = guiManager.getAuctionStartPrice(playerId);
                                if (price > 0 && startPrice != null && price <= startPrice) {
                                    messageManager.sendMessage(player, "buy_now_must_be_greater");
                                } else if (price == 0 || price == -1) {
                                    guiManager.setAuctionBuyNowPrice(playerId, null);
                                    messageManager.sendMessage(player, "buy_now_price_removed");
                                } else {
                                    guiManager.setAuctionBuyNowPrice(playerId, price);
                                    messageManager.sendMessage(player, "buy_now_price_set_success", "%price%", String.format("%.2f", price), "%currency%", plugin.getConfigManager().getCurrencySymbol());
                                }
                            }
                            guiManager.openCreateAuctionGui(player);
                            break;
                    }
                } catch (NumberFormatException e) {
                    messageManager.sendMessage(player, "error_invalid_number_format", "%value%", chatMessage);
                    if (state == AetherAuctions.PlayerInputState.AWAITING_DURATION || state == AetherAuctions.PlayerInputState.AWAITING_START_PRICE || state == AetherAuctions.PlayerInputState.AWAITING_BUY_NOW_PRICE) {
                       guiManager.openCreateAuctionGui(player);
                    }
                } catch (Exception e) {
                     plugin.getLogger().severe("Error processing chat input for " + player.getName() + " (state " + state + "): " + e.getMessage());
                     e.printStackTrace();
                     messageManager.sendMessage(player, "internal_error");
                } finally {
                    if (state != AetherAuctions.PlayerInputState.NONE) {
                         plugin.getPlayerInputState().remove(playerId);
                    }
                }
            });
        }
    }

    private long parseDurationString(String durationString) {
        if (durationString == null || durationString.trim().isEmpty()) return -1;
        durationString = durationString.trim().toLowerCase();
        long totalMillis = 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([smhd])").matcher(durationString);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            switch (unit) {
                case 'd': totalMillis += value * 24 * 60 * 60 * 1000; break;
                case 'h': totalMillis += value * 60 * 60 * 1000; break;
                case 'm': totalMillis += value * 60 * 1000; break;
                case 's': totalMillis += value * 1000; break;
            }
        }
        if (!found && durationString.matches("\\d+")) {
            try {
                return Long.parseLong(durationString) * 1000;
            } catch (NumberFormatException e) { return -1;}
        }
        return found ? totalMillis : -1;
    }

    private String formatDurationMillis(long millis) {
        if (millis < 0) return "N/A";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }
}
