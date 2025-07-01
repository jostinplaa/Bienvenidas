package com.aetherauctions.auction;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.model.Bid;
import com.aetherauctions.storage.AuctionStorage;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil; // Import InventoryUtil
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse; // Import EconomyResponse
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import com.aetherauctions.model.AuctionHistoryEvent;
import com.aetherauctions.util.SerializationUtil; // Añadido


public class AuctionManager {
    private final AetherAuctions plugin;
    private final AuctionStorage auctionStorage;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<UUID, Auction> activeAuctionsCache;
    private BukkitTask expirationCheckTask;

    public AuctionManager(AetherAuctions plugin, AuctionStorage storage) {
        this.plugin = plugin;
        this.auctionStorage = storage;
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.activeAuctionsCache = new ConcurrentHashMap<>();
    }

    public void loadAuctions() {
        activeAuctionsCache.clear();
        List<Auction> loaded = auctionStorage.loadActiveAuctions();
        for (Auction auction : loaded) {
            activeAuctionsCache.put(auction.getId(), auction);
        }
        plugin.getLogger().info("Cargadas " + activeAuctionsCache.size() + " subastas activas en el caché de AuctionManager.");
        startExpirationCheckTask();
    }

    public void startExpirationCheckTask() {
        if (expirationCheckTask != null) {
            expirationCheckTask.cancel();
        }
        long interval = configManager.getExpirationCheckIntervalSeconds() * 20L;
        if (interval <= 0) {
            plugin.getLogger().warning("El intervalo de revisión de expiración de subastas es <= 0. La tarea no se iniciará.");
            return;
        }
        expirationCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredAuctions();
            }
        }.runTaskTimer(plugin, interval, interval);
        plugin.getLogger().info("Tarea de revisión de subastas expiradas iniciada (cada " + (interval/20) + " segundos).");
    }

    public void stopScheduledTasks() {
        if (expirationCheckTask != null) {
            expirationCheckTask.cancel();
            expirationCheckTask = null;
            plugin.getLogger().info("Tarea de revisión de subastas expiradas detenida.");
        }
    }

    private void checkExpiredAuctions() {
        long currentTime = System.currentTimeMillis();
        List<Auction> toProcess = new ArrayList<>();
        for (Auction auction : activeAuctionsCache.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE && auction.getExpirationTimestamp() <= currentTime) {
                toProcess.add(auction);
            }
        }

        if (!toProcess.isEmpty()) {
            plugin.getLogger().info("Encontradas " + toProcess.size() + " subastas expiradas para procesar.");
            for (Auction auction : toProcess) {
                endAuction(auction);
            }
        }
    }

    public boolean createAuction(Player seller, ItemStack item, double startPrice, double buyNowPrice, long durationSeconds) {
        if (item == null || item.getType().isAir()) {
            messageManager.sendMessage(seller, "auction_create_error_no_item");
            return false;
        }
        if (startPrice <= 0) {
            messageManager.sendMessage(seller, "auction_create_error_invalid_start_price");
            return false;
        }
        if (buyNowPrice > 0 && buyNowPrice <= startPrice) {
            messageManager.sendMessage(seller, "auction_create_error_buy_now_too_low");
            return false;
        }
        if (durationSeconds <= 0) {
            messageManager.sendMessage(seller, "auction_create_error_invalid_duration");
            return false;
        }

        int maxAuctions = configManager.getMaxActiveAuctionsPerPlayer(seller);
        if (getPlayerActiveAuctions(seller.getUniqueId()).size() >= maxAuctions) {
            messageManager.sendMessage(seller, "auction_create_error_max_auctions_reached", "%limit%", String.valueOf(maxAuctions));
            return false;
        }

        double creationFee = configManager.getAuctionCreationFee(seller);
        Economy econ = AetherAuctions.getEconomy();
        if (creationFee > 0) {
            if (!econ.has(seller, creationFee)) {
                messageManager.sendMessage(seller, "auction_create_error_insufficient_funds_fee", "%fee%", String.valueOf(creationFee));
                return false;
            }
            if (!econ.withdrawPlayer(seller, creationFee).transactionSuccess()) {
                messageManager.sendMessage(seller, "auction_create_error_fee_charge_failed");
                return false;
            }
             messageManager.sendMessage(seller, "auction_create_fee_charged", "%fee%", String.valueOf(creationFee));
        }

        UUID auctionId = UUID.randomUUID();
        long creationTime = System.currentTimeMillis();
        long expirationTime = creationTime + (durationSeconds * 1000L);

        Auction auction = new Auction(
                auctionId,
                seller.getUniqueId(),
                seller.getName(),
                item.clone(),
                startPrice,
                buyNowPrice > 0 && configManager.isBuyNowAllowed() ? buyNowPrice : -1,
                creationTime,
                expirationTime
        );

        ItemStack itemToRemove = item.clone();
        seller.getInventory().removeItem(itemToRemove);

        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.put(auction.getId(), auction);
            messageManager.sendMessage(seller, "auction_create_success", "%id%", auction.getId().toString());

            // Registrar evento de historial
            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent historyEvent = new AuctionHistoryEvent(
                        seller.getUniqueId(),
                        auction.getId(),
                        InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(),
                        SerializationUtil.itemStackToBase64(auction.getItemStack()), // O una representación más simple
                        AuctionHistoryEvent.HistoryEventType.AUCTION_CREATED,
                        auction.getStartPrice(),
                        null, // No counterparty for creation
                        null,
                        System.currentTimeMillis()
                );
                try {
                    auctionStorage.saveHistoryEvent(historyEvent);
                    // Consider purging old history for the player
                    auctionStorage.purgeOldPlayerHistory(seller.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Error al guardar evento de historial (AUCTION_CREATED) para subasta ID: " + auction.getId(), ex);
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la nueva subasta ID: " + auction.getId(), e);
            messageManager.sendMessage(seller, "auction_create_error_database");
            if (creationFee > 0) econ.depositPlayer(seller, creationFee);
            seller.getInventory().addItem(item.clone());
            return false;
        }
    }

    public boolean placeBid(Player bidder, UUID auctionId, double bidAmount) {
        Auction auction = activeAuctionsCache.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(bidder, "auction_bid_error_not_active");
            return false;
        }
        if (auction.getSellerId().equals(bidder.getUniqueId())) {
            messageManager.sendMessage(bidder, "auction_bid_error_own_auction");
            return false;
        }
        double minNextBid = auction.getCurrentBid() + configManager.getMinBidIncrement();
        if (auction.getBidHistory().isEmpty()) {
            minNextBid = auction.getCurrentBid();
        }
         if (bidAmount < minNextBid) {
            messageManager.sendMessage(bidder, "auction_bid_error_too_low", "%amount%", String.valueOf(minNextBid));
            return false;
        }
        Economy econ = AetherAuctions.getEconomy();
        if (!econ.has(bidder, bidAmount)) {
            messageManager.sendMessage(bidder, "auction_bid_error_insufficient_funds");
            return false;
        }

        if (auction.hasBuyNow() && configManager.isBuyNowAllowed() && bidAmount >= auction.getBuyNowPrice()) {
            messageManager.sendMessage(bidder, "auction_bid_triggers_buy_now");
            return buyNow(bidder, auctionId);
        }

        OfflinePlayer previousHighestBidder = null;
        double oldBid = 0;
        if (auction.getHighestBidderId() != null) {
            previousHighestBidder = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
            oldBid = auction.getCurrentBid();
        }

        if (!econ.withdrawPlayer(bidder, bidAmount).transactionSuccess()) {
            messageManager.sendMessage(bidder, "auction_bid_error_payment_failed");
            return false;
        }

        UUID previousHighestBidderUUID = null;
        if (previousHighestBidder != null) {
            previousHighestBidderUUID = previousHighestBidder.getUniqueId();
            econ.depositPlayer(previousHighestBidder, oldBid);
            if (previousHighestBidder.isOnline() && previousHighestBidder.getPlayer() != null) {
                messageManager.sendMessage(previousHighestBidder.getPlayer(), "auction_outbid_notification", "%id%", auction.getId().toString());
            }
        }

        auction.setHighestBidderId(bidder.getUniqueId());
        auction.setHighestBidderName(bidder.getName());
        auction.setCurrentBid(bidAmount);
        auction.addBidToHistory(new Bid(bidder.getUniqueId(), bidder.getName(), bidAmount, System.currentTimeMillis()));

        try {
            auctionStorage.saveAuction(auction);
            messageManager.sendMessage(bidder, "auction_bid_success", "%amount%", String.valueOf(bidAmount));
            Player seller = Bukkit.getPlayer(auction.getSellerId());
            if (seller != null && seller.isOnline()) {
                messageManager.sendMessage(seller, "auction_new_bid_on_your_item",
                    "%bidder%", bidder.getName(),
                    "%amount%", String.valueOf(bidAmount),
                    "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()), // Added item_name
                    "%id%", auction.getId().toString()); // Added auction_id
            }

            // Registrar eventos de historial
            if (plugin.getConfigManager().isHistoryEnabled()) {
                // Evento para el pujador actual
                AuctionHistoryEvent bidPlacedEvent = new AuctionHistoryEvent(
                        bidder.getUniqueId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.BID_PLACED, bidAmount,
                        auction.getSellerName(), auction.getSellerId(), System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(bidPlacedEvent);
                auctionStorage.purgeOldPlayerHistory(bidder.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());

                // Evento para el pujador anterior (si existía)
                if (previousHighestBidderUUID != null) {
                    AuctionHistoryEvent outbidEvent = new AuctionHistoryEvent(
                            previousHighestBidderUUID, auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.BID_OUTBID, oldBid, // Precio es la puja que fue superada
                            bidder.getName(), bidder.getUniqueId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(outbidEvent);
                    auctionStorage.purgeOldPlayerHistory(previousHighestBidderUUID, plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la puja para la subasta ID: " + auction.getId(), e);
            messageManager.sendMessage(bidder, "auction_bid_error_database");
            econ.depositPlayer(bidder, bidAmount); // Revertir
            return false;
        }
    }

    public boolean buyNow(Player buyer, UUID auctionId) {
        Auction auction = activeAuctionsCache.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE || !auction.hasBuyNow() || !configManager.isBuyNowAllowed()) {
            messageManager.sendMessage(buyer, "auction_buy_now_error_not_available");
            return false;
        }
        if (auction.getSellerId().equals(buyer.getUniqueId())) {
            messageManager.sendMessage(buyer, "auction_buy_now_error_own_auction");
            return false;
        }
        Economy econ = AetherAuctions.getEconomy();
        double buyNowPrice = auction.getBuyNowPrice();
        if (!econ.has(buyer, buyNowPrice)) {
            messageManager.sendMessage(buyer, "auction_buy_now_error_insufficient_funds");
            return false;
        }
        if (!econ.withdrawPlayer(buyer, buyNowPrice).transactionSuccess()) {
            messageManager.sendMessage(buyer, "auction_buy_now_error_payment_failed");
            return false;
        }

        if (auction.getHighestBidderId() != null && !auction.getHighestBidderId().equals(buyer.getUniqueId())) {
            OfflinePlayer previousHighestBidder = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
            econ.depositPlayer(previousHighestBidder, auction.getCurrentBid());
             if (previousHighestBidder.isOnline() && previousHighestBidder.getPlayer() != null) {
                messageManager.sendMessage(previousHighestBidder.getPlayer(), "auction_bought_out_refund", "%id%", auction.getId().toString());
            }
             // Registrar evento de historial para el pujador superado por compra directa
            if (plugin.getConfigManager().isHistoryEnabled() && auction.getHighestBidderId() != null && !auction.getHighestBidderId().equals(buyer.getUniqueId())) {
                 AuctionHistoryEvent prevBidderRefundEvent = new AuctionHistoryEvent(
                        auction.getHighestBidderId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.BID_REFUNDED_AUCTION_SOLD_BUYNOW, auction.getCurrentBid(),
                        buyer.getName(), buyer.getUniqueId(), System.currentTimeMillis()
                );
                try {
                    auctionStorage.saveHistoryEvent(prevBidderRefundEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getHighestBidderId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Error al guardar evento de historial (BID_REFUNDED_AUCTION_SOLD_BUYNOW): " + auction.getId(), ex);
                }
            }
        }

        double commission = buyNowPrice * (configManager.getCommissionPercentage() / 100.0);
        double amountToSeller = buyNowPrice - commission;
        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerId());

        auction.setStatus(AuctionStatus.SOLD_BUYNOW);
        auction.setHighestBidderId(buyer.getUniqueId());
        auction.setHighestBidderName(buyer.getName());
        auction.setCurrentBid(buyNowPrice);

        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            plugin.getLogger().info("Subasta " + auction.getId() + " marcada como SOLD_BUYNOW y guardada.");

            if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                EconomyResponse tx = econ.depositPlayer(sellerOffline, amountToSeller);
                if (tx.transactionSuccess()) {
                     messageManager.sendMessage(sellerOffline.getPlayer(), "auction_your_item_sold_buy_now_money_received",
                        "%buyer%", buyer.getName(),
                        "%price%", String.format("%.2f", buyNowPrice),
                        "%received%", String.format("%.2f", amountToSeller),
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%currency%", configManager.getCurrencySymbol(),
                        "#id_short%", auction.getId().toString().substring(0,8));
                } else {
                    plugin.getLogger().warning("Fallo al depositar dinero a vendedor online " + sellerOffline.getName() + " para subasta (compra directa) " + auction.getId() + ". Razón: " + tx.errorMessage + ". Se creará PendingReward.");
                    plugin.getRewardManager().createPendingReward(
                        sellerOffline.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.MONEY_AUCTION_SOLD,
                        amountToSeller,
                        "reward_reason_money_sold_buynow_seller_error", // Nueva clave de mensaje para el motivo
                        java.util.Arrays.asList(
                            "%amount%", String.format("%.2f", amountToSeller),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getId().toString(),
                            "%buyer%", buyer.getName(),
                            "%reason%", tx.errorMessage
                        )
                    );
                    messageManager.sendMessage(sellerOffline.getPlayer(), "auction_your_item_sold_buy_now_money_pending_error",
                        "%received%", String.format("%.2f", amountToSeller),
                        "%currency%", configManager.getCurrencySymbol(),
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString(),
                        "%reason%", tx.errorMessage);
                }
            } else { // Vendedor offline
                plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline para subasta (compra directa) " + auction.getId() + ". Se creará PendingReward (MONEY).");
                 plugin.getRewardManager().createPendingReward(
                    sellerOffline.getUniqueId(),
                    com.aetherauctions.model.PendingReward.RewardType.MONEY_AUCTION_SOLD,
                    amountToSeller,
                    "reward_reason_money_sold_buynow_seller_offline",
                    java.util.Arrays.asList(
                        "%amount%", String.format("%.2f", amountToSeller),
                        "%currency%", configManager.getCurrencySymbol(),
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString(),
                        "%buyer%", buyer.getName()
                    )
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al procesar compra directa (SOLD_BUYNOW) para subasta " + auction.getId() + ". El pago al vendedor podría no haberse procesado.", e);
            econ.depositPlayer(buyer, buyNowPrice); // Revertir pago al comprador
            messageManager.sendMessage(buyer, "auction_buy_now_error_database_reverted");
            // Revert status in memory if save failed
            auction.setStatus(AuctionStatus.ACTIVE);
            // Potentially revert bidder info if it was a bid that triggered buy now
            // activeAuctionsCache.put(auction.getId(), auction); // Ensure it's back in cache if removed prematurely
            return false;
        }

        // Entregar ítem al comprador
        if (buyer.getInventory().firstEmpty() == -1) {
            messageManager.sendMessage(buyer, "auction_buy_now_inventory_full_pending",
                "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                "#id_short%", auction.getId().toString().substring(0,8)
            );
            plugin.getRewardManager().createPendingReward(
                buyer.getUniqueId(),
                com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_WON, // Usar ITEM_AUCTION_WON temporalmente o crear ITEM_PURCHASED si es necesario
                auction.getItemStack().clone(),
                "reward_reason_item_bought_inventory_full",
                java.util.Arrays.asList(
                    "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                    "#id_short%", auction.getId().toString().substring(0,8)
                )
            );
        } else {
            buyer.getInventory().addItem(auction.getItemStack().clone());
            messageManager.sendMessage(buyer, "auction_buy_now_success_item_received", // Cambiado a mensaje específico
                "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                "#id_short%", auction.getId().toString().substring(0,8)
            );
        }
        // TODO: Actualizar GUIs
        return true;
    }

    private void endAuction(Auction auction) {
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            plugin.getLogger().warning("endAuction llamado para una subasta no válida o no activa: " + (auction != null ? auction.getId() : "null"));
            return;
        }
        plugin.getLogger().info("Finalizando subasta ID: " + auction.getId());
        Economy econ = AetherAuctions.getEconomy();
        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerId());

        if (auction.getHighestBidderId() != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
            double finalPrice = auction.getCurrentBid();

            auction.setStatus(AuctionStatus.SOLD_BID);
            try {
                auctionStorage.saveAuction(auction);
                activeAuctionsCache.remove(auction.getId());
                plugin.getLogger().info("Subasta " + auction.getId() + " marcada como SOLD_BID y guardada.");

                double commission = finalPrice * (configManager.getCommissionPercentage() / 100.0);
                double amountToSeller = finalPrice - commission;

                if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                    EconomyResponse tx = econ.depositPlayer(sellerOffline, amountToSeller);
                    if (tx.transactionSuccess()) {
                        messageManager.sendMessage(sellerOffline.getPlayer(), "auction_your_item_sold_bid_money_received",
                            "%winner%", auction.getHighestBidderName(),
                            "%price%", String.format("%.2f", finalPrice),
                            "%received%", String.format("%.2f", amountToSeller),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%currency%", configManager.getCurrencySymbol(),
                            "#id_short%", auction.getId().toString().substring(0,8));
                    } else {
                        plugin.getLogger().warning("Fallo al depositar dinero a vendedor online " + sellerOffline.getName() + " para subasta " + auction.getId() + ". Razón: " + tx.errorMessage + ". Se creará PendingReward.");
                        plugin.getRewardManager().createPendingReward(
                            sellerOffline.getUniqueId(),
                            com.aetherauctions.model.PendingReward.RewardType.MONEY_AUCTION_SOLD,
                            amountToSeller,
                            "reward_reason_money_sold_bid_seller_error", // Nueva clave de mensaje
                            java.util.Arrays.asList(
                                "%amount%", String.format("%.2f", amountToSeller),
                                "%currency%", configManager.getCurrencySymbol(),
                                "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString(),
                                "%winner%", auction.getHighestBidderName(),
                                "%reason%", tx.errorMessage
                            )
                        );
                        messageManager.sendMessage(sellerOffline.getPlayer(), "auction_your_item_sold_bid_money_pending_error",
                            "%received%", String.format("%.2f", amountToSeller),
                             "%currency%", configManager.getCurrencySymbol(),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "#id_short%", auction.getId().toString().substring(0,8),
                            "%reason%", tx.errorMessage);
                    }
                } else {
                    plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline para subasta " + auction.getId() + ". Se creará PendingReward (MONEY).");
                    plugin.getRewardManager().createPendingReward(
                        sellerOffline.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.MONEY_AUCTION_SOLD,
                        amountToSeller,
                        "reward_reason_money_sold_bid_seller_offline", // Nueva clave de mensaje
                        java.util.Arrays.asList(
                            "%amount%", String.format("%.2f", amountToSeller),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getId().toString(), // Corregido
                            "%winner%", auction.getHighestBidderName()
                        )
                    );
                }

                // Dar ítem al ganador
                if (winner.isOnline() && winner.getPlayer() != null && winner.getPlayer().getInventory().firstEmpty() == -1) {
                    messageManager.sendMessage(winner.getPlayer(), "auction_won_inventory_full_pending",
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString());
                    plugin.getRewardManager().createPendingReward(
                        winner.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_WON,
                        auction.getItemStack().clone(),
                        "reward_reason_item_won_inventory_full",
                        java.util.Arrays.asList(
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getId().toString()
                        )
                    );
                } else if (winner.isOnline() && winner.getPlayer() != null) {
                     winner.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                     messageManager.sendMessage(winner.getPlayer(), "auction_won_item_received",
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString());
                } else { // Ganador offline
                     plugin.getRewardManager().createPendingReward(
                        winner.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_WON,
                        auction.getItemStack().clone(),
                        "reward_reason_item_won_offline",
                        java.util.Arrays.asList(
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getId().toString()
                        )
                    );
                }
                // Registrar evento de historial para el ganador
                if (plugin.getConfigManager().isHistoryEnabled()) {
                    AuctionHistoryEvent wonEvent = new AuctionHistoryEvent(
                            winner.getUniqueId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.AUCTION_WON_BY_BIDDER, finalPrice,
                            auction.getSellerName(), auction.getSellerId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(wonEvent);
                    auctionStorage.purgeOldPlayerHistory(winner.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());

                    // Evento para el vendedor
                     AuctionHistoryEvent soldForSellerEvent = new AuctionHistoryEvent(
                        auction.getSellerId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.AUCTION_SOLD_FOR_SELLER, amountToSeller, // Monto neto
                        winner.getName(), winner.getUniqueId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(soldForSellerEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getSellerId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error de DB al finalizar (SOLD_BID) subasta " + auction.getId() + ". El pago/entrega podría no haberse procesado.", e);
            }
        } else { // Subasta expirada sin pujas
            auction.setStatus(AuctionStatus.EXPIRED);
            try {
                auctionStorage.saveAuction(auction);
                activeAuctionsCache.remove(auction.getId());
                plugin.getLogger().info("Subasta " + auction.getId() + " marcada como EXPIRED y guardada.");

                // Devolver ítem al vendedor
                if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null && sellerOffline.getPlayer().getInventory().firstEmpty() == -1) {
                    messageManager.sendMessage(sellerOffline.getPlayer(), "auction_expired_inventory_full_pending",
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString());
                    plugin.getRewardManager().createPendingReward(
                        sellerOffline.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_RETURNED,
                        auction.getItemStack().clone(),
                        "reward_reason_item_expired_inventory_full",
                         java.util.Arrays.asList(
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getId().toString()
                        )
                    );
                } else if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                    sellerOffline.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                    messageManager.sendMessage(sellerOffline.getPlayer(), "auction_expired_item_returned",
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getId().toString());
                } else { // Vendedor offline
                     plugin.getRewardManager().createPendingReward(
                        sellerOffline.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_RETURNED,
                        auction.getItemStack().clone(),
                        "reward_reason_item_expired_offline",
                        java.util.Arrays.asList(
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getId().toString()
                        )
                    );
                }
                // Registrar evento de historial para subasta expirada
                if (plugin.getConfigManager().isHistoryEnabled()) {
                    AuctionHistoryEvent expiredEvent = new AuctionHistoryEvent(
                            auction.getSellerId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.AUCTION_EXPIRED_RETURNED, 0, // Precio 0 ya que no hubo venta
                            null, null, System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(expiredEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getSellerId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Error de DB al finalizar (EXPIRED) subasta " + auction.getId() + ".", e);
            }
        }
    }

    public boolean cancelAuction(Player canceller, UUID auctionId) {
        Auction auction = activeAuctionsCache.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(canceller, "auction_cancel_error_not_active");
            return false;
        }

        boolean isAdmin = canceller.hasPermission("aetherauctions.admin.cancel");
        if (!auction.getSellerId().equals(canceller.getUniqueId()) && !isAdmin) {
            messageManager.sendMessage(canceller, "auction_cancel_error_no_permission");
            return false;
        }

        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerId());
        String itemDisplayName = InventoryUtil.formatMaterialName(auction.getItemStack().getType());

        // Primero, actualizar estado y guardar en DB
        auction.setStatus(AuctionStatus.CANCELLED);
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            plugin.getLogger().info("Subasta " + auction.getId() + " marcada como CANCELLED y guardada.");

            // Devolver ítem al vendedor
            if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null && sellerOffline.getPlayer().getInventory().firstEmpty() == -1) {
                messageManager.sendMessage(sellerOffline.getPlayer(), "auction_cancelled_inventory_full", "%item_name%", itemDisplayName);
                // TODO PASO 13: Crear PendingReward (ITEM) para el vendedor
            } else if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                sellerOffline.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                if (sellerOffline.getUniqueId().equals(canceller.getUniqueId())) {
                     messageManager.sendMessage(sellerOffline.getPlayer(), "auction_cancelled_item_returned_self",
                        "%item_name%", itemDisplayName,
                        "#id_short%", auction.getId().toString().substring(0,8));
                } else { // Cancelado por admin
                     messageManager.sendMessage(sellerOffline.getPlayer(), "auction_cancelled_item_returned_by_admin",
                        "%item_name%", itemDisplayName,
                        "#id_short%", auction.getId().toString().substring(0,8),
                        "%admin%", canceller.getName());
                }
            } else { // Vendedor offline o inventario lleno (en caso de online)
                 plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline o inventario lleno. Ítem de subasta cancelada " + auction.getId() + " (" + itemDisplayName + ") irá a PendingReward.");
                 plugin.getRewardManager().createPendingReward(
                    sellerOffline.getUniqueId(),
                    com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_RETURNED, // Reutilizar este tipo
                    auction.getItemStack().clone(),
                    "reward_reason_item_cancelled_return_pending", // Nueva clave de mensaje
                    java.util.Arrays.asList(
                        "%item_name%", itemDisplayName,
                        "%id%", auction.getId().toString(), // Corregido
                        "%canceller%", canceller.getName()
                    )
                 );
                 if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                     messageManager.sendMessage(sellerOffline.getPlayer(), "auction_cancelled_inventory_full_pending",
                        "%item_name%", itemDisplayName,
                        "%id%", auction.getId().toString());
                 }
            }

            // Reembolsar al pujador más alto si existe y registrar historial
            if (auction.getHighestBidderId() != null && auction.getCurrentBid() > 0) {
                OfflinePlayer highestBidderOffline = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
                double amountToRefund = auction.getCurrentBid();
                Economy econ = AetherAuctions.getEconomy();

                if (highestBidderOffline.isOnline() && highestBidderOffline.getPlayer() != null) {
                    EconomyResponse tx = econ.depositPlayer(highestBidderOffline, amountToRefund);
                    if (tx.transactionSuccess()) {
                        messageManager.sendMessage(highestBidderOffline.getPlayer(), "auction_cancelled_bid_refunded",
                            "%id%", auction.getId().toString(),
                            "%amount%", String.format("%.2f", amountToRefund),
                            "%currency%", configManager.getCurrencySymbol());
                    } else {
                        plugin.getRewardManager().createPendingReward(
                            highestBidderOffline.getUniqueId(),
                            com.aetherauctions.model.PendingReward.RewardType.MONEY_BID_REFUND,
                            amountToRefund,
                            "reward_reason_money_cancelled_refund_error",
                            java.util.Arrays.asList(
                                "%amount%", String.format("%.2f", amountToRefund),
                                "%currency%", configManager.getCurrencySymbol(),
                                "%id%", auction.getId().toString(), // Corregido
                                "%reason%", tx.errorMessage
                            )
                        );
                        messageManager.sendMessage(highestBidderOffline.getPlayer(), "auction_cancelled_bid_refund_pending_error",
                            "%amount%", String.format("%.2f", amountToRefund),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%id%", auction.getId().toString(),
                            "%reason%", tx.errorMessage);
                    }
                } else {
                     plugin.getRewardManager().createPendingReward(
                        highestBidderOffline.getUniqueId(),
                        com.aetherauctions.model.PendingReward.RewardType.MONEY_BID_REFUND,
                        amountToRefund,
                        "reward_reason_money_cancelled_refund_offline",
                        java.util.Arrays.asList(
                            "%amount%", String.format("%.2f", amountToRefund),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%id%", auction.getId().toString() // Corregido
                        )
                    );
                }
                // Registrar evento de historial para el reembolso de la puja
                if (plugin.getConfigManager().isHistoryEnabled()) {
                    AuctionHistoryEvent bidRefundEvent = new AuctionHistoryEvent(
                            auction.getHighestBidderId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.BID_REFUNDED_AUCTION_CANCELLED, amountToRefund,
                            canceller.getName(), canceller.getUniqueId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(bidRefundEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getHighestBidderId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }
            }

            // Registrar evento de historial para la cancelación (para el vendedor)
            if (plugin.getConfigManager().isHistoryEnabled()) {
                 AuctionHistoryEvent.HistoryEventType cancelEventType = auction.getSellerId().equals(canceller.getUniqueId()) ?
                                                                  AuctionHistoryEvent.HistoryEventType.AUCTION_CANCELLED_BY_SELLER :
                                                                  AuctionHistoryEvent.HistoryEventType.AUCTION_CANCELLED_BY_ADMIN;
                AuctionHistoryEvent cancelEvent = new AuctionHistoryEvent(
                        auction.getSellerId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        cancelEventType, auction.getCurrentBid(), // Precio actual en el momento de la cancelación
                        canceller.getName(), canceller.getUniqueId(), System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(cancelEvent);
                auctionStorage.purgeOldPlayerHistory(auction.getSellerId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
            }

             // Notificar al cancelador (si es admin y no el mismo vendedor)
            if (isAdmin && !auction.getSellerId().equals(canceller.getUniqueId())) {
                messageManager.sendMessage(canceller, "auction_cancelled_admin_success", "%id%", auction.getId().toString());
            }
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al cancelar subasta " + auction.getId() + ". El reembolso/devolución podría no haberse procesado.", e);
            messageManager.sendMessage(canceller, "auction_cancel_error_database");
            auction.setStatus(AuctionStatus.ACTIVE);
            return false;
        }
    }

    public boolean adminDeleteAuction(Auction auction, CommandSender adminSender) {
        if (auction == null) {
            messageManager.sendMessage(adminSender, "admin_borrar_error_not_found_generic"); // Nueva clave
            return false;
        }
        plugin.getLogger().info("Admin " + adminSender.getName() + " está borrando la subasta ID: " + auction.getId() + " (Vendedor: " + auction.getSellerName() + ", Ítem: " + auction.getItemStack().getType() + ")");

        AuctionStatus originalStatus = auction.getStatus();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
        OfflinePlayer highestBidder = auction.getHighestBidderId() != null ? Bukkit.getOfflinePlayer(auction.getHighestBidderId()) : null;
        // Ensure getCurrencySymbol() is available, assuming cfgManager is initialized and accessible
        String currencySymbol = configManager.getCurrencySymbol();


        // 1. Devolver ítem al vendedor (si no se había vendido ya)
        if (originalStatus == AuctionStatus.ACTIVE || originalStatus == AuctionStatus.EXPIRED) { // Solo devolver si no fue SOLD_xxx
            ItemStack itemToReturn = auction.getItemStack().clone();
            boolean itemReturnedDirectly = false;
            if (seller.isOnline() && seller.getPlayer() != null) {
                if (seller.getPlayer().getInventory().firstEmpty() != -1) {
                    seller.getPlayer().getInventory().addItem(itemToReturn);
                    messageManager.sendMessage(seller.getPlayer(), "auction_admin_deleted_item_returned",
                        "#id_short%", auction.getId().toString().substring(0,8), // Cambiado a #id_short%
                        "%item_name%", InventoryUtil.formatMaterialName(itemToReturn.getType()),
                        "%admin%", adminSender.getName());
                    itemReturnedDirectly = true;
                } else { // Inventario lleno
                    messageManager.sendMessage(seller.getPlayer(), "auction_admin_deleted_item_pending_inv_full",
                         "#id_short%", auction.getId().toString().substring(0,8), // Cambiado a #id_short%
                         "%item_name%", InventoryUtil.formatMaterialName(itemToReturn.getType()),
                         "%admin%", adminSender.getName());
                }
            }
            if (!itemReturnedDirectly) { // Vendedor offline o inventario lleno
                plugin.getLogger().info("Ítem de subasta " + auction.getId() + " borrada por admin irá a PendingReward para vendedor " + seller.getName());
                plugin.getRewardManager().createPendingReward(
                    seller.getUniqueId(),
                    com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_RETURNED,
                    itemToReturn,
                    "reward_reason_item_admin_deleted", // Clave existente
                    java.util.Arrays.asList(
                        "%item_name%", InventoryUtil.formatMaterialName(itemToReturn.getType()),
                        "%id%", auction.getId().toString(),
                        "%admin%", adminSender.getName()
                    )
                );
            }
        }

        // 2. Reembolsar al pujador más alto y registrar historial
        if (originalStatus == AuctionStatus.ACTIVE && highestBidder != null && auction.getHighestBidderId() != null && auction.getCurrentBid() > 0 && !auction.getBidHistory().isEmpty()) {
            double amountToRefundToBidder = auction.getCurrentBid();
            boolean bidRefundedDirectly = false;
            if (highestBidder.isOnline() && highestBidder.getPlayer() != null) {
                EconomyResponse tx = AetherAuctions.getEconomy().depositPlayer(highestBidder, amountToRefundToBidder);
                if (tx.transactionSuccess()) {
                     messageManager.sendMessage(highestBidder.getPlayer(), "auction_admin_deleted_bid_refunded",
                        "%id%", auction.getId().toString(),
                        "%amount%", String.format("%.2f", amountToRefundToBidder),
                        "%currency%", currencySymbol,
                        "%admin%", adminSender.getName());
                    bidRefundedDirectly = true;
                } else {
                     messageManager.sendMessage(highestBidder.getPlayer(), "auction_admin_deleted_bid_refund_pending_error",
                        "%id%", auction.getId().toString(),
                        "%amount%", String.format("%.2f", amountToRefundToBidder),
                        "%currency%", currencySymbol,
                        "%admin%", adminSender.getName(),
                        "%reason%", tx.errorMessage);
                }
            }
            if (!bidRefundedDirectly) {
                plugin.getRewardManager().createPendingReward(
                    highestBidder.getUniqueId(),
                    com.aetherauctions.model.PendingReward.RewardType.MONEY_BID_REFUND,
                    amountToRefundToBidder,
                    "reward_reason_money_admin_deleted_refund",
                     java.util.Arrays.asList(
                        "%amount%", String.format("%.2f", amountToRefundToBidder),
                        "%currency%", currencySymbol,
                        "%id%", auction.getId().toString(),
                        "%admin%", adminSender.getName()
                    )
                );
            }
            // Registrar evento de historial para el reembolso de la puja
            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent bidRefundEvent = new AuctionHistoryEvent(
                        auction.getHighestBidderId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.BID_REFUNDED_AUCTION_CANCELLED, // O un tipo específico ADMIN_DELETE_REFUND
                        amountToRefundToBidder, adminSender.getName(), null, System.currentTimeMillis() // Counterparty es el admin
                );
                try {
                    auctionStorage.saveHistoryEvent(bidRefundEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getHighestBidderId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                } catch (SQLException ex) {
                     plugin.getLogger().log(Level.SEVERE, "Error guardando historial de reembolso por borrado admin para subasta: " + auction.getId(), ex);
                }
            }
        }

        // 3. Registrar evento de historial para el borrado de la subasta (para el vendedor)
        if (plugin.getConfigManager().isHistoryEnabled()) {
            AuctionHistoryEvent deleteEvent = new AuctionHistoryEvent(
                    auction.getSellerId(), auction.getId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                    auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                    AuctionHistoryEvent.HistoryEventType.AUCTION_CANCELLED_BY_ADMIN, // Reutilizar o crear ADMIN_DELETED
                    auction.getCurrentBid(), adminSender.getName(), null, System.currentTimeMillis()
            );
             try {
                auctionStorage.saveHistoryEvent(deleteEvent);
                auctionStorage.purgeOldPlayerHistory(auction.getSellerId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
            } catch (SQLException ex) {
                 plugin.getLogger().log(Level.SEVERE, "Error guardando historial de borrado admin para subasta: " + auction.getId(), ex);
            }
        }

        // 4. Actualizar estado de la subasta y guardar/eliminar de caché
        auction.setStatus(AuctionStatus.ADMIN_DELETED);
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            plugin.getLogger().info("Subasta " + auction.getId() + " marcada como " + auction.getStatus() + " y eliminada del caché por admin " + adminSender.getName());
            messageManager.sendMessage(adminSender, "admin_borrar_success", "%id%", auction.getId().toString(), "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al actualizar subasta " + auction.getId() + " durante borrado por admin.", e);
            messageManager.sendMessage(adminSender, "admin_borrar_failed", "%id%", auction.getId().toString());
            auction.setStatus(originalStatus);
            if (originalStatus == AuctionStatus.ACTIVE && !activeAuctionsCache.containsKey(auction.getId())) {
                 activeAuctionsCache.put(auction.getId(), auction);
            }
            return false;
        } // Cierre del catch (SQLException e)
    } // Cierre del método adminDeleteAuction


    public Map<UUID, Auction> getActiveAuctionsMap() {
        return new ConcurrentHashMap<>(activeAuctionsCache);
    }

    public List<Auction> getActiveAuctions() {
        return new ArrayList<>(activeAuctionsCache.values().stream()
            .filter(a -> a.getStatus() == AuctionStatus.ACTIVE)
            .collect(Collectors.toList()));
    }

    public Auction getAuctionById(UUID auctionId) {
        return activeAuctionsCache.get(auctionId);
    }

    public List<Auction> getPlayerActiveAuctions(UUID sellerId) {
        return activeAuctionsCache.values().stream()
                .filter(auction -> auction.getSellerId().equals(sellerId) && auction.getStatus() == AuctionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public Auction getAuctionByIdFuzzy(String idStr) {
        if (idStr == null || idStr.isEmpty()) return null;
        try {
            UUID fullUuid = UUID.fromString(idStr);
            return getAuctionById(fullUuid);
        } catch (IllegalArgumentException e) {
            for (Auction auction : activeAuctionsCache.values()) {
                if (auction.getId().toString().toLowerCase().startsWith(idStr.toLowerCase())) { // Asegurar comparación en minúsculas
                    return auction;
                }
            }
        }
        return null;
    }
}
