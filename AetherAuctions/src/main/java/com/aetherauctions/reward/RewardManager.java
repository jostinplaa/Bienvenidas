package com.aetherauctions.reward;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.PendingReward;
import com.aetherauctions.storage.AuctionStorage;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
// import org.bukkit.inventory.PlayerInventory; // Not directly used
import net.milkbowl.vault.economy.EconomyResponse;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class RewardManager {
    private final AetherAuctions plugin;
    private final AuctionStorage auctionStorage;
    private final MessageManager msgManager;
    private final Economy econ;

    public RewardManager(AetherAuctions plugin) {
        this.plugin = plugin;
        this.auctionStorage = plugin.getAuctionStorage();
        this.msgManager = plugin.getMessageManager();
        this.econ = AetherAuctions.getEconomy();
    }

    public void processPendingRewards(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        List<PendingReward> rewards = auctionStorage.getPendingRewardsByOwner(playerId);

        if (rewards.isEmpty()) {
            // msgManager.sendMessage(player, "rewards_none_pending");
            return;
        }

        msgManager.sendMessage(player, "rewards_checking_pending");
        int deliveredCount = 0;
        int failedToDeliver = 0;

        for (PendingReward reward : rewards) {
            boolean success = false;
            String rewardItemName = "N/A";

            if (reward.getItemToClaim() != null) {
                rewardItemName = InventoryUtil.formatMaterialName(reward.getItemToClaim().getType());
            }

            if (reward.getType() == PendingReward.RewardType.ITEM_AUCTION_WON ||
                reward.getType() == PendingReward.RewardType.ITEM_AUCTION_RETURNED) {
                ItemStack itemToGive = reward.getItemToClaim();
                if (itemToGive != null) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(itemToGive.clone());
                        success = true;
                        // Send specific reason message
                        msgManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                    } else {
                        msgManager.sendMessage(player, "rewards_delivery_item_inventory_full", "%item_name%", rewardItemName);
                        failedToDeliver++;
                    }
                } else {
                     plugin.getLogger().warning("PendingReward de ítem (ID: " + reward.getRewardId() + ") para " + player.getName() + " tiene un ItemStack null. No se puede entregar.");
                     success = true;
                }
            } else if (reward.getType() == PendingReward.RewardType.MONEY_AUCTION_SOLD ||
                       reward.getType() == PendingReward.RewardType.MONEY_BID_REFUND) {
                double amount = reward.getMoneyToClaim();
                if (amount > 0) {
                    EconomyResponse tx = econ.depositPlayer(player, amount);
                    if (tx.transactionSuccess()) {
                        success = true;
                        msgManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                    } else {
                        msgManager.sendMessage(player, "rewards_delivery_money_error", "%amount%", String.valueOf(amount), "%reason%", tx.errorMessage);
                        failedToDeliver++;
                    }
                } else {
                    success = true;
                }
            }

            if (success) {
                try {
                    auctionStorage.markRewardDelivered(reward.getRewardId());
                    deliveredCount++;
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Fallo al marcar PendingReward ID: " + reward.getRewardId() + " como entregada para " + player.getName(), e);
                }
            }
        }

        if (deliveredCount > 0) {
            msgManager.sendMessage(player, "rewards_delivered_summary", "%count%", String.valueOf(deliveredCount));
        }
        if (failedToDeliver > 0) { // Changed from deliveredCount < rewards.size() to be more explicit
             msgManager.sendMessage(player, "rewards_some_still_pending", "%remaining%", String.valueOf(failedToDeliver));
        }
    }

    public void attemptClaimNextReward(Player player) {
        List<PendingReward> rewards = auctionStorage.getPendingRewardsByOwner(player.getUniqueId());
        if (rewards.isEmpty()) {
            msgManager.sendMessage(player, "rewards_claim_no_rewards");
            return;
        }

        PendingReward nextReward = rewards.get(0);
        boolean claimed = false;
        String rewardItemName = "N/A";
        if (nextReward.getItemToClaim() != null) {
             rewardItemName = InventoryUtil.formatMaterialName(nextReward.getItemToClaim().getType());
        }

        if (nextReward.getType() == PendingReward.RewardType.ITEM_AUCTION_WON ||
            nextReward.getType() == PendingReward.RewardType.ITEM_AUCTION_RETURNED) {
            ItemStack item = nextReward.getItemToClaim();
            if (item != null) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item.clone());
                    msgManager.sendMessage(player, nextReward.getReasonMessageKey(), nextReward.getReasonPlaceholders().toArray(new String[0]));
                    claimed = true;
                } else {
                    msgManager.sendMessage(player, "rewards_claim_inventory_full", "%item_name%", rewardItemName);
                }
            } else {
                claimed = true;
                msgManager.sendMessage(player, "rewards_claim_item_corrupt", "%reward_id%", nextReward.getRewardId().toString().substring(0,8));
            }
        } else if (nextReward.getType() == PendingReward.RewardType.MONEY_AUCTION_SOLD ||
                   nextReward.getType() == PendingReward.RewardType.MONEY_BID_REFUND) {
            double amount = nextReward.getMoneyToClaim();
             EconomyResponse tx = econ.depositPlayer(player, amount);
            if (tx.transactionSuccess()) {
                msgManager.sendMessage(player, nextReward.getReasonMessageKey(), nextReward.getReasonPlaceholders().toArray(new String[0]));
                claimed = true;
            } else {
                msgManager.sendMessage(player, "rewards_claim_money_error", "%reason%", tx.errorMessage);
            }
        }

        if (claimed) {
            try {
                auctionStorage.markRewardDelivered(nextReward.getRewardId());
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Fallo al marcar PendingReward ID: " + nextReward.getRewardId() + " como entregada (vía reclamo) para " + player.getName(), e);
            }
        }
    }
}
