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
                if ("gui".equalsIgnoreCase(configManager.getRewardDeliveryMethod())) { // Usar getter de configManager
                    if (configManager.notifyOnJoinIfGuiModeRewards() && !rewards.isEmpty()) { // Usar nuevo getter
                        messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(rewards.size()));
                        logger.info(String.format("[RewardManager] Modo GUI: Notificando a %s de %d recompensas pendientes.", playerName, rewards.size()));
                    } else if (rewards.isEmpty()) {
                         logger.info(String.format("[RewardManager] Modo GUI: No hay recompensas pendientes para %s.", playerName));
                    } else {
                        // Modo GUI pero notify_on_join_if_gui_mode es false, no hacer nada.
                        logger.info(String.format("[RewardManager] Modo GUI: %s tiene %d recompensas, pero la notificación al unirse está desactivada.", playerName, rewards.size()));
                    }
                    return; // No procesar automáticamente en modo GUI
                }

                // Modo AUTO (comportamiento original)
                messageManager.sendMessage(player, "reward_processing_on_join");
                int successfullyClaimedCount = 0;
                int initiallyPending = rewards.size();

                for (PendingReward reward : rewards) {
                    logger.info(String.format("[RewardManager] Intentando procesar (auto) recompensa ID: %s, Tipo: %s para %s.", reward.getRewardId(), reward.getType(), playerName));
                    boolean success = deliverRewardInternal(player, reward, true); // true para auto-proceso
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
                         messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(stillPendingCount -1 > 0 ? stillPendingCount -1 : 0 )); // Ajustar conteo
                    } else {
                        List<PendingReward> remainingRewards = auctionStorage.getPendingRewardsByOwner(playerId)
                                .stream()
                                .filter(r -> !r.isDelivered())
                                .collect(Collectors.toList());
                        if(!remainingRewards.isEmpty()){
                            messageManager.sendMessage(player, "rewards_pending_notification", "%count%", String.valueOf(remainingRewards.size()));
                        } else {
                            messageManager.sendMessage(player, "rewards_none_pending");
                        }
                    }
                }
            });
        });
    }

    // Método público para reclamar una recompensa específica, usualmente desde la GUI
    public boolean attemptClaimSpecificReward(Player player, PendingReward reward) {
        if (reward == null || reward.isDelivered()) {
            logger.warning(String.format("[RewardManager] Intento de reclamar recompensa nula o ya entregada por %s. ID: %s", player.getName(), reward != null ? reward.getRewardId() : "NULL"));
            return false;
        }
        logger.info(String.format("[RewardManager] %s intentando reclamar (específica) recompensa ID: %s, Tipo: %s.", player.getName(), reward.getRewardId(), reward.getType()));
        boolean success = deliverRewardInternal(player, reward, false); // false indica que no es un proceso automático
        if (success) {
            logger.info(String.format("[RewardManager] Recompensa ID: %s reclamada (específica) exitosamente por %s.", reward.getRewardId(), player.getName()));
        } else {
            logger.info(String.format("[RewardManager] Fallo al reclamar (específica) recompensa ID: %s por %s.", reward.getRewardId(), player.getName()));
        }
        return success;
    }

    public void attemptClaimAllRewards(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        logger.info(String.format("[RewardManager] %s (%s) intentando reclamar TODAS las recompensas.", playerName, playerId));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PendingReward> allPendingRewards = auctionStorage.getPendingRewardsByOwner(playerId)
                    .stream()
                    .filter(r -> !r.isDelivered())
                    .collect(Collectors.toList());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (allPendingRewards.isEmpty()) {
                    messageManager.sendMessage(player, "rewards_none_pending");
                    logger.info(String.format("[RewardManager] %s intentó reclamar todo, pero no hay recompensas pendientes.", playerName));
                    return;
                }

                int moneyClaimedCount = 0;
                double totalMoneyClaimed = 0;
                int itemsClaimedCount = 0;
                int itemsFailedCount = 0;
                boolean inventoryWasFullForItem = false;

                // Primero, dinero
                List<PendingReward> moneyRewards = allPendingRewards.stream()
                        .filter(r -> r.getType() == RewardType.MONEY_AUCTION_SOLD || r.getType() == RewardType.MONEY_BID_REFUND)
                        .collect(Collectors.toList());

                for (PendingReward moneyReward : moneyRewards) {
                    if (deliverRewardInternal(player, moneyReward, false)) {
                        moneyClaimedCount++;
                        totalMoneyClaimed += moneyReward.getMoneyToClaim();
                    }
                    // No se necesita manejo de error aquí, deliverRewardInternal ya envía mensajes
                }

                // Luego, ítems
                List<PendingReward> itemRewards = allPendingRewards.stream()
                        .filter(r -> r.getType() == RewardType.ITEM_AUCTION_WON || r.getType() == RewardType.ITEM_AUCTION_RETURNED)
                        .collect(Collectors.toList());

                for (PendingReward itemReward : itemRewards) {
                    if (inventoryWasFullForItem) { // Si ya se llenó el inventario, no intentar más ítems
                        itemsFailedCount++;
                        continue;
                    }
                    if (deliverRewardInternal(player, itemReward, false)) {
                        itemsClaimedCount++;
                    } else {
                        // Verificar si fue por inventario lleno
                        if (player.getInventory().firstEmpty() == -1) {
                            inventoryWasFullForItem = true;
                            // El mensaje de inventario lleno ya lo da deliverRewardInternal
                        }
                        itemsFailedCount++;
                    }
                }

                // Mensajes de resumen
                if (moneyClaimedCount > 0) {
                    messageManager.sendMessage(player, "claim_all_summary_money", // Nueva clave
                            "%count%", String.valueOf(moneyClaimedCount),
                            "%total_amount%", String.format("%.2f", totalMoneyClaimed),
                            "%currency%", configManager.getCurrencySymbol());
                }
                if (itemsClaimedCount > 0) {
                     messageManager.sendMessage(player, "claim_all_summary_items_claimed", // Nueva clave
                            "%count%", String.valueOf(itemsClaimedCount));
                }
                if (itemsFailedCount > 0) {
                    messageManager.sendMessage(player, "claim_all_summary_items_failed", // Nueva clave
                            "%count%", String.valueOf(itemsFailedCount),
                            "%reason%", inventoryWasFullForItem ? messageManager.getRawMessage("claim_all_reason_inventory_full") : messageManager.getRawMessage("claim_all_reason_other") // Nuevas claves
                    );
                }
                if (moneyClaimedCount == 0 && itemsClaimedCount == 0 && itemsFailedCount == 0 && !allPendingRewards.isEmpty()){
                     messageManager.sendMessage(player, "claim_all_nothing_claimable_now"); // Nueva clave: Ej: "No se pudo reclamar nada en este momento. Revisa tu inventario."
                } else if (moneyClaimedCount == 0 && itemsClaimedCount == 0 && itemsFailedCount > 0) {
                    // Ya se notificó sobre el fallo de ítems
                }
                 else if (moneyClaimedCount == 0 && itemsClaimedCount == 0 && itemsFailedCount == 0 && allPendingRewards.isEmpty()){
                     // Esto no debería pasar debido al chequeo inicial, pero por si acaso.
                     messageManager.sendMessage(player, "rewards_none_pending");
                 }


                // La GUI se refrescará al ser reabierta desde ClaimRewardsGUI
                logger.info(String.format("[RewardManager] Reclamo total para %s finalizado. Dinero: %d (%.2f %s), Ítems OK: %d, Ítems Fallidos: %d (Inv Lleno: %b)",
                        playerName, moneyClaimedCount, totalMoneyClaimed, configManager.getCurrencySymbol(), itemsClaimedCount, itemsFailedCount, inventoryWasFullForItem));
            });
        });
    }


    // Sobrecarga para mantener compatibilidad con llamadas antiguas que no especifican isClaimAll
    public boolean deliverRewardInternal(Player player, PendingReward reward, boolean isAutoProcess) {
        return deliverRewardInternal(player, reward, isAutoProcess, false);
    }

    public boolean deliverRewardInternal(Player player, PendingReward reward, boolean isAutoProcess, boolean isClaimAll) {
        String currencySymbol = configManager.getCurrencySymbol();
        boolean success = false;
        String playerName = player.getName();
        UUID rewardId = reward.getRewardId();

        // No loguear aquí para reducir spam, ya se loguea en el método que llama.
        // logger.info(String.format("[RewardManager] deliverRewardInternal para %s. Recompensa ID: %s, Tipo: %s, Auto: %b, ClaimAll: %b", playerName, rewardId, reward.getType(), isAutoProcess, isClaimAll));

        switch (reward.getType()) {
            case ITEM_AUCTION_WON:
            case ITEM_AUCTION_RETURNED:
                ItemStack item = reward.getItemToClaim();
                if (item == null || item.getType() == Material.AIR) {
                    // No enviar mensaje al jugador por ítem corrupto, solo loguear.
                    logger.severe(String.format("[RewardManager] Ítem nulo/aire para recompensa ID: %s para %s. Marcando como entregada.", rewardId, playerName));
                    try {
                        auctionStorage.markRewardDelivered(rewardId);
                    } catch (SQLException e) {
                        logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] CRITICAL: Fallo al marcar recompensa de ÍTEM CORRUPTO ID %s como entregada. Error: %s", rewardId, e.getMessage()), e);
                    }
                    return false;
                }

                PlayerInventory inventory = player.getInventory();
                if (inventory.firstEmpty() != -1) {
                    inventory.addItem(item.clone());
                    try {
                        auctionStorage.markRewardDelivered(rewardId);
                        if (!isAutoProcess && !isClaimAll) { // Solo mensaje en reclamo individual manual
                            messageManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                        } else if (isAutoProcess && !"gui".equalsIgnoreCase(configManager.getRewardDeliveryMethod())) { // Modo AUTO
                             messageManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                        }
                        // No enviar mensaje si es isClaimAll, el resumen se encarga.
                        // No enviar mensaje si es isAutoProcess en modo GUI.
                        success = true;
                    } catch (SQLException e) {
                        logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] CRITICAL DB: Ítem entregado para ID %s a %s PERO FALLÓ MARCADO. Error: %s", rewardId, playerName, e.getMessage()), e);
                        inventory.removeItem(item.clone()); // Revertir
                        player.updateInventory();
                        success = false;
                    }
                } else { // Inventario Lleno
                    if (!isAutoProcess && !isClaimAll) { // Mensaje de inv. lleno solo en reclamo individual manual
                        messageManager.sendMessage(player, "reward_claim_failed_inventory",
                            "%item_name%", InventoryUtil.formatMaterialName(item.getType()),
                            "%id%", rewardId.toString());
                    }
                    // No enviar mensaje si es isClaimAll o isAutoProcess, se maneja en el resumen o notificación general.
                    success = false;
                }
                break;
            case MONEY_AUCTION_SOLD:
            case MONEY_BID_REFUND:
                if (economy != null) {
                    double amountToDeliver = reward.getMoneyToClaim();
                    if (amountToDeliver <= 0) {
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
                             if (!isAutoProcess && !isClaimAll) { // Solo mensaje en reclamo individual manual
                                messageManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                            } else if (isAutoProcess && !"gui".equalsIgnoreCase(configManager.getRewardDeliveryMethod())) { // Modo AUTO
                                 messageManager.sendMessage(player, reward.getReasonMessageKey(), reward.getReasonPlaceholders().toArray(new String[0]));
                            }
                            success = true;
                        } catch (SQLException e) {
                            logger.log(java.util.logging.Level.SEVERE, String.format("[RewardManager] CRITICAL DB: Dinero entregado para ID %s a %s (%.2f) PERO FALLÓ MARCADO. Error: %s", rewardId, playerName, amountToDeliver, e.getMessage()), e);
                            success = false;
                        }
                    } else { // Fallo Vault
                        if (!isAutoProcess && !isClaimAll) {
                             messageManager.sendMessage(player, "reward_claim_failed_money_econ_error",
                                "%amount%", String.format("%.2f", amountToDeliver), "%currency%", currencySymbol,
                                "%reason%", econResponse.errorMessage, "%id%", rewardId.toString());
                        }
                        success = false;
                    }
                } else { // Vault no disponible
                    if (!isAutoProcess && !isClaimAll) {
                        messageManager.sendMessage(player, "reward_claim_failed_money_no_vault",
                            "%amount%", String.format("%.2f", reward.getMoneyToClaim()), "%currency%", currencySymbol,
                             "%id%", rewardId.toString());
                    }
                    success = false;
                }
                break;
            default: // Tipo desconocido
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
            if (!configManager.isRewardCleanupEnabled()){ // Usar nueva clave para habilitar/deshabilitar
                logger.info("Old reward cleanup task is disabled via config (rewards.cleanup_delivered_rewards.enabled: false).");
                return;
            }
            int daysToKeep = configManager.getDeliveredRewardsCleanupDays(); // Usar nuevo getter
            if (daysToKeep <= 0) { // Esta condición ahora es redundante si isRewardCleanupEnabled es la principal
                logger.info("Old reward cleanup effectively disabled (daysToKeep is " + daysToKeep + ").");
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
