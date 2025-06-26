package com.aetherauctions.managers;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.PendingReward;
import com.aetherauctions.model.PendingReward.RewardType;
import com.aetherauctions.storage.AuctionStorage; // Added import
import com.aetherauctions.config.ConfigManager;   // Added import
import com.aetherauctions.config.MessageManager; // Added import
import com.aetherauctions.util.SerializationUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RewardManager {

    private final AetherAuctions plugin;
    private final AuctionStorage auctionStorage;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Economy economy;
    private final Logger logger;

    public RewardManager(AetherAuctions plugin, AuctionStorage auctionStorage, ConfigManager configManager, MessageManager messageManager, Economy economy) {
        this.plugin = plugin;
        this.auctionStorage = auctionStorage;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.economy = economy;
        this.logger = plugin.getLogger();
    }

    public void processPendingRewards(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PendingReward> rewards = auctionStorage.getPendingRewardsByOwner(player.getUniqueId())
                    .stream()
                    .filter(r -> !r.isDelivered())
                    .collect(Collectors.toList());

            if (rewards.isEmpty()) {
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                messageManager.sendMessage(player, "reward_processing_on_join");
                int successfullyClaimedCount = 0;
                int remainingPendingCount = 0;

                for (PendingReward reward : rewards) {
                    boolean success = deliverRewardInternal(player, reward, true);
                    if (success) {
                        successfullyClaimedCount++;
                    } else {
                        remainingPendingCount++;
                    }
                }

                if (remainingPendingCount > 0) {
                    messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(remainingPendingCount));
                }
                // Optionally, send a summary of what was auto-claimed, or rely on individual messages from deliverRewardInternal
            });
        });
    }

    public void attemptClaimNextReward(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PendingReward> rewards = auctionStorage.getPendingRewardsByOwner(player.getUniqueId())
                    .stream()
                    .filter(r -> !r.isDelivered())
                    .collect(Collectors.toList()); // Fetches all, but we'll process one

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rewards.isEmpty()) {
                    messageManager.sendMessage(player, "rewards_none_pending");
                    return;
                }

                PendingReward rewardToClaim = rewards.get(0); // Get the oldest one
                boolean success = deliverRewardInternal(player, rewardToClaim, false);

                if (success) {
                    // Success message is handled by deliverRewardInternal
                } else {
                    // Failure message is handled by deliverRewardInternal
                    // If it failed, it means it's likely still pending, unless it was an unrecoverable error
                    // (which deliverRewardInternal should log)
                    // We can re-check if it's truly still pending for the message.
                    // Use getRewardId() as corrected in other parts of the class
                    if (auctionStorage.getPendingReward(rewardToClaim.getRewardId()) != null &&
                        !auctionStorage.getPendingReward(rewardToClaim.getRewardId()).isDelivered()){
                         messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(rewards.size()));
                    }
                }
            });
        });
    }

    private boolean deliverRewardInternal(Player player, PendingReward reward, boolean isAutoProcess) {
        String currencySymbol = configManager.getCurrencySymbol();
        boolean success = false;

        switch (reward.getType()) { // Changed to use correct getter: getType()
            case ITEM_AUCTION_WON:
            case ITEM_AUCTION_RETURNED:
                ItemStack item = reward.getItemToClaim(); // Changed to use correct getter: getItemToClaim()
                if (item == null || item.getType() == Material.AIR) {
                    // If getItemToClaim() returns null, it means it was stored as null or deserialization failed at AuctionStorage
                    logger.severe("[RewardManager] Item to claim is null or AIR for reward ID: " + reward.getRewardId() + ". This might indicate corrupt data or an issue during reward creation/loading.");
                    try {
                        auctionStorage.markRewardDelivered(reward.getRewardId()); // Mark as delivered to prevent retries
                    } catch (java.sql.SQLException e) {
                        logger.log(java.util.logging.Level.SEVERE, "[RewardManager] CRITICAL: Failed to mark CORRUPT ITEM reward ID " + reward.getRewardId() +
                                   " as delivered. Error: " + e.getMessage(), e);
                    }
                    return false; // Cannot deliver a non-existent item.
                }

                PlayerInventory inventory = player.getInventory();
                if (inventory.firstEmpty() != -1) {
                    inventory.addItem(item);
                    try {
                        auctionStorage.markRewardDelivered(reward.getRewardId()); // Changed to use correct getter: getRewardId()
                        String messageKey = isAutoProcess ? "reward_auto_item_received" : "reward_claim_successful_item";
                        messageManager.sendMessage(player, messageKey, "%item_name%", item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().toString());
                        success = true;
                    } catch (java.sql.SQLException e) {
                        logger.log(java.util.logging.Level.SEVERE, "[RewardManager] CRITICAL: Successfully delivered ITEM reward ID " + reward.getRewardId() +
                                   " (Item: " + item.getType() + ") to player " + player.getName() + " (" + player.getUniqueId() +
                                   ") BUT FAILED TO MARK AS DELIVERED IN DATABASE. This may lead to duplicate delivery. Error: " + e.getMessage(), e);
                        success = false; // Overall operation failed due to DB error after delivery
                    }
                } else {
                    if (!isAutoProcess) { // Only send inventory full if manually claiming
                        messageManager.sendMessage(player, "reward_claim_failed_inventory", "%item_name%", item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().toString());
                    }
                    success = false;
                }
                break;

            case MONEY_AUCTION_SOLD:
            case MONEY_BID_REFUND:
                if (economy != null) {
                    double amountToDeliver = reward.getMoneyToClaim(); // Changed to use correct getter: getMoneyToClaim()
                    net.milkbowl.vault.economy.EconomyResponse econResponse = economy.depositPlayer(player, amountToDeliver);
                    if (econResponse.transactionSuccess()) {
                        try {
                            auctionStorage.markRewardDelivered(reward.getRewardId()); // Changed to use correct getter: getRewardId()
                            String messageKey = isAutoProcess ? "reward_auto_money_received" : "reward_claim_successful_money";
                            messageManager.sendMessage(player, messageKey, "%amount%", String.valueOf(amountToDeliver), "%currency%", currencySymbol);
                            success = true;
                        } catch (java.sql.SQLException e) {
                            logger.log(java.util.logging.Level.SEVERE, "[RewardManager] CRITICAL: Successfully delivered MONEY reward ID " + reward.getRewardId() +
                                       " (Amount: " + amountToDeliver + ") to player " + player.getName() + " (" + player.getUniqueId() +
                                       ") BUT FAILED TO MARK AS DELIVERED IN DATABASE. This may lead to duplicate delivery. Error: " + e.getMessage(), e);
                            success = false; // Overall operation failed due to DB error after delivery
                        }
                    } else {
                        // Log existing general warning + new detailed one for persistent failures
                        logger.warning("[RewardManager] Vault transaction failed for player " + player.getName() + " for reward ID " + reward.getRewardId() + ": " + econResponse.errorMessage);
                        logger.log(java.util.logging.Level.WARNING, "[RewardManager] Persistent Vault transaction failure for reward ID: " + reward.getRewardId() +
                                   " to player " + player.getName() + " (" + player.getUniqueId() + "). Amount: " + amountToDeliver +
                                   ". Vault Error: " + econResponse.errorMessage + ". Reward remains pending.");
                        if (!isAutoProcess) { // Only send econ error if manually claiming
                             messageManager.sendMessage(player, "reward_claim_failed_money", "%amount%", String.valueOf(amountToDeliver), "%currency%", currencySymbol);
                        }
                        success = false;
                    }
                } else {
                    logger.severe("[RewardManager] Vault (Economy) is not available. Cannot process money reward ID: " + reward.getRewardId());
                    success = false; // Vault not found, cannot process
                }
                break;
        }
        return success;
    }

    public void scheduleOldDeliveredRewardCleanup() {
        long delayTicks = 20L * 60 * 5; // 5 minutes after startup
        long periodTicks = 20L * 60 * 60 * 24; // Run once a day
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            logger.info("Running scheduled cleanup of old delivered rewards...");
            int daysToKeep = configManager.getDeliveredRewardsKeptDays();
            if (daysToKeep <= 0) {
                logger.info("Old reward cleanup disabled (daysToKeep is " + daysToKeep + ").");
                return;
            }
            try {
                long daysToKeepMillis = (long) daysToKeep * 24L * 60L * 60L * 1000L;
                long olderThanTimestamp = System.currentTimeMillis() - daysToKeepMillis;
                int deletedCount = auctionStorage.deleteOldDeliveredRewards(olderThanTimestamp);
                if (deletedCount > 0) {
                    logger.info("Deleted " + deletedCount + " old delivered rewards (older than " + daysToKeep + " days).");
                } else {
                    logger.info("No old delivered rewards found to delete (older than " + daysToKeep + " days).");
                }
            } catch (java.sql.SQLException e) {
                logger.log(java.util.logging.Level.SEVERE, "Error during scheduled cleanup of old delivered rewards.", e);
            }
        }, delayTicks, periodTicks);
    }
}
