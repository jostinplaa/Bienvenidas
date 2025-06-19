package com.aetherauctions.listeners;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.gui.GUIManager;
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
    private final GUIManager guiManager;
    private final MessageManager messageManager;

    public PlayerChatListener(AetherAuctions plugin) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager(); // Get from main plugin class
        this.guiManager = plugin.getGuiManager();       // Get from main plugin class
        this.messageManager = plugin.getMessageManager(); // Get from main plugin class
    }

    @EventHandler(priority = EventPriority.LOWEST) // Process before other chat plugins, and cancel if needed
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if this player is expected to provide bid input
        if (guiManager.isPlayerPendingBid(playerId)) {
            event.setCancelled(true); // Cancel the chat event so the message doesn't appear publicly

            Integer auctionId = guiManager.getAndRemovePlayerPendingBid(playerId); // Retrieve and clear pending state
            if (auctionId == null) {
                // Should not happen if state was managed correctly, but as a safeguard:
                messageManager.sendMessage(player, "internal_error"); // Or a more specific "no_pending_bid_auction"
                return;
            }

            String message = event.getMessage();
            double bidAmount;
            try {
                bidAmount = Double.parseDouble(message);
            } catch (NumberFormatException e) {
                messageManager.sendMessage(player, "error_invalid_bid_input_nan", "%input%", message);
                // Optionally, re-open auction details GUI or main GUI
                // For now, just informing and player has to re-initiate bid.
                AuctionItem auctionItem = auctionManager.getAuction(auctionId);
                if (auctionItem == null) {
                    try { auctionItem = plugin.getDatabaseManager().getAuction(auctionId); } catch (Exception dbEx) {}
                }
                if (auctionItem != null) {
                    // Run synchronously as it involves opening GUI
                    AuctionItem finalAuctionItem = auctionItem; // effectively final for lambda
                    Bukkit.getScheduler().runTask(plugin, () -> guiManager.openAuctionInfoGui(player, finalAuctionItem));
                } else {
                     Bukkit.getScheduler().runTask(plugin, () -> guiManager.openNewMainAuctionGui(player, 0));
                }
                return;
            }

            // Perform bidding logic synchronously as it involves economy and potentially GUI updates
            AuctionItem finalAuctionItemForLambda = auctionManager.getAuction(auctionId); // Re-fetch for fresh state
             if (finalAuctionItemForLambda == null) {
                 try { finalAuctionItemForLambda = plugin.getDatabaseManager().getAuction(auctionId); } catch (Exception dbEx) {}
             }
            final AuctionItem auctionToBidOn = finalAuctionItemForLambda;


            Bukkit.getScheduler().runTask(plugin, () -> {
                if (auctionToBidOn == null) {
                    messageManager.sendMessage(player, "auction_ended_no_longer_exists");
                    guiManager.openNewMainAuctionGui(player, 0);
                    return;
                }
                boolean bidSuccess = auctionManager.placeBid(player, auctionToBidOn, bidAmount);

                // Re-open auction info GUI regardless of success to show updated state or error context
                // AuctionManager.placeBid handles sending success/failure messages
                // If bid was successful, AuctionManager also calls GUI refresh which will update this player's GUI too.
                // If not, we manually reopen to show the state (e.g. if bid was too low).
                if (!bidSuccess) {
                    guiManager.openAuctionInfoGui(player, auctionToBidOn);
                }
            });
        }
        // Handle other input states like AWAITING_DURATION, AWAITING_START_PRICE etc. from previous implementations
        // This part is copied from existing PlayerChatListener logic for other inputs.
        else if (plugin.getPlayerInputState().getOrDefault(playerId, AetherAuctions.PlayerInputState.NONE) != AetherAuctions.PlayerInputState.NONE) {
            AetherAuctions.PlayerInputState state = plugin.getPlayerInputState().get(playerId);
            event.setCancelled(true);
            String chatMessage = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    switch (state) {
                        case AWAITING_DURATION:
                            long durationMillis = parseDurationString(chatMessage); // Use local or shared helper
                            boolean isVip = player.hasPermission(plugin.getConfigManager().getVipPermission());
                            long maxPlayerDurationSeconds = isVip ? plugin.getConfigManager().getVipExtendedDurationSeconds() : plugin.getConfigManager().getMaxDurationSeconds();

                            if (durationMillis > 0 && durationMillis >= plugin.getConfigManager().getMinDurationSeconds() * 1000L && durationMillis <= maxPlayerDurationSeconds * 1000L) {
                                guiManager.setAuctionDuration(playerId, durationMillis);
                                messageManager.sendMessage(player, "duration_set_success", "%duration%", chatMessage);
                            } else {
                                if(durationMillis <=0 && !chatMessage.equals("0")) messageManager.sendMessage(player, "error_invalid_duration_format", "%value%", chatMessage); // Use the new message key
                                else if (durationMillis < plugin.getConfigManager().getMinDurationSeconds() * 1000L) messageManager.sendMessage(player, "duration_too_short", "%duration%", formatDurationMillis(plugin.getConfigManager().getMinDurationSeconds() * 1000L));
                                else messageManager.sendMessage(player, "duration_too_long", "%duration%", formatDurationMillis(maxPlayerDurationSeconds * 1000L));
                            }
                            guiManager.openCreateAuctionGui(player);
                            break;

                        case AWAITING_START_PRICE:
                        case AWAITING_BUY_NOW_PRICE:
                            double price = Double.parseDouble(chatMessage.replace(",", "."));
                            if (price < 0 && !(state == AetherAuctions.PlayerInputState.AWAITING_BUY_NOW_PRICE && (price == 0 || price == -1))) { // Allow 0 or -1 for buynow to remove it
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
                        // Note: AWAITING_BID_AMOUNT is now handled by the isPlayerPendingBid block
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
                    if (state != AetherAuctions.PlayerInputState.NONE) { // Only reset if it was a handled state
                         plugin.getPlayerInputState().remove(playerId); // Reset state only if it was one of these specific input states
                    }
                }
            });
        }
    }

    // Helper method to parse duration strings like "1d", "2h30m", "3600s" into milliseconds
    // This can be made more robust or moved to a utility class
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
        if (!found && durationString.matches("\\d+")) { // Only numbers, assume seconds and convert to millis
            try {
                return Long.parseLong(durationString) * 1000;
            } catch (NumberFormatException e) { return -1;}
        }
        return found ? totalMillis : -1;
    }

    // Simplified formatter for messages, could be expanded or use GUIManager's one
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
