package com.aetherauctions.managers;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.PendingReward;
import com.aetherauctions.model.PendingReward.RewardType;
import com.aetherauctions.storage.AuctionStorage; // Added import
import com.aetherauctions.config.ConfigManager;   // Added import
import com.aetherauctions.config.MessageManager; // Added import
import com.aetherauctions.util.SerializationUtil;
import com.aetherauctions.util.InventoryUtil; // Added import
import java.sql.SQLException; // Added import
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

    public void createPendingReward(UUID ownerId, PendingReward.RewardType type, ItemStack item, String reasonKey, List<String> reasonPlaceholders) {
        if (type != PendingReward.RewardType.ITEM_AUCTION_WON && type != PendingReward.RewardType.ITEM_AUCTION_RETURNED) {
            logger.warning("[RewardManager] createPendingReward (ITEM) llamado con tipo incorrecto: " + type + " para " + ownerId);
            return;
        }
        PendingReward reward = new PendingReward(ownerId, type, item, reasonKey, reasonPlaceholders);
        try {
            auctionStorage.saveReward(reward);
            logger.info(String.format("[RewardManager] PendingReward ÍTEM CREADO. ID: %s, Dueño: %s, Tipo: %s, Ítem: %s, Razón: %s",
                reward.getRewardId(), ownerId, type, (item != null ? item.getType() : "NULL"), reasonKey));
        } catch (SQLException e) {
            logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] FALLO SQL al guardar PendingReward ÍTEM. Dueño: %s, Tipo: %s", ownerId, type), e);
        }
    }

    public void createPendingReward(UUID ownerId, PendingReward.RewardType type, double amount, String reasonKey, List<String> reasonPlaceholders) {
        if (type != PendingReward.RewardType.MONEY_AUCTION_SOLD && type != PendingReward.RewardType.MONEY_BID_REFUND) {
            logger.warning("[RewardManager] createPendingReward (MONEY) llamado con tipo incorrecto: " + type + " para " + ownerId);
            return;
        }
        PendingReward reward = new PendingReward(ownerId, type, amount, reasonKey, reasonPlaceholders);
        try {
            auctionStorage.saveReward(reward);
            logger.info(String.format("[RewardManager] PendingReward DINERO CREADO. ID: %s, Dueño: %s, Tipo: %s, Monto: %.2f, Razón: %s",
                reward.getRewardId(), ownerId, type, amount, reasonKey));
        } catch (SQLException e) {
            logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] FALLO SQL al guardar PendingReward DINERO. Dueño: %s, Tipo: %s, Monto: %.2f", ownerId, type, amount), e);
        }
    }

    public void processPendingRewards(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        logger.info(String.format("[RewardManager] Iniciando procesamiento de recompensas pendientes para %s (%s) al unirse.", playerName, playerId));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PendingReward> rewards = auctionStorage.getPendingRewardsByOwner(playerId)
                    .stream()
                    .filter(r -> !r.isDelivered())
                    .collect(Collectors.toList());

            if (rewards.isEmpty()) {
                logger.info(String.format("[RewardManager] No hay recompensas pendientes para %s (%s).", playerName, playerId));
                return;
            }
            logger.info(String.format("[RewardManager] Encontradas %d recompensas pendientes para %s (%s).", rewards.size(), playerName, playerId));

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                messageManager.sendMessage(player, "reward_processing_on_join");
                int successfullyClaimedCount = 0;
                int initiallyPending = rewards.size();

                for (PendingReward reward : rewards) {
                    logger.info(String.format("[RewardManager] Intentando procesar (auto) recompensa ID: %s, Tipo: %s para %s.", reward.getRewardId(), reward.getType(), playerName));
                    boolean success = deliverRewardInternal(player, reward, true);
                    if (success) {
                        successfullyClaimedCount++;
                        logger.info(String.format("[RewardManager] Recompensa ID: %s procesada (auto) exitosamente para %s.", reward.getRewardId(), playerName));
                    } else {
                        logger.info(String.format("[RewardManager] Fallo al procesar (auto) recompensa ID: %s para %s. Permanecerá pendiente.", reward.getRewardId(), playerName));
                    }
                }

                int remainingPendingCount = initiallyPending - successfullyClaimedCount;
                if (remainingPendingCount > 0) {
                    messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(remainingPendingCount));
                }
                logger.info(String.format("[RewardManager] Procesamiento (auto) finalizado para %s. Exitosas: %d, Aún pendientes: %d.", playerName, successfullyClaimedCount, remainingPendingCount));
            });
        });
    }

    public void attemptClaimNextReward(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        logger.info(String.format("[RewardManager] Jugador %s (%s) intentando reclamar la siguiente recompensa.", playerName, playerId));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PendingReward> rewards = auctionStorage.getPendingRewardsByOwner(playerId)
                    .stream()
                    .filter(r -> !r.isDelivered())
                    .collect(Collectors.toList()); // Fetches all, but we'll process one

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rewards.isEmpty()) {
                    messageManager.sendMessage(player, "rewards_none_pending");
                    logger.info(String.format("[RewardManager] %s intentó reclamar, pero no hay recompensas pendientes.", playerName));
                    return;
                }

                PendingReward rewardToClaim = rewards.get(0); // Get the oldest one
                logger.info(String.format("[RewardManager] %s intentando reclamar (manual) recompensa ID: %s, Tipo: %s.", playerName, rewardToClaim.getRewardId(), rewardToClaim.getType()));
                boolean success = deliverRewardInternal(player, rewardToClaim, false);

                if (success) {
                    logger.info(String.format("[RewardManager] Recompensa ID: %s reclamada (manual) exitosamente por %s.", rewardToClaim.getRewardId(), playerName));
                    // Mensaje de éxito ya manejado por deliverRewardInternal
                } else {
                    logger.info(String.format("[RewardManager] Fallo al reclamar (manual) recompensa ID: %s por %s.", rewardToClaim.getRewardId(), playerName));
                    // Mensaje de fallo ya manejado por deliverRewardInternal
                    // Verificar si aún hay recompensas pendientes para la notificación
                    long stillPendingCount = rewards.stream().filter(r -> !r.isDelivered() || r.getRewardId().equals(rewardToClaim.getRewardId())).count();
                    if (auctionStorage.getPendingReward(rewardToClaim.getRewardId()) != null && !auctionStorage.getPendingReward(rewardToClaim.getRewardId()).isDelivered()){
                         messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(stillPendingCount));
                    } else { // Si la recompensa fue marcada como entregada por alguna razón (ej. corrupta)
                        List<PendingReward> remainingRewards = auctionStorage.getPendingRewardsByOwner(playerId)
                                .stream()
                                .filter(r -> !r.isDelivered())
                                .collect(Collectors.toList());
                        if(!remainingRewards.isEmpty()){
                            messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(remainingRewards.size()));
                        } else {
                             // No more rewards after this attempt, even if it failed due to corruption.
                        }
                    }
                }
            });
        });
    }

    private boolean deliverRewardInternal(Player player, PendingReward reward, boolean isAutoProcess) {
        String currencySymbol = configManager.getCurrencySymbol();
        boolean success = false;
        String playerName = player.getName();
        UUID rewardId = reward.getRewardId();

        logger.info(String.format("[RewardManager] deliverRewardInternal para %s. Recompensa ID: %s, Tipo: %s, Auto: %b", playerName, rewardId, reward.getType(), isAutoProcess));

        switch (reward.getType()) {
            case ITEM_AUCTION_WON:
            case ITEM_AUCTION_RETURNED:
                ItemStack item = reward.getItemToClaim();
                if (item == null || item.getType() == Material.AIR) {
                    logger.severe(String.format("[RewardManager] Ítem nulo/aire para recompensa ID: %s para %s. Marcando como entregada.", rewardId, playerName));
                    try {
                        auctionStorage.markRewardDelivered(rewardId);
                    } catch (SQLException e) {
                        logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] CRITICAL: Fallo al marcar recompensa de ÍTEM CORRUPTO ID %s como entregada. Error: %s", rewardId, e.getMessage()), e);
                    }
                    return false; // No se puede entregar.
                }

                PlayerInventory inventory = player.getInventory();
                if (inventory.firstEmpty() != -1) {
                    inventory.addItem(item.clone());
                    try {
                        auctionStorage.markRewardDelivered(rewardId);
                        messageManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                        logger.info(String.format("[RewardManager] ÉXITO entrega ÍTEM. ID: %s, Jugador: %s, Ítem: %s", rewardId, playerName, item.getType()));
                        success = true;
                    } catch (SQLException e) {
                        logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] CRITICAL DB: Ítem entregado para ID %s a %s PERO FALLÓ MARCADO. Error: %s", rewardId, playerName, e.getMessage()), e);
                        inventory.removeItem(item.clone());
                        player.updateInventory();
                        logger.warning(String.format("[RewardManager] Intento de REVERSIÓN de entrega de ítem para ID %s debido a fallo DB.", rewardId));
                        success = false;
                    }
                } else {
                    if (!isAutoProcess) {
                        messageManager.sendMessage(player, "reward_claim_failed_inventory",
                            "%item_name%", InventoryUtil.formatMaterialName(item.getType()),
                            "#id_short%", rewardId.toString().substring(0,8));
                    }
                    logger.info(String.format("[RewardManager] FALLO entrega ÍTEM (Inv. Lleno). ID: %s, Jugador: %s", rewardId, playerName));
                    success = false;
                }
                break;
            case MONEY_AUCTION_SOLD:
            case MONEY_BID_REFUND:
                if (economy != null) {
                    double amountToDeliver = reward.getMoneyToClaim();
                    if (amountToDeliver <= 0) {
                        logger.info(String.format("[RewardManager] Monto <= 0 (%.2f) para recompensa ID: %s. Marcando como entregada.", amountToDeliver, rewardId));
                        try {
                            auctionStorage.markRewardDelivered(rewardId);
                        } catch (SQLException e) {
                             logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] Fallo al marcar recompensa dinero (<=0) ID %s como entregada. Error: %s", rewardId, e.getMessage()), e);
                        }
                        return true;
                    }

                    net.milkbowl.vault.economy.EconomyResponse econResponse = economy.depositPlayer(player, amountToDeliver);
                    if (econResponse.transactionSuccess()) {
                        try {
                            auctionStorage.markRewardDelivered(rewardId);
                            messageManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                            logger.info(String.format("[RewardManager] ÉXITO entrega DINERO. ID: %s, Jugador: %s, Monto: %.2f", rewardId, playerName, amountToDeliver));
                            success = true;
                        } catch (java.sql.SQLException e) {
                            logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] CRITICAL DB: Dinero entregado para ID %s a %s (%.2f) PERO FALLÓ MARCADO. Error: %s", rewardId, playerName, amountToDeliver, e.getMessage()), e);
                            logger.severe(String.format("[RewardManager] ACCIÓN MANUAL REQUERIDA: Revertir depósito de %.2f a %s (UUID: %s) por recompensa %s", amountToDeliver, playerName, player.getUniqueId(), rewardId));
                            success = false;
                        }
                    } else {
                        logger.warning(String.format("[RewardManager] FALLO Vault Tx. ID: %s, Jugador: %s, Razón: %s", rewardId, playerName, econResponse.errorMessage));
                        if (!isAutoProcess) {
                             messageManager.sendMessage(player, "reward_claim_failed_money_econ_error",
                                "%amount%", String.format("%.2f", amountToDeliver), "%currency%", currencySymbol,
                                "%reason%", econResponse.errorMessage, "#id_short%", rewardId.toString().substring(0,8));
                        }
                        success = false;
                    }
                } else {
                    logger.severe(String.format("[RewardManager] FALLO Vault No Disp. ID: %s, Jugador: %s", rewardId, playerName));
                    if (!isAutoProcess) {
                        messageManager.sendMessage(player, "reward_claim_failed_money_no_vault",
                            "%amount%", String.format("%.2f", reward.getMoneyToClaim()), "%currency%", currencySymbol,
                             "#id_short%", rewardId.toString().substring(0,8));
                    }
                    success = false;
                }
                break;
            default:
                logger.warning(String.format("[RewardManager] Tipo recompensa DESCONOCIDO. ID: %s, Tipo: %s. Marcando como entregada.", rewardId, reward.getType()));
                try {
                    auctionStorage.markRewardDelivered(rewardId);
                } catch (SQLException e) {
                    logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] Fallo al marcar recompensa tipo DESCONOCIDO ID %s como entregada. Error: %s", rewardId, e.getMessage()), e);
                }
                success = false;
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
