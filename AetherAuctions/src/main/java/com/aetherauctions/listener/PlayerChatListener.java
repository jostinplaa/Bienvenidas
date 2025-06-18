package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.config.MessageManager; // Import MessageManager
import com.aetherauctions.gui.GUIManager;
import com.aetherauctions.util.InventoryUtil; // Import InventoryUtil
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // Will be replaced by MessageManager
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final AetherAuctions plugin;
    private final GUIManager guiManager;
    private final AuctionManager auctionManager;
    private final MessageManager messageManager; // Add MessageManager

    public PlayerChatListener(AetherAuctions plugin, GUIManager guiManager, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.auctionManager = auctionManager;
        this.messageManager = plugin.getMessageManager(); // Get MessageManager
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        AetherAuctions.PlayerInputState state = plugin.getPlayerInputState().getOrDefault(playerUUID, AetherAuctions.PlayerInputState.NONE);

        if (state == AetherAuctions.PlayerInputState.NONE) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                switch (state) {
                    case AWAITING_DURATION:
                        long durationMillis = parseDuration(message);
                        boolean isVip = player.hasPermission(plugin.getConfigManager().getVipPermission());
                        long maxPlayerDurationSeconds = isVip ? plugin.getConfigManager().getVipExtendedDurationSeconds() : plugin.getConfigManager().getMaxDurationSeconds();

                        if (durationMillis > 0 && durationMillis >= plugin.getConfigManager().getMinDurationSeconds() * 1000L && durationMillis <= maxPlayerDurationSeconds * 1000L) {
                            guiManager.setAuctionDuration(playerUUID, durationMillis);
                            messageManager.sendMessage(player, "duration_set_success", "%duration%", message);
                        } else {
                            if(durationMillis <=0) messageManager.sendMessage(player, "invalid_duration_format");
                            else if (durationMillis < plugin.getConfigManager().getMinDurationSeconds() * 1000L) messageManager.sendMessage(player, "duration_too_short", "%duration%", InventoryUtil.formatTime(plugin.getConfigManager().getMinDurationSeconds() * 1000L));
                            else messageManager.sendMessage(player, "duration_too_long", "%duration%", InventoryUtil.formatTime(maxPlayerDurationSeconds * 1000L)); // Use maxPlayerDurationSeconds
                        }
                        guiManager.openCreateAuctionGui(player);
                        break;

                    case AWAITING_START_PRICE:
                    case AWAITING_BUY_NOW_PRICE:
                        double price = parseDouble(message);
                        if (price < 0) {
                            messageManager.sendMessage(player, "invalid_price_format"); // Or a specific "price_negative"
                            guiManager.openCreateAuctionGui(player);
                            break;
                        }

                        if (state == AetherAuctions.PlayerInputState.AWAITING_START_PRICE) {
                            if (price == 0) {
                                messageManager.sendMessage(player, "start_price_cannot_be_zero"); // New message key
                            } else if (price > plugin.getConfigManager().getMaxStartPrice()){
                                messageManager.sendMessage(player, "start_price_too_high", "%max_price%", String.format("%.2f", plugin.getConfigManager().getMaxStartPrice()), "%currency%", plugin.getConfigManager().getCurrencySymbol());
                            }else {
                                guiManager.setAuctionStartPrice(playerUUID, price);
                                messageManager.sendMessage(player, "start_price_set_success", "%price%", String.format("%.2f", price), "%currency%", plugin.getConfigManager().getCurrencySymbol()); // New key
                            }
                        } else { // AWAITING_BUY_NOW_PRICE
                            Double startPrice = guiManager.getAuctionStartPrice(playerUUID);
                            if (price > 0 && startPrice != null && price <= startPrice) {
                                messageManager.sendMessage(player, "buy_now_must_be_greater");
                            } else if (price == 0) {
                                guiManager.setAuctionBuyNowPrice(playerUUID, null); // Remove buy now
                                messageManager.sendMessage(player, "buy_now_price_removed"); // New key
                            } else {
                                guiManager.setAuctionBuyNowPrice(playerUUID, price);
                                messageManager.sendMessage(player, "buy_now_price_set_success", "%price%", String.format("%.2f", price), "%currency%", plugin.getConfigManager().getCurrencySymbol()); // New key
                            }
                        }
                        guiManager.openCreateAuctionGui(player);
                        break;

                    case AWAITING_BID_AMOUNT:
                        Integer targetAuctionId = plugin.getPlayerTargetAuction().get(playerUUID);
                        if (targetAuctionId == null) {
                            messageManager.sendMessage(player, "internal_error_no_auction_id_bid"); // New key
                            break;
                        }
                        AuctionItem auctionItem = auctionManager.getAuction(targetAuctionId);
                        if (auctionItem == null) {
                             try { auctionItem = plugin.getDatabaseManager().getAuction(targetAuctionId); } catch (Exception e) {}
                        }
                        if (auctionItem == null) {
                            messageManager.sendMessage(player, "auction_ended_no_longer_exists");
                            break;
                        }

                        double bidAmount = parseDouble(message);
                        // auctionManager.placeBid will handle messaging for success/failure
                        boolean success = auctionManager.placeBid(player, auctionItem, bidAmount);
                        if (!success) { // If bid failed, re-open info GUI
                            guiManager.openAuctionInfoGui(player, auctionItem);
                        } else { // If successful, might want to reopen main auction GUI or info GUI
                             guiManager.openAuctionInfoGui(player, auctionManager.getAuction(targetAuctionId)); // Reopen to see updated bid
                        }
                        break;
                }
            } catch (NumberFormatException e) {
                messageManager.sendMessage(player, "invalid_price_format"); // Generic for now
                // Re-open the relevant GUI
                if (state == AetherAuctions.PlayerInputState.AWAITING_DURATION || state == AetherAuctions.PlayerInputState.AWAITING_START_PRICE || state == AetherAuctions.PlayerInputState.AWAITING_BUY_NOW_PRICE) {
                    guiManager.openCreateAuctionGui(player);
                } else if (state == AetherAuctions.PlayerInputState.AWAITING_BID_AMOUNT) {
                    Integer auctionId = plugin.getPlayerTargetAuction().get(playerUUID);
                    if (auctionId != null) {
                        AuctionItem item = auctionManager.getAuction(auctionId);
                        if(item == null) try {item = plugin.getDatabaseManager().getAuction(auctionId);} catch(Exception ex){}
                        if (item != null) guiManager.openAuctionInfoGui(player, item);
                        else player.closeInventory();
                    } else player.closeInventory();
                }
            } catch (Exception e) {
                 plugin.getLogger().severe("Error processing chat input for " + player.getName() + ": " + e.getMessage());
                 e.printStackTrace();
                 messageManager.sendMessage(player, "internal_error");
            } finally {
                plugin.getPlayerInputState().put(playerUUID, AetherAuctions.PlayerInputState.NONE); // Reset state
                if (state == AetherAuctions.PlayerInputState.AWAITING_BID_AMOUNT) {
                     plugin.getPlayerTargetAuction().remove(playerUUID); // Clear target auction after bid attempt
                }
            }
        });
    }

    private double parseDouble(String message) throws NumberFormatException {
        return Double.parseDouble(message.replace(",", ".")); // Allow comma for decimals
    }

    private long parseDuration(String durationString) {
        long totalSeconds = 0;
        durationString = durationString.toLowerCase();

        if (durationString.matches("^\\d+$")) { // Just numbers, assume seconds
            return Long.parseLong(durationString) * 1000;
        }

        // Regex to find number-character pairs (e.g., 1d, 2h, 30m, 45s)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([dhms])").matcher(durationString);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            int value = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            switch (unit) {
                case 'd': totalSeconds += value * 24 * 60 * 60; break;
                case 'h': totalSeconds += value * 60 * 60; break;
                case 'm': totalSeconds += value * 60; break;
                case 's': totalSeconds += value; break;
            }
        }
        return found ? totalSeconds * 1000 : -1; // Return -1 if format is invalid and not just seconds
    }
}
