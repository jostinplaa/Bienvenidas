package com.aetherauctions.auction;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.model.Bid;
import com.aetherauctions.storage.AuctionStorage;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import com.aetherauctions.util.SerializationUtil;
import com.aetherauctions.model.AuctionHistoryEvent;
import com.aetherauctions.events.AuctionUpdateEvent;
import com.aetherauctions.managers.RewardManager;
import com.aetherauctions.model.PendingReward;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.Arrays;

public class AuctionManager {
    private final AetherAuctions plugin;
    private final AuctionStorage auctionStorage;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final RewardManager rewardManager;
    private final Map<UUID, Auction> activeAuctionsCache;
    private BukkitTask expirationCheckTask;
    private final Map<UUID, Long> playerLastAuctionCreationTime = new HashMap<>(); // Para cooldown

    public AuctionManager(AetherAuctions plugin, AuctionStorage storage) {
        this.plugin = plugin;
        this.auctionStorage = storage;
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.rewardManager = plugin.getRewardManager(); // Initialize rewardManager
        this.activeAuctionsCache = new ConcurrentHashMap<>();
    }

    public void loadAuctions() {
        activeAuctionsCache.clear();
        List<Auction> loaded = auctionStorage.loadActiveAuctions();
        for (Auction auction : loaded) {
            activeAuctionsCache.put(auction.getAuctionId(), auction);
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
            if (auction.getStatus() == AuctionStatus.ACTIVE && auction.getEndTimeMillis() <= currentTime) {
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
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_no_item"));
            return false;
        }
        if (startPrice <= 0) {
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_invalid_start_price"));
            return false;
        }
        if (buyNowPrice > 0 && buyNowPrice <= startPrice) {
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_buy_now_too_low"));
            return false;
        }
        if (durationSeconds <= 0) {
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_invalid_duration"));
            return false;
        }

        int maxAuctions = configManager.getMaxActiveAuctionsPerPlayer(); // No necesita 'seller'
        if (getPlayerActiveAuctions(seller.getUniqueId()).size() >= maxAuctions) {
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_max_auctions_reached", "%limit%", String.valueOf(maxAuctions)));
            return false;
        }

        double creationFee = configManager.getAuctionCreationFee(); // No necesita 'seller'
        Economy econ = AetherAuctions.getEconomy();
        if (creationFee > 0) {
            if (!econ.has(seller, creationFee)) {
                messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_insufficient_funds_fee", "%fee%", String.valueOf(creationFee)));
                return false;
            }
            EconomyResponse feeTx = econ.withdrawPlayer(seller, creationFee);
            if (!feeTx.transactionSuccess()) {
                messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_fee_charge_failed"));
                return false;
            }
             messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_fee_charged", "%fee%", String.valueOf(creationFee)));
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
                expirationTime,
                AuctionStatus.ACTIVE,
                null, null, 0.0,
                new ArrayList<>(),
                false, null
        );

        ItemStack itemToRemove = item.clone();
        seller.getInventory().removeItem(itemToRemove);

        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.put(auction.getAuctionId(), auction);
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_success", "%id%", auction.getAuctionId().toString()));

            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent historyEvent = new AuctionHistoryEvent(
                        seller.getUniqueId(),
                        auction.getAuctionId(),
                        InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(),
                        SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.AUCTION_CREATED,
                        auction.getStartPrice(),
                        null,
                        null,
                        System.currentTimeMillis()
                );
                try {
                    auctionStorage.saveHistoryEvent(historyEvent);
                    auctionStorage.purgeOldPlayerHistory(seller.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Error al guardar evento de historial (AUCTION_CREATED) para subasta ID: " + auction.getAuctionId(), ex);
                }
            }
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.NEW_AUCTION_LISTED));
            if (configManager.getAuctionCreationCooldownSeconds() > 0) {
                playerLastAuctionCreationTime.put(seller.getUniqueId(), System.currentTimeMillis());
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la nueva subasta ID: " + auction.getAuctionId(), e);
            messageManager.sendMessage(seller, "auction_create_error_database"); // Corregido
            if (creationFee > 0) econ.depositPlayer(seller, creationFee);
            seller.getInventory().addItem(item.clone());
            return false;
        }
    }

    // --- Métodos de Cooldown ---
    public boolean isPlayerOnAuctionCreationCooldown(UUID playerId) {
        long cooldownSeconds = configManager.getAuctionCreationCooldownSeconds();
        if (cooldownSeconds <= 0) return false; // Cooldown desactivado
        long lastCreation = playerLastAuctionCreationTime.getOrDefault(playerId, 0L);
        long timeSinceLast = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastCreation);
        return timeSinceLast < cooldownSeconds;
    }

    public long getAuctionCreationCooldownTimeLeft(UUID playerId) {
        long cooldownSeconds = configManager.getAuctionCreationCooldownSeconds();
        if (cooldownSeconds <= 0) return 0L;
        long lastCreation = playerLastAuctionCreationTime.getOrDefault(playerId, 0L);
        long timeSinceLast = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastCreation);
        return Math.max(0L, cooldownSeconds - timeSinceLast);
    }
    // --- Fin Métodos de Cooldown ---

    public boolean placeBid(Player bidder, UUID auctionId, double bidAmount) {
        Auction auction = activeAuctionsCache.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_error_not_active"));
            return false;
        }
        if (auction.getSellerUUID().equals(bidder.getUniqueId())) {
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_error_own_auction"));
            return false;
        }
        double minNextBid = auction.getCurrentBid() + configManager.getMinBidIncrement();
        if (auction.getBidHistory().isEmpty()) {
             minNextBid = auction.getStartPrice();
        }
         if (bidAmount < minNextBid) {
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_error_too_low", "%amount%", String.valueOf(minNextBid)));
            return false;
        }
        Economy econ = AetherAuctions.getEconomy();
        if (!econ.has(bidder, bidAmount)) {
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_error_insufficient_funds"));
            return false;
        }

        if (auction.getBuyoutPrice() > 0 && configManager.isBuyNowAllowed() && bidAmount >= auction.getBuyoutPrice()) {
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_triggers_buy_now"));
            return buyNow(bidder, auctionId);
        }

        OfflinePlayer previousHighestBidderOp = null;
        double oldBid = 0;
        if (auction.getHighestBidderUUID() != null) {
            previousHighestBidderOp = Bukkit.getOfflinePlayer(auction.getHighestBidderUUID());
            oldBid = auction.getCurrentBid();
        }

        EconomyResponse withdrawTx = econ.withdrawPlayer(bidder, bidAmount);
        if (!withdrawTx.transactionSuccess()) {
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_error_payment_failed"));
            return false;
        }

        UUID previousHighestBidderUUID = null;
        if (previousHighestBidderOp != null) {
            previousHighestBidderUUID = previousHighestBidderOp.getUniqueId();
            econ.depositPlayer(previousHighestBidderOp, oldBid);
            if (previousHighestBidderOp.isOnline() && previousHighestBidderOp.getPlayer() != null) {
                Player prevBidderPlayer = previousHighestBidderOp.getPlayer();
                messageManager.sendMessage(prevBidderPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_outbid_notification", "%id%", auction.getAuctionId().toString()));
                plugin.getSoundManager().playSound(prevBidderPlayer, "bid_outbid_notification");
            }
        }

        auction.setHighestBidderUUID(bidder.getUniqueId());
        auction.setHighestBidderName(bidder.getName());
        auction.setCurrentBid(bidAmount);
        auction.addBidToHistory(new Bid(bidder.getUniqueId(), bidder.getName(), bidAmount, System.currentTimeMillis()));

        try {
            auctionStorage.saveAuction(auction);
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_success", "%amount%", String.valueOf(bidAmount)));
            Player sellerPlayer = Bukkit.getPlayer(auction.getSellerUUID());
            if (sellerPlayer != null && sellerPlayer.isOnline()) {
                messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_new_bid_on_your_item",
                    "%bidder%", bidder.getName(),
                    "%amount%", String.valueOf(bidAmount),
                    "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                    "%id%", auction.getAuctionId().toString()));
                plugin.getSoundManager().playSound(sellerPlayer, "bid_new_on_own");
            }
            plugin.getSoundManager().playSound(bidder, "bid_placed_success");

            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent bidPlacedEvent = new AuctionHistoryEvent(
                        bidder.getUniqueId(), auction.getAuctionId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.BID_PLACED, bidAmount,
                        auction.getSellerName(), auction.getSellerUUID(), System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(bidPlacedEvent);
                auctionStorage.purgeOldPlayerHistory(bidder.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());

                if (previousHighestBidderUUID != null) {
                    AuctionHistoryEvent outbidEvent = new AuctionHistoryEvent(
                            previousHighestBidderUUID, auction.getAuctionId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.BID_OUTBID, oldBid,
                            bidder.getName(), bidder.getUniqueId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(outbidEvent);
                    auctionStorage.purgeOldPlayerHistory(previousHighestBidderUUID, plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }
            }
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.NEW_BID));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la puja para la subasta ID: " + auction.getAuctionId(), e);
            messageManager.sendMessage(bidder, configManager.getPluginPrefix() + messageManager.getMessage("auction_bid_error_database"));
            econ.depositPlayer(bidder, bidAmount);
            return false;
        }
    }

    public boolean buyNow(Player buyer, UUID auctionId) {
        Auction auction = activeAuctionsCache.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE || !(auction.getBuyoutPrice() > 0) || !configManager.isBuyNowAllowed()) {
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_error_not_available"));
            return false;
        }
        if (auction.getSellerUUID().equals(buyer.getUniqueId())) {
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_error_own_auction"));
            return false;
        }
        Economy econ = AetherAuctions.getEconomy();
        double buyNowPrice = auction.getBuyoutPrice();
        if (!econ.has(buyer, buyNowPrice)) {
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_error_insufficient_funds"));
            return false;
        }
        EconomyResponse withdrawTx = econ.withdrawPlayer(buyer, buyNowPrice);
        if (!withdrawTx.transactionSuccess()) {
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_error_payment_failed"));
            return false;
        }

        if (auction.getHighestBidderUUID() != null && !auction.getHighestBidderUUID().equals(buyer.getUniqueId())) {
            OfflinePlayer previousHighestBidderOp = Bukkit.getOfflinePlayer(auction.getHighestBidderUUID());
            econ.depositPlayer(previousHighestBidderOp, auction.getCurrentBid());
             if (previousHighestBidderOp.isOnline() && previousHighestBidderOp.getPlayer() != null) {
                messageManager.sendMessage(previousHighestBidderOp.getPlayer(), configManager.getPluginPrefix() + messageManager.getMessage("auction_bought_out_refund", "%id%", auction.getAuctionId().toString()));
            }
            if (plugin.getConfigManager().isHistoryEnabled() && auction.getHighestBidderUUID() != null && !auction.getHighestBidderUUID().equals(buyer.getUniqueId())) {
                 AuctionHistoryEvent prevBidderRefundEvent = new AuctionHistoryEvent(
                        auction.getHighestBidderUUID(), auction.getAuctionId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.BID_REFUNDED_AUCTION_SOLD_BUYNOW, auction.getCurrentBid(),
                        buyer.getName(), buyer.getUniqueId(), System.currentTimeMillis()
                );
                try {
                    auctionStorage.saveHistoryEvent(prevBidderRefundEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getHighestBidderUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Error al guardar evento de historial (BID_REFUNDED_AUCTION_SOLD_BUYNOW): " + auction.getAuctionId(), ex);
                }
            }
        }

        double commission = calculateCommission(auction.getItemStack(), buyNowPrice);
        double amountToSeller = buyNowPrice - commission;
        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerUUID());

        auction.setStatus(AuctionStatus.SOLD_BUYNOW);
        auction.setHighestBidderUUID(buyer.getUniqueId());
        auction.setHighestBidderName(buyer.getName());
        auction.setCurrentBid(buyNowPrice);

        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getAuctionId());
            plugin.getLogger().info("Subasta " + auction.getAuctionId() + " marcada como SOLD_BUYNOW y guardada.");

            if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                Player sellerPlayer = sellerOffline.getPlayer();
                EconomyResponse depositTx = econ.depositPlayer(sellerPlayer, amountToSeller);
                if (depositTx.transactionSuccess()) {
                     messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_your_item_sold_buy_now_money_received",
                        "%buyer%", buyer.getName(),
                        "%price%", String.format("%.2f", buyNowPrice),
                        "%received%", String.format("%.2f", amountToSeller),
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%currency%", configManager.getCurrencySymbol(),
                        "%id%", auction.getAuctionId().toString()));
                } else {
                    plugin.getLogger().warning("Fallo al depositar dinero a vendedor online " + sellerOffline.getName() + " para subasta (compra directa) " + auction.getAuctionId() + ". Razón: " + depositTx.errorMessage + ". Se creará PendingReward.");
                    rewardManager.createPendingReward(
                        sellerOffline.getUniqueId(),
                        PendingReward.RewardType.MONEY_AUCTION_SOLD,
                        amountToSeller,
                        "reward_reason_money_sold_buynow_seller_error",
                        Arrays.asList(
                            "%amount%", String.format("%.2f", amountToSeller),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getAuctionId().toString(),
                            "%buyer%", buyer.getName(),
                            "%reason%", depositTx.errorMessage
                        )
                    );
                    messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_your_item_sold_buy_now_money_pending_error",
                        "%received%", String.format("%.2f", amountToSeller),
                        "%currency%", configManager.getCurrencySymbol(),
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getAuctionId().toString(),
                        "%reason%", depositTx.errorMessage));
                }
            } else {
                plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline para subasta (compra directa) " + auction.getAuctionId() + ". Se creará PendingReward (MONEY).");
                 rewardManager.createPendingReward(
                    sellerOffline.getUniqueId(),
                    PendingReward.RewardType.MONEY_AUCTION_SOLD,
                    amountToSeller,
                    "reward_reason_money_sold_buynow_seller_offline",
                    Arrays.asList(
                        "%amount%", String.format("%.2f", amountToSeller),
                        "%currency%", configManager.getCurrencySymbol(),
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        "%id%", auction.getAuctionId().toString(),
                        "%buyer%", buyer.getName()
                    )
                );
            }
            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent boughtEvent = new AuctionHistoryEvent(
                        buyer.getUniqueId(), auction.getAuctionId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.AUCTION_SOLD_TO_BUYER,
                        buyNowPrice, auction.getSellerName(), auction.getSellerUUID(), System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(boughtEvent);
                auctionStorage.purgeOldPlayerHistory(buyer.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());

                AuctionHistoryEvent soldEvent = new AuctionHistoryEvent(
                        auction.getSellerUUID(), auction.getAuctionId(), InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                        auction.getItemStack().getType().name(), SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.AUCTION_SOLD_FOR_SELLER,
                        amountToSeller,
                        buyer.getName(), buyer.getUniqueId(), System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(soldEvent);
                auctionStorage.purgeOldPlayerHistory(auction.getSellerUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al procesar compra directa (SOLD_BUYNOW) para subasta " + auction.getAuctionId() + ". El pago al vendedor podría no haberse procesado.", e);
            econ.depositPlayer(buyer, buyNowPrice);
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_error_database_reverted"));
            auction.setStatus(AuctionStatus.ACTIVE);
            if (!activeAuctionsCache.containsKey(auction.getAuctionId())) activeAuctionsCache.put(auction.getAuctionId(), auction);
            return false;
        }
        Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.SOLD_BUYNOW));

        boolean itemDeliveredToInv = false;
        if (buyer.getInventory().firstEmpty() != -1) {
            buyer.getInventory().addItem(auction.getItemStack().clone());
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_success_item_received",
                "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                "%id%", auction.getAuctionId().toString()
            ));
            itemDeliveredToInv = true;
        } else {
            messageManager.sendMessage(buyer, configManager.getPluginPrefix() + messageManager.getMessage("auction_buy_now_inventory_full_pending",
                "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                "%id%", auction.getAuctionId().toString()
            ));
            rewardManager.createPendingReward(
                buyer.getUniqueId(),
                PendingReward.RewardType.ITEM_AUCTION_WON,
                auction.getItemStack().clone(),
                "reward_reason_item_bought_inventory_full",
                Arrays.asList(
                    "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                    "%id%", auction.getAuctionId().toString()
                )
            );
        }

        if (itemDeliveredToInv) {
            plugin.getSoundManager().playSound(buyer, "auction_item_claimed");
        } else {
            plugin.getSoundManager().playSound(buyer, "inventory_full");
        }
        if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
             plugin.getSoundManager().playSound(sellerOffline.getPlayer(), "auction_sold_for_seller");
        }
        return true;
    }

    private void endAuction(Auction auction) {
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            plugin.getLogger().warning("endAuction llamado para una subasta no válida o no activa: " + (auction != null ? auction.getAuctionId() : "null"));
            return;
        }
        plugin.getLogger().info("Finalizando subasta ID: " + auction.getAuctionId());
        Economy econ = AetherAuctions.getEconomy();
        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerUUID());
        String itemNameForNotification;

        if (auction.getHighestBidderUUID() != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getHighestBidderUUID());
            double finalPrice = auction.getCurrentBid();
            auction.setStatus(AuctionStatus.SOLD_BID);

            try {
                auctionStorage.saveAuction(auction);
                activeAuctionsCache.remove(auction.getAuctionId());
                plugin.getLogger().info("Subasta " + auction.getAuctionId() + " marcada como SOLD_BID y guardada.");

                double commission = calculateCommission(auction.getItemStack(), finalPrice);
                double amountToSeller = finalPrice - commission;

                if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                    Player sellerPlayer = sellerOffline.getPlayer();
                    EconomyResponse tx = econ.depositPlayer(sellerPlayer, amountToSeller);
                    if (tx.transactionSuccess()) {
                        messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_your_item_sold_bid_money_received",
                            "%winner%", auction.getHighestBidderName(),
                            "%price%", String.format("%.2f", finalPrice),
                            "%received%", String.format("%.2f", amountToSeller),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%id%", auction.getAuctionId().toString()));
                        plugin.getSoundManager().playSound(sellerPlayer, "auction_sold_for_seller");
                    } else {
                        rewardManager.createPendingReward(
                            sellerOffline.getUniqueId(),
                            PendingReward.RewardType.MONEY_AUCTION_SOLD,
                            amountToSeller,
                            "reward_reason_money_sold_bid_seller_error",
                            Arrays.asList(
                                "%amount%", String.format("%.2f", amountToSeller),
                                "%currency%", configManager.getCurrencySymbol(),
                                "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                                "%id%", auction.getAuctionId().toString(),
                                "%winner%", auction.getHighestBidderName(),
                                "%reason%", tx.errorMessage
                            )
                        );
                        messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_your_item_sold_bid_money_pending_error",
                            "%received%", String.format("%.2f", amountToSeller),
                             "%currency%", configManager.getCurrencySymbol(),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getAuctionId().toString(),
                            "%reason%", tx.errorMessage));
                        plugin.getSoundManager().playSound(sellerPlayer, "error");
                    }
                } else {
                    rewardManager.createPendingReward(
                        sellerOffline.getUniqueId(),
                        PendingReward.RewardType.MONEY_AUCTION_SOLD,
                        amountToSeller,
                        "reward_reason_money_sold_bid_seller_offline",
                        Arrays.asList(
                            "%amount%", String.format("%.2f", amountToSeller),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()),
                            "%id%", auction.getAuctionId().toString(),
                            "%winner%", auction.getHighestBidderName()
                        )
                    );
                }

                List<ItemStack> itemsToDeliver = new ArrayList<>();
                itemNameForNotification = "Ítem Desconocido";
                if (auction.isMystery()) {
                    itemNameForNotification = auction.getMysteryDescription() != null && !auction.getMysteryDescription().isEmpty() ? auction.getMysteryDescription() : "Lote Misterioso";
                    try {
                        itemsToDeliver.addAll(auctionStorage.getMysteryAuctionContents(auction.getAuctionId()));
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Error obteniendo contenido de subasta misteriosa " + auction.getAuctionId() + " para el ganador.", e);
                    }
                } else if (auction.getItemStack() != null && auction.getItemStack().getType() != Material.AIR) {
                    itemsToDeliver.add(auction.getItemStack().clone());
                    itemNameForNotification = InventoryUtil.formatMaterialName(auction.getItemStack().getType());
                }

                boolean allItemsDeliveredToInv = true;
                if (!itemsToDeliver.isEmpty()) {
                    if (winner.isOnline() && winner.getPlayer() != null) {
                        Player winnerPlayer = winner.getPlayer();
                        for (ItemStack item : itemsToDeliver) {
                            if (winnerPlayer.getInventory().firstEmpty() != -1) {
                                winnerPlayer.getInventory().addItem(item);
                            } else {
                                allItemsDeliveredToInv = false;
                                rewardManager.createPendingReward(winner.getUniqueId(), PendingReward.RewardType.ITEM_AUCTION_WON, item, "reward_reason_item_won_inventory_full", Arrays.asList("%item_name%", InventoryUtil.formatMaterialName(item.getType()), "%id%", auction.getAuctionId().toString()));
                            }
                        }
                        if (allItemsDeliveredToInv) {
                            messageManager.sendMessage(winnerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_won_item_received", "%item_name%", itemNameForNotification, "%id%", auction.getAuctionId().toString()));
                            plugin.getSoundManager().playSound(winnerPlayer, "auction_won");
                        } else {
                            messageManager.sendMessage(winnerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_won_inventory_full_pending", "%item_name%", itemNameForNotification, "%id%", auction.getAuctionId().toString()));
                             plugin.getSoundManager().playSound(winnerPlayer, "inventory_full");
                        }
                    } else {
                        for (ItemStack item : itemsToDeliver) {
                            rewardManager.createPendingReward(winner.getUniqueId(), PendingReward.RewardType.ITEM_AUCTION_WON, item, "reward_reason_item_won_offline", Arrays.asList("%item_name%", InventoryUtil.formatMaterialName(item.getType()), "%id%", auction.getAuctionId().toString()));
                        }
                    }
                } else if (auction.isMystery()){
                     plugin.getLogger().warning("Subasta misteriosa " + auction.getAuctionId() + " ganada por " + winner.getName() + " no tenía ítems para entregar.");
                     if(winner.isOnline() && winner.getPlayer() != null) messageManager.sendMessage(winner.getPlayer(), configManager.getPluginPrefix() + messageManager.getMessage("auction_won_mystery_empty", "%id%", auction.getAuctionId().toString()));
                }


                if (plugin.getConfigManager().isHistoryEnabled()) {
                    AuctionHistoryEvent wonEvent = new AuctionHistoryEvent(
                            winner.getUniqueId(), auction.getAuctionId(), itemNameForNotification,
                            auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                            auction.isMystery() ? "Contiene " + itemsToDeliver.size() + " ítems" : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.AUCTION_WON_BY_BIDDER, finalPrice,
                            auction.getSellerName(), auction.getSellerUUID(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(wonEvent);
                    auctionStorage.purgeOldPlayerHistory(winner.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());

                     AuctionHistoryEvent soldForSellerEvent = new AuctionHistoryEvent(
                        auction.getSellerUUID(), auction.getAuctionId(), itemNameForNotification,
                         auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                         auction.isMystery() ? "Contiene " + itemsToDeliver.size() + " ítems" : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.AUCTION_SOLD_FOR_SELLER, amountToSeller,
                        winner.getName(), winner.getUniqueId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(soldForSellerEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getSellerUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error de DB al finalizar (SOLD_BID) subasta " + auction.getAuctionId() + ". El pago/entrega podría no haberse procesado.", e);
            }
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.SOLD_BID));

        } else {
            auction.setStatus(AuctionStatus.EXPIRED);
            try {
                auctionStorage.saveAuction(auction);
                activeAuctionsCache.remove(auction.getAuctionId());
                plugin.getLogger().info("Subasta " + auction.getAuctionId() + " marcada como EXPIRED y guardada.");

                itemNameForNotification = auction.isMystery() ? (auction.getMysteryDescription() != null && !auction.getMysteryDescription().isEmpty() ? auction.getMysteryDescription() : "Lote Misterioso") : InventoryUtil.formatMaterialName(auction.getItemStack().getType());

                List<ItemStack> itemsToReturnToSellerExpired = new ArrayList<>();
                 if (auction.isMystery()) {
                    try {
                        itemsToReturnToSellerExpired.addAll(auctionStorage.getMysteryAuctionContents(auction.getAuctionId()));
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Error obteniendo contenido de subasta misteriosa expirada " + auction.getAuctionId(), e);
                    }
                } else if (auction.getItemStack() != null && auction.getItemStack().getType() != Material.AIR) {
                    itemsToReturnToSellerExpired.add(auction.getItemStack().clone());
                }


                if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                    Player sellerPlayer = sellerOffline.getPlayer();
                    boolean allReturnedDirectly = true;
                    for (ItemStack item : itemsToReturnToSellerExpired) {
                        if (sellerPlayer.getInventory().firstEmpty() == -1) {
                            allReturnedDirectly = false;
                            rewardManager.createPendingReward(
                                sellerOffline.getUniqueId(),
                                PendingReward.RewardType.ITEM_AUCTION_RETURNED,
                                item,
                                "reward_reason_item_expired_inventory_full",
                                Arrays.asList(
                                    "%item_name%", InventoryUtil.formatMaterialName(item.getType()),
                                    "%id%", auction.getAuctionId().toString()
                                )
                            );
                        } else {
                             sellerPlayer.getInventory().addItem(item);
                        }
                    }
                    if (allReturnedDirectly && !itemsToReturnToSellerExpired.isEmpty()) {
                         messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_expired_item_returned",
                            "%item_name%", itemNameForNotification,
                            "%id%", auction.getAuctionId().toString()));
                    } else if (!itemsToReturnToSellerExpired.isEmpty()) {
                        messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_expired_inventory_full_pending",
                            "%item_name%", itemNameForNotification,
                            "%id%", auction.getAuctionId().toString()));
                    }

                } else if (!itemsToReturnToSellerExpired.isEmpty()) {
                    for (ItemStack item : itemsToReturnToSellerExpired) {
                         rewardManager.createPendingReward(
                            sellerOffline.getUniqueId(),
                            PendingReward.RewardType.ITEM_AUCTION_RETURNED,
                            item,
                            "reward_reason_item_expired_offline",
                            Arrays.asList(
                                "%item_name%", InventoryUtil.formatMaterialName(item.getType()),
                                "%id%", auction.getAuctionId().toString()
                            )
                        );
                    }
                }
                if (plugin.getConfigManager().isHistoryEnabled()) {
                    AuctionHistoryEvent expiredEvent = new AuctionHistoryEvent(
                            auction.getSellerUUID(), auction.getAuctionId(), itemNameForNotification,
                            auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                            auction.isMystery() ? auction.getMysteryDescription() : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.AUCTION_EXPIRED_RETURNED, 0,
                            null, null, System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(expiredEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getSellerUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Error de DB al finalizar (EXPIRED) subasta " + auction.getAuctionId() + ".", e);
            }
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.EXPIRED));
        }
    }

    public boolean cancelAuction(Player canceller, UUID auctionId) {
        Auction auction = activeAuctionsCache.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            messageManager.sendMessage(canceller, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancel_error_not_active"));
            return false;
        }

        boolean isAdmin = canceller.hasPermission("aetherauctions.admin.cancel");
        if (!auction.getSellerUUID().equals(canceller.getUniqueId()) && !isAdmin) {
            messageManager.sendMessage(canceller, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancel_error_no_permission"));
            return false;
        }

        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(auction.getSellerUUID());
        String itemDisplayName = auction.isMystery() ? (auction.getMysteryDescription() != null && !auction.getMysteryDescription().isEmpty() ? auction.getMysteryDescription() : "Lote Misterioso") : InventoryUtil.formatMaterialName(auction.getItemStack().getType());

        auction.setStatus(AuctionStatus.CANCELLED);
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getAuctionId());
            plugin.getLogger().info("Subasta " + auction.getAuctionId() + " marcada como CANCELLED y guardada.");
            String itemSnapshotForHistory = auction.isMystery() ? auction.getMysteryDescription() : SerializationUtil.itemStackToBase64(auction.getItemStack());
            String itemMaterialForHistory = auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name();


            List<ItemStack> itemsToReturnToSeller = new ArrayList<>();
            if (auction.isMystery()) {
                itemsToReturnToSeller.addAll(auctionStorage.getMysteryAuctionContents(auction.getAuctionId()));
            } else if (auction.getItemStack() != null && auction.getItemStack().getType() != Material.AIR) {
                itemsToReturnToSeller.add(auction.getItemStack().clone());
            }

            if (!itemsToReturnToSeller.isEmpty()) {
                if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                    Player onlineSeller = sellerOffline.getPlayer();
                    boolean allReturnedToInv = true;
                    for (ItemStack item : itemsToReturnToSeller) {
                        if (onlineSeller.getInventory().firstEmpty() == -1) {
                            allReturnedToInv = false;
                            rewardManager.createPendingReward(sellerOffline.getUniqueId(), PendingReward.RewardType.ITEM_AUCTION_RETURNED, item, "reward_reason_item_cancelled_return_pending", Arrays.asList("%item_name%", InventoryUtil.formatMaterialName(item.getType()), "%id%", auction.getAuctionId().toString(), "%canceller%", canceller.getName()));
                        } else {
                            onlineSeller.getInventory().addItem(item);
                        }
                    }
                    if (allReturnedToInv) {
                         messageManager.sendMessage(onlineSeller, configManager.getPluginPrefix() + messageManager.getMessage(sellerOffline.getUniqueId().equals(canceller.getUniqueId()) ? "auction_cancelled_item_returned_self" : "auction_cancelled_item_returned_by_admin", "%item_name%", itemDisplayName, "%id%", auction.getAuctionId().toString(), "%admin%", canceller.getName()));
                    } else {
                        messageManager.sendMessage(onlineSeller, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancelled_inventory_full_pending", "%item_name%", itemDisplayName, "%id%", auction.getAuctionId().toString()));
                    }
                } else {
                    for (ItemStack item : itemsToReturnToSeller) {
                        rewardManager.createPendingReward(sellerOffline.getUniqueId(), PendingReward.RewardType.ITEM_AUCTION_RETURNED, item, "reward_reason_item_cancelled_return_pending", Arrays.asList("%item_name%", InventoryUtil.formatMaterialName(item.getType()), "%id%", auction.getAuctionId().toString(), "%canceller%", canceller.getName()));
                    }
                }
            }


            if (auction.getHighestBidderUUID() != null && auction.getCurrentBid() > 0) {
                OfflinePlayer highestBidderOffline = Bukkit.getOfflinePlayer(auction.getHighestBidderUUID());
                double amountToRefund = auction.getCurrentBid();
                Economy econ = AetherAuctions.getEconomy();

                if (highestBidderOffline.isOnline() && highestBidderOffline.getPlayer() != null) {
                    Player highestBidderPlayer = highestBidderOffline.getPlayer();
                    EconomyResponse tx = econ.depositPlayer(highestBidderPlayer, amountToRefund);
                    if (tx.transactionSuccess()) {
                        messageManager.sendMessage(highestBidderPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancelled_bid_refunded",
                            "%id%", auction.getAuctionId().toString(),
                            "%amount%", String.format("%.2f", amountToRefund),
                            "%currency%", configManager.getCurrencySymbol()));
                    } else {
                        rewardManager.createPendingReward(
                            highestBidderOffline.getUniqueId(),
                            PendingReward.RewardType.MONEY_BID_REFUND,
                            amountToRefund,
                            "reward_reason_money_cancelled_refund_error",
                            Arrays.asList(
                                "%amount%", String.format("%.2f", amountToRefund),
                                "%currency%", configManager.getCurrencySymbol(),
                                "%id%", auction.getAuctionId().toString(),
                                "%reason%", tx.errorMessage
                            )
                        );
                        messageManager.sendMessage(highestBidderPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancelled_bid_refund_pending_error",
                            "%amount%", String.format("%.2f", amountToRefund),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%id%", auction.getAuctionId().toString(),
                            "%reason%", tx.errorMessage));
                    }
                } else {
                     rewardManager.createPendingReward(
                        highestBidderOffline.getUniqueId(),
                        PendingReward.RewardType.MONEY_BID_REFUND,
                        amountToRefund,
                        "reward_reason_money_cancelled_refund_offline",
                        Arrays.asList(
                            "%amount%", String.format("%.2f", amountToRefund),
                            "%currency%", configManager.getCurrencySymbol(),
                            "%id%", auction.getAuctionId().toString()
                        )
                    );
                }
                if (plugin.getConfigManager().isHistoryEnabled()) {
                    AuctionHistoryEvent bidRefundEvent = new AuctionHistoryEvent(
                            auction.getHighestBidderUUID(), auction.getAuctionId(), itemDisplayName,
                            auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                            auction.isMystery() ? auction.getMysteryDescription() : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                            AuctionHistoryEvent.HistoryEventType.BID_REFUNDED_AUCTION_CANCELLED, amountToRefund,
                            canceller.getName(), canceller.getUniqueId(), System.currentTimeMillis()
                    );
                    auctionStorage.saveHistoryEvent(bidRefundEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getHighestBidderUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                }
            }

            if (plugin.getConfigManager().isHistoryEnabled()) {
                 AuctionHistoryEvent.HistoryEventType cancelEventType = auction.getSellerUUID().equals(canceller.getUniqueId()) ?
                                                                  AuctionHistoryEvent.HistoryEventType.AUCTION_CANCELLED_BY_SELLER :
                                                                  AuctionHistoryEvent.HistoryEventType.AUCTION_CANCELLED_BY_ADMIN;
                AuctionHistoryEvent cancelEvent = new AuctionHistoryEvent(
                        auction.getSellerUUID(), auction.getAuctionId(), itemDisplayName,
                        auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                        auction.isMystery() ? auction.getMysteryDescription() : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        cancelEventType, auction.getCurrentBid(),
                        canceller.getName(), canceller.getUniqueId(), System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(cancelEvent);
                auctionStorage.purgeOldPlayerHistory(auction.getSellerUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
            }

            if (isAdmin && !auction.getSellerUUID().equals(canceller.getUniqueId())) {
                messageManager.sendMessage(canceller, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancelled_admin_success", "%id%", auction.getAuctionId().toString()));
            }
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.CANCELLED));
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al cancelar subasta " + auction.getAuctionId() + ". El reembolso/devolución podría no haberse procesado.", e);
            messageManager.sendMessage(canceller, configManager.getPluginPrefix() + messageManager.getMessage("auction_cancel_error_database"));
            auction.setStatus(AuctionStatus.ACTIVE);
            if (!activeAuctionsCache.containsKey(auction.getAuctionId())) activeAuctionsCache.put(auction.getAuctionId(), auction);
            return false;
        }
    }

    public boolean adminDeleteAuction(Auction auction, CommandSender adminSender) {
        if (auction == null) {
            messageManager.sendMessage(adminSender, configManager.getPluginPrefix() + messageManager.getMessage("admin_borrar_error_not_found_generic"));
            return false;
        }
        plugin.getLogger().info("Admin " + adminSender.getName() + " está borrando la subasta ID: " + auction.getAuctionId() + " (Vendedor: " + auction.getSellerName() + ", Ítem: " + (auction.getItemStack() != null ? auction.getItemStack().getType() : "Lote Misterioso") + ")");

        AuctionStatus originalStatus = auction.getStatus();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUUID());
        OfflinePlayer highestBidder = auction.getHighestBidderUUID() != null ? Bukkit.getOfflinePlayer(auction.getHighestBidderUUID()) : null;
        String currencySymbol = configManager.getCurrencySymbol();
        String itemDisplayName = auction.isMystery() ? (auction.getMysteryDescription() != null && !auction.getMysteryDescription().isEmpty() ? auction.getMysteryDescription() : "Lote Misterioso") : (auction.getItemStack() != null ? InventoryUtil.formatMaterialName(auction.getItemStack().getType()) : "Ítem Desconocido");


        if (originalStatus == AuctionStatus.ACTIVE || originalStatus == AuctionStatus.EXPIRED) {
            List<ItemStack> itemsToReturn = new ArrayList<>();
            if (auction.isMystery()) {
                try { itemsToReturn.addAll(auctionStorage.getMysteryAuctionContents(auction.getAuctionId())); }
                catch (SQLException e) { plugin.getLogger().log(Level.SEVERE, "Error obteniendo contenido de subasta misteriosa " + auction.getAuctionId() + " para borrado admin.", e); }
            } else if (auction.getItemStack() != null && auction.getItemStack().getType() != Material.AIR) {
                itemsToReturn.add(auction.getItemStack().clone());
            }

            if (!itemsToReturn.isEmpty()) {
                boolean itemsReturnedDirectly = true;
                 if (seller.isOnline() && seller.getPlayer() != null) {
                    Player sellerPlayer = seller.getPlayer();
                    for(ItemStack item : itemsToReturn) {
                        if (sellerPlayer.getInventory().firstEmpty() == -1) {
                            itemsReturnedDirectly = false;
                             rewardManager.createPendingReward(seller.getUniqueId(), PendingReward.RewardType.ITEM_AUCTION_RETURNED, item, "reward_reason_item_admin_deleted", Arrays.asList("%item_name%", InventoryUtil.formatMaterialName(item.getType()), "%id%", auction.getAuctionId().toString(), "%admin%", adminSender.getName()));
                        } else {
                            sellerPlayer.getInventory().addItem(item);
                        }
                    }
                    if (itemsReturnedDirectly) {
                        messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_admin_deleted_item_returned", "%id%", auction.getAuctionId().toString(), "%item_name%", itemDisplayName, "%admin%", adminSender.getName()));
                    } else {
                         messageManager.sendMessage(sellerPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_admin_deleted_item_pending_inv_full", "%id%", auction.getAuctionId().toString(), "%item_name%", itemDisplayName, "%admin%", adminSender.getName()));
                    }
                } else {
                    for(ItemStack item : itemsToReturn) {
                        rewardManager.createPendingReward(seller.getUniqueId(), PendingReward.RewardType.ITEM_AUCTION_RETURNED, item, "reward_reason_item_admin_deleted", Arrays.asList("%item_name%", InventoryUtil.formatMaterialName(item.getType()), "%id%", auction.getAuctionId().toString(), "%admin%", adminSender.getName()));
                    }
                }
            }
        }

        if (originalStatus == AuctionStatus.ACTIVE && highestBidder != null && auction.getHighestBidderUUID() != null && auction.getCurrentBid() > 0 && !auction.getBidHistory().isEmpty()) {
            double amountToRefundToBidder = auction.getCurrentBid();
            boolean bidRefundedDirectly = false;
            if (highestBidder.isOnline() && highestBidder.getPlayer() != null) {
                Player highestBidderPlayer = highestBidder.getPlayer();
                EconomyResponse tx = AetherAuctions.getEconomy().depositPlayer(highestBidderPlayer, amountToRefundToBidder);
                if (tx.transactionSuccess()) {
                     messageManager.sendMessage(highestBidderPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_admin_deleted_bid_refunded",
                        "%id%", auction.getAuctionId().toString(),
                        "%amount%", String.format("%.2f", amountToRefundToBidder),
                        "%currency%", currencySymbol,
                        "%admin%", adminSender.getName()));
                    bidRefundedDirectly = true;
                } else {
                     messageManager.sendMessage(highestBidderPlayer, configManager.getPluginPrefix() + messageManager.getMessage("auction_admin_deleted_bid_refund_pending_error",
                        "%id%", auction.getAuctionId().toString(),
                        "%amount%", String.format("%.2f", amountToRefundToBidder),
                        "%currency%", currencySymbol,
                        "%admin%", adminSender.getName(),
                        "%reason%", tx.errorMessage));
                }
            }
            if (!bidRefundedDirectly) {
                rewardManager.createPendingReward(
                    highestBidder.getUniqueId(),
                    PendingReward.RewardType.MONEY_BID_REFUND,
                    amountToRefundToBidder,
                    "reward_reason_money_admin_deleted_refund",
                     Arrays.asList(
                        "%amount%", String.format("%.2f", amountToRefundToBidder),
                        "%currency%", currencySymbol,
                        "%id%", auction.getAuctionId().toString(),
                        "%admin%", adminSender.getName()
                    )
                );
            }
            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent bidRefundEvent = new AuctionHistoryEvent(
                        auction.getHighestBidderUUID(), auction.getAuctionId(), itemDisplayName,
                        auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                        auction.isMystery() ? auction.getMysteryDescription() : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                        AuctionHistoryEvent.HistoryEventType.BID_REFUNDED_AUCTION_CANCELLED,
                        amountToRefundToBidder, adminSender.getName(), null, System.currentTimeMillis()
                );
                try {
                    auctionStorage.saveHistoryEvent(bidRefundEvent);
                    auctionStorage.purgeOldPlayerHistory(auction.getHighestBidderUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
                } catch (SQLException ex) {
                     plugin.getLogger().log(Level.SEVERE, "Error guardando historial de reembolso por borrado admin para subasta: " + auction.getAuctionId(), ex);
                }
            }
        }

        if (plugin.getConfigManager().isHistoryEnabled()) {
            AuctionHistoryEvent deleteEvent = new AuctionHistoryEvent(
                    auction.getSellerUUID(), auction.getAuctionId(), itemDisplayName,
                    auction.isMystery() ? Material.CHEST.name() : auction.getItemStack().getType().name(),
                    auction.isMystery() ? auction.getMysteryDescription() : SerializationUtil.itemStackToBase64(auction.getItemStack()),
                    AuctionHistoryEvent.HistoryEventType.AUCTION_CANCELLED_BY_ADMIN,
                    auction.getCurrentBid(), adminSender.getName(), null, System.currentTimeMillis()
            );
             try {
                auctionStorage.saveHistoryEvent(deleteEvent);
                auctionStorage.purgeOldPlayerHistory(auction.getSellerUUID(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
            } catch (SQLException ex) {
                 plugin.getLogger().log(Level.SEVERE, "Error guardando historial de borrado admin para subasta: " + auction.getAuctionId(), ex);
            }
        }

        auction.setStatus(AuctionStatus.ADMIN_DELETED);
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getAuctionId());
            plugin.getLogger().info("Subasta " + auction.getAuctionId() + " marcada como " + auction.getStatus() + " y eliminada del caché por admin " + adminSender.getName());
            messageManager.sendMessage(adminSender, configManager.getPluginPrefix() + messageManager.getMessage("admin_borrar_success", "%id%", auction.getAuctionId().toString(), "%item_name%", itemDisplayName));
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.CANCELLED));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al actualizar subasta " + auction.getAuctionId() + " durante borrado por admin.", e);
            messageManager.sendMessage(adminSender, configManager.getPluginPrefix() + messageManager.getMessage("admin_borrar_failed", "%id%", auction.getAuctionId().toString()));
            auction.setStatus(originalStatus);
            if (originalStatus == AuctionStatus.ACTIVE && !activeAuctionsCache.containsKey(auction.getAuctionId())) {
                 activeAuctionsCache.put(auction.getAuctionId(), auction);
            }
            return false;
        }
    }


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
                .filter(auction -> auction.getSellerUUID().equals(sellerId) && auction.getStatus() == AuctionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public Auction getAuctionByIdFuzzy(String idStr) {
        if (idStr == null || idStr.isEmpty()) return null;
        try {
            UUID fullUuid = UUID.fromString(idStr);
            return getAuctionById(fullUuid);
        } catch (IllegalArgumentException e) {
            for (Auction auction : activeAuctionsCache.values()) {
                if (auction.getAuctionId().toString().toLowerCase().startsWith(idStr.toLowerCase())) {
                    return auction;
                }
            }
        }
        return null;
    }

    public boolean createMysteryAuction(Player seller, List<ItemStack> lotItems, double startPrice, double buyNowPrice, long durationSeconds, String mysteryDescription) {
        if (lotItems == null || lotItems.isEmpty()) {
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("prepare_mystery_gui_error_no_items_on_confirm")); // Usar la clave más específica
            return false;
        }
        if (startPrice <= 0) { messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_invalid_start_price")); return false; }
        if (buyNowPrice > 0 && buyNowPrice <= startPrice) { messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_buy_now_too_low")); return false; }
        if (durationSeconds <= 0) { messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_invalid_duration")); return false; }

        int maxAuctions = configManager.getMaxActiveAuctionsPerPlayer(); // No necesita 'seller'
        if (getPlayerActiveAuctions(seller.getUniqueId()).size() >= maxAuctions) {
            messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_max_auctions_reached", "%limit%", String.valueOf(maxAuctions)));
            return false;
        }

        Economy econ = AetherAuctions.getEconomy();
        double creationFee = configManager.getAuctionCreationFee(); // No necesita 'seller'
        if (creationFee > 0) {
            if (!econ.has(seller, creationFee)) {
                messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_insufficient_funds_fee", "%fee%", String.valueOf(creationFee)));
                return false;
            }
            EconomyResponse feeTx = econ.withdrawPlayer(seller, creationFee);
            if (!feeTx.transactionSuccess()) {
                messageManager.sendMessage(seller, configManager.getPluginPrefix() + messageManager.getMessage("auction_create_error_fee_charge_failed"));
                return false; // No devolver la tarifa aquí, se hace en el catch si falla el guardado de la subasta
            }
            // Mensaje de tarifa cobrada se envía solo si la subasta se crea con éxito.
        }

        UUID auctionId = UUID.randomUUID();
        long creationTime = System.currentTimeMillis();
        long expirationTime = creationTime + (durationSeconds * 1000L);

        Auction auction = new Auction(
                auctionId, seller.getUniqueId(), seller.getName(),
                null,
                startPrice, buyNowPrice > 0 && configManager.isBuyNowAllowed() ? buyNowPrice : -1,
                creationTime, expirationTime,
                AuctionStatus.ACTIVE, null, null, 0.0, new ArrayList<>(),
                true, mysteryDescription
        );

        try {
            auctionStorage.saveAuction(auction);
            auctionStorage.saveMysteryAuctionContents(auctionId, lotItems);

            activeAuctionsCache.put(auction.getAuctionId(), auction);

            if (creationFee > 0) { // Enviar mensaje de tarifa solo si todo fue bien
                messageManager.sendMessage(seller, "auction_create_fee_charged", "%fee%", String.valueOf(creationFee)); // Corregido
            }
            messageManager.sendMessage(seller, "mystery_auction_create_success", "%id%", auction.getAuctionId().toString().substring(0,8)); // Corregido y ID acortado
            plugin.getSoundManager().playSound(seller, "auction_created");


            if (plugin.getConfigManager().isHistoryEnabled()) {
                AuctionHistoryEvent historyEvent = new AuctionHistoryEvent(
                        seller.getUniqueId(), auction.getAuctionId(),
                        mysteryDescription != null && !mysteryDescription.isEmpty() ? mysteryDescription : "Lote Misterioso",
                        Material.CHEST.name(),
                        "Contiene " + lotItems.size() + " ítems.",
                        AuctionHistoryEvent.HistoryEventType.AUCTION_CREATED,
                        auction.getStartPrice(), null, null, System.currentTimeMillis()
                );
                auctionStorage.saveHistoryEvent(historyEvent);
                auctionStorage.purgeOldPlayerHistory(seller.getUniqueId(), plugin.getConfigManager().getHistoryRecordsPerPlayer());
            }
            Bukkit.getPluginManager().callEvent(new AuctionUpdateEvent(auction, AuctionUpdateEvent.UpdateType.NEW_AUCTION_LISTED));
            if (configManager.getAuctionCreationCooldownSeconds() > 0) {
                playerLastAuctionCreationTime.put(seller.getUniqueId(), System.currentTimeMillis());
            }
            return true; // Éxito
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la nueva subasta misteriosa ID: " + auction.getAuctionId(), e);
            messageManager.sendMessage(seller, "auction_create_error_database"); // Corregido
            if (creationFee > 0) {
                econ.depositPlayer(seller, creationFee); // Devolver tarifa si el guardado falló
                messageManager.sendMessage(seller, "auction_create_fee_refunded_on_error", "%fee%", String.valueOf(creationFee)); // Corregido (asumiendo que esta clave es para un mensaje completo)
            }
            return false; // Fallo
        }
    }


    private double calculateCommission(ItemStack item, double salePrice) {
        String type = configManager.getCommissionType();
        double commissionPercentage = 0;
        Material itemMaterial = (item != null) ? item.getType() : null;

        switch (type.toLowerCase()) {
            case "per_category_percentage":
                if (itemMaterial == null) {
                    commissionPercentage = configManager.getDefaultOtherCategoryCommissionPercentage();
                    plugin.getLogger().fine("Calculating commission for null/mystery item, using default_other_category_percentage: " + commissionPercentage);
                    break;
                }
                Map<String, Double> categoryPercentages = configManager.getCategoryCommissionPercentagesMap();
                String materialName = itemMaterial.name();
                if (categoryPercentages.containsKey(materialName)) {
                    commissionPercentage = categoryPercentages.get(materialName);
                } else {
                    commissionPercentage = configManager.getDefaultOtherCategoryCommissionPercentage();
                }
                break;
            case "tiered_percentage":
                List<Map<String, Object>> tiers = configManager.getTieredCommissionTiers();
                tiers.sort((t1, t2) -> {
                    double price1 = -1;
                    double price2 = -1;
                    Object p1Obj = t1.get("max_price");
                    Object p2Obj = t2.get("max_price");

                    if (p1Obj instanceof Number) price1 = ((Number) p1Obj).doubleValue();
                    if (p2Obj instanceof Number) price2 = ((Number) p2Obj).doubleValue();

                    if (price1 < 0) price1 = Double.MAX_VALUE;
                    if (price2 < 0) price2 = Double.MAX_VALUE;
                    return Double.compare(price1, price2);
                });

                boolean tierFound = false;
                for (Map<String, Object> tier : tiers) {
                    Object maxPriceObj = tier.get("max_price");
                    Object percentageObj = tier.get("percentage");

                    if (maxPriceObj instanceof Number && percentageObj instanceof Number) {
                        double maxPrice = ((Number) maxPriceObj).doubleValue();
                        double percentage = ((Number) percentageObj).doubleValue();
                        if (maxPrice < 0 || salePrice <= maxPrice) {
                            commissionPercentage = percentage;
                            tierFound = true;
                            break;
                        }
                    } else {
                        plugin.getLogger().warning("Skipping invalid tier in calculateCommission (non-numeric price/percentage): " + tier);
                    }
                }
                 if (!tierFound) {
                    plugin.getLogger().warning("Tiered commission: Sale price " + salePrice + " did not match any defined tier. Falling back to default commission.");
                    commissionPercentage = configManager.getDefaultCommissionPercentage();
                }
                break;
            case "flat_percentage":
            default:
                commissionPercentage = configManager.getDefaultCommissionPercentage();
                break;
        }
        return salePrice * (commissionPercentage / 100.0);
    }
}
