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
            messageManager.sendMessage(seller, "auction_create_success", "%id%", auction.getId().toString().substring(0, 8));
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

        if (previousHighestBidder != null) {
            econ.depositPlayer(previousHighestBidder, oldBid);
            if (previousHighestBidder.isOnline() && previousHighestBidder.getPlayer() != null) {
                messageManager.sendMessage(previousHighestBidder.getPlayer(), "auction_outbid_notification", "%auction_id%", auction.getId().toString().substring(0,8));
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
                messageManager.sendMessage(seller, "auction_new_bid_on_your_item", "%bidder%", bidder.getName(), "%amount%", String.valueOf(bidAmount));
            }
            // TODO: Actualizar GUIs abiertas
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la puja para la subasta ID: " + auction.getId(), e);
            messageManager.sendMessage(bidder, "auction_bid_error_database");
            econ.depositPlayer(bidder, bidAmount);
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
                messageManager.sendMessage(previousHighestBidder.getPlayer(), "auction_bought_out_refund", "%auction_id%", auction.getId().toString().substring(0,8));
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
                        "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()));
                } else {
                    plugin.getLogger().warning("Fallo al depositar dinero a vendedor online " + sellerOffline.getName() + " para subasta (compra directa) " + auction.getId() + ". Razón: " + tx.errorMessage + ". Se creará PendingReward.");
                    // TODO PASO 13: Crear PendingReward (MONEY) para el vendedor
                    messageManager.sendMessage(sellerOffline.getPlayer(), "auction_your_item_sold_buy_now_money_pending_error", "%reason%", tx.errorMessage);
                }
            } else {
                plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline para subasta (compra directa) " + auction.getId() + ". Se creará PendingReward (MONEY).");
                // TODO PASO 13: Crear PendingReward (MONEY) para el vendedor
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al procesar compra directa (SOLD_BUYNOW) para subasta " + auction.getId() + ". El pago al vendedor podría no haberse procesado.", e);
            econ.depositPlayer(buyer, buyNowPrice);
            messageManager.sendMessage(buyer, "auction_buy_now_error_database_reverted");
            // Revert status in memory if save failed, so it's not stuck in SOLD_BUYNOW if payment couldn't be made to seller
            auction.setStatus(AuctionStatus.ACTIVE);
            auction.setHighestBidderId(null); // Revert bidder info as well
            auction.setHighestBidderName(null);
            // auction.setCurrentBid(oldBid); // Revert to old bid if any - oldBid is not defined in this scope
            return false;
        }

        if (buyer.getInventory().firstEmpty() == -1) {
            messageManager.sendMessage(buyer, "auction_buy_now_inventory_full");
            // TODO PASO 13: Crear PendingReward (ITEM) para el comprador
        } else {
            buyer.getInventory().addItem(auction.getItemStack().clone());
            messageManager.sendMessage(buyer, "buy_now_success"); // Generic success, item received.
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
                            "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()));
                    } else {
                        plugin.getLogger().warning("Fallo al depositar dinero a vendedor online " + sellerOffline.getName() + " para subasta " + auction.getId() + ". Razón: " + tx.errorMessage + ". Se creará PendingReward.");
                        // TODO PASO 13: Crear PendingReward (MONEY) para el vendedor
                        messageManager.sendMessage(sellerOffline.getPlayer(), "auction_your_item_sold_bid_money_pending_error", "%reason%", tx.errorMessage);
                    }
                } else {
                    plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline para subasta " + auction.getId() + ". Se creará PendingReward (MONEY).");
                    // TODO PASO 13: Crear PendingReward (MONEY) para el vendedor
                }

                // Dar ítem al ganador
                if (winner.isOnline() && winner.getPlayer() != null && winner.getPlayer().getInventory().firstEmpty() == -1) {
                    messageManager.sendMessage(winner.getPlayer(), "auction_won_inventory_full", "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()));
                    // TODO PASO 13: Crear PendingReward (ITEM) para el ganador
                } else if (winner.isOnline() && winner.getPlayer() != null) {
                     winner.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                     messageManager.sendMessage(winner.getPlayer(), "auction_won_item_received", "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()));
                } else {
                    plugin.getLogger().info("Ganador " + winner.getName() + " offline. Ítem para subasta " + auction.getId() + " necesita ser reclamado.");
                    // TODO PASO 13: Crear PendingReward (ITEM) para el ganador (offline)
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error de DB al finalizar (SOLD_BID) subasta " + auction.getId() + ". El pago/entrega podría no haberse procesado.", e);
                // Auction status not updated in DB, so it might be re-processed or require admin. Cache state is not reverted here.
            }
        } else {
            auction.setStatus(AuctionStatus.EXPIRED);
            try {
                auctionStorage.saveAuction(auction);
                activeAuctionsCache.remove(auction.getId());
                plugin.getLogger().info("Subasta " + auction.getId() + " marcada como EXPIRED y guardada.");

                // Devolver ítem al vendedor
                if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null && sellerOffline.getPlayer().getInventory().firstEmpty() == -1) {
                    messageManager.sendMessage(sellerOffline.getPlayer(), "auction_expired_inventory_full", "%item_name%", InventoryUtil.formatMaterialName(auction.getItemStack().getType()));
                    // TODO PASO 13: Crear PendingReward (ITEM) para el vendedor
                } else if (sellerOffline.isOnline() && sellerOffline.getPlayer() != null) {
                    sellerOffline.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                    messageManager.sendMessage(sellerOffline.getPlayer(), "auction_expired_item_returned");
                } else {
                    plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline. Ítem de subasta expirada " + auction.getId() + " necesita ser reclamado.");
                    // TODO PASO 13: Crear PendingReward (ITEM) para el vendedor (offline)
                }
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Error de DB al finalizar (EXPIRED) subasta " + auction.getId() + ".", e);
            }
        }
        // TODO: Actualizar GUIs
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
                     messageManager.sendMessage(sellerOffline.getPlayer(), "auction_cancelled_item_returned_self", "%item_name%", itemDisplayName);
                } else {
                     messageManager.sendMessage(sellerOffline.getPlayer(), "auction_cancelled_item_returned_by_admin", "%item_name%", itemDisplayName, "%admin%", canceller.getName());
                }
            } else {
                 plugin.getLogger().info("Vendedor " + sellerOffline.getName() + " offline. Ítem de subasta cancelada " + auction.getId() + " (" + itemDisplayName + ") necesita ser reclamado.");
                 // TODO PASO 13: Crear PendingReward (ITEM) para el vendedor (offline)
            }

            // Reembolsar al pujador más alto si existe
            if (auction.getHighestBidderId() != null) {
                OfflinePlayer highestBidderOffline = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
                double amountToRefund = auction.getCurrentBid();
                Economy econ = AetherAuctions.getEconomy();

                if (highestBidderOffline.isOnline() && highestBidderOffline.getPlayer() != null) {
                    EconomyResponse tx = econ.depositPlayer(highestBidderOffline, amountToRefund);
                    if (tx.transactionSuccess()) {
                        messageManager.sendMessage(highestBidderOffline.getPlayer(), "auction_cancelled_bid_refunded",
                            "%auction_id_short%", auction.getId().toString().substring(0,8),
                            "%amount%", String.format("%.2f", amountToRefund));
                    } else {
                        plugin.getLogger().warning("Fallo al reembolsar puja a " + highestBidderOffline.getName() + " para subasta cancelada " + auction.getId() + ". Razón: " + tx.errorMessage + ". Se creará PendingReward.");
                        // TODO PASO 13: Crear PendingReward (MONEY_REFUND) para highestBidderOffline
                        messageManager.sendMessage(highestBidderOffline.getPlayer(), "auction_cancelled_bid_refund_pending_error", "%reason%", tx.errorMessage);
                    }
                } else {
                    plugin.getLogger().info("Pujador " + highestBidderOffline.getName() + " offline para reembolso de subasta cancelada " + auction.getId() + ". Se creará PendingReward (MONEY_REFUND).");
                    // TODO PASO 13: Crear PendingReward (MONEY_REFUND) para highestBidderOffline
                }
            }
             // Notificar al cancelador (si es admin y no el mismo vendedor)
            if (isAdmin && !auction.getSellerId().equals(canceller.getUniqueId())) {
                messageManager.sendMessage(canceller, "auction_cancelled_admin_success", "%id%", auction.getId().toString().substring(0,8));
            }
            // TODO: Actualizar GUIs
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al cancelar subasta " + auction.getId() + ". El reembolso/devolución podría no haberse procesado.", e);
            messageManager.sendMessage(canceller, "auction_cancel_error_database");
            // Revert status in cache as DB operation failed
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
                        "%id_short%", auction.getId().toString().substring(0,8),
                        "%item_name%", InventoryUtil.formatMaterialName(itemToReturn.getType()),
                        "%admin%", adminSender.getName());
                    itemReturnedDirectly = true;
                } else {
                    messageManager.sendMessage(seller.getPlayer(), "auction_admin_deleted_item_pending_inv_full",
                         "%id_short%", auction.getId().toString().substring(0,8),
                         "%item_name%", InventoryUtil.formatMaterialName(itemToReturn.getType()),
                         "%admin%", adminSender.getName());
                }
            }
            if (!itemReturnedDirectly) {
                plugin.getLogger().info("Ítem de subasta " + auction.getId() + " borrada por admin irá a PendingReward para vendedor " + seller.getName());
                com.aetherauctions.model.PendingReward itemReward = new com.aetherauctions.model.PendingReward( // Explicitly use model package
                    seller.getUniqueId(),
                    com.aetherauctions.model.PendingReward.RewardType.ITEM_AUCTION_RETURNED, // Reusar este tipo
                    itemToReturn,
                    "reward_reason_item_admin_deleted",
                    java.util.Arrays.asList( // Explicitly use java.util.Arrays
                        "%item_name%", InventoryUtil.formatMaterialName(itemToReturn.getType()),
                        "%id_short%", auction.getId().toString().substring(0,8),
                        "%admin%", adminSender.getName()
                    )
                );
                try {
                    auctionStorage.saveReward(itemReward);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Fallo al guardar PendingReward (ítem admin_deleted) para " + seller.getName() + ", subasta " + auction.getId(), e);
                }
            }
        }

        // 2. Reembolsar al pujador más alto (si la subasta estaba activa y había un pujador válido con puja)
        // highestBidderId check already done for highestBidder initialization
        if (originalStatus == AuctionStatus.ACTIVE && highestBidder != null && auction.getHighestBidderId() != null && auction.getCurrentBid() > 0 && !auction.getBidHistory().isEmpty()) {
            double amountToRefundToBidder = auction.getCurrentBid();
            boolean bidRefundedDirectly = false;
            if (highestBidder.isOnline() && highestBidder.getPlayer() != null) {
                EconomyResponse tx = AetherAuctions.getEconomy().depositPlayer(highestBidder, amountToRefundToBidder);
                if (tx.transactionSuccess()) {
                    messageManager.sendMessage(highestBidder.getPlayer(), "auction_admin_deleted_bid_refunded",
                        "%id_short%", auction.getId().toString().substring(0,8),
                        "%amount%", String.format("%.2f %s", amountToRefundToBidder, currencySymbol),
                        "%admin%", adminSender.getName());
                    bidRefundedDirectly = true;
                } else {
                     messageManager.sendMessage(highestBidder.getPlayer(), "auction_admin_deleted_bid_refund_pending_error",
                        "%id_short%", auction.getId().toString().substring(0,8),
                        "%amount%", String.format("%.2f %s", amountToRefundToBidder, currencySymbol),
                        "%admin%", adminSender.getName(),
                        "%reason%", tx.errorMessage);
                }
            }
            if (!bidRefundedDirectly) {
                plugin.getLogger().info("Reembolso para pujador " + highestBidder.getName() + " (subasta " + auction.getId() + " borrada por admin) irá a PendingReward.");
                com.aetherauctions.model.PendingReward moneyRefundReward = new com.aetherauctions.model.PendingReward( // Explicitly use model package
                    highestBidder.getUniqueId(),
                    com.aetherauctions.model.PendingReward.RewardType.MONEY_BID_REFUND, // Reusar este tipo
                    amountToRefundToBidder,
                    "reward_reason_money_admin_deleted_refund",
                     java.util.Arrays.asList( // Explicitly use java.util.Arrays
                        "%amount%", String.format("%.2f %s", amountToRefundToBidder, currencySymbol),
                        "%id_short%", auction.getId().toString().substring(0,8),
                        "%admin%", adminSender.getName()
                    )
                );
                try {
                    auctionStorage.saveReward(moneyRefundReward);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Fallo al guardar PendingReward (reembolso admin_deleted) para " + highestBidder.getName() + ", subasta " + auction.getId(), e);
                }
            }
        }

        // 3. Actualizar estado de la subasta y guardar/eliminar de caché
        auction.setStatus(AuctionStatus.CANCELLED); // O un nuevo AuctionStatus.ADMIN_DELETED
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            plugin.getLogger().info("Subasta " + auction.getId() + " marcada como " + auction.getStatus() + " y eliminada del caché por admin " + adminSender.getName());
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al actualizar subasta " + auction.getId() + " durante borrado por admin.", e);
            // Attempt to revert in-memory state if DB save failed
            auction.setStatus(originalStatus);
            if (originalStatus == AuctionStatus.ACTIVE && !activeAuctionsCache.containsKey(auction.getId())) {
                 activeAuctionsCache.put(auction.getId(), auction); // Put back if it was active and removed
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
                if (auction.getId().toString().startsWith(idStr.toLowerCase())) {
                    return auction;
                }
            }
        }
        return null;
    }
}
