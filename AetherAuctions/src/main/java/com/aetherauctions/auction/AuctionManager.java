package com.aetherauctions.auction;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.model.Bid;
import com.aetherauctions.storage.AuctionStorage;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.CommandSender; // Import CommandSender
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
        // Assuming getExpirationCheckIntervalSeconds() exists in ConfigManager
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
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
        econ.depositPlayer(seller, amountToSeller);

        if (buyer.getInventory().firstEmpty() == -1) {
            messageManager.sendMessage(buyer, "auction_buy_now_inventory_full");
            // TODO: Implement claim system
            econ.depositPlayer(buyer, buyNowPrice); // Refund buyer
            econ.withdrawPlayer(seller, amountToSeller); // Revert payment to seller
            return false;
        } else {
            buyer.getInventory().addItem(auction.getItemStack().clone());
        }

        auction.setStatus(AuctionStatus.SOLD_BUYNOW);
        auction.setHighestBidderId(buyer.getUniqueId());
        auction.setHighestBidderName(buyer.getName());
        auction.setCurrentBid(buyNowPrice);

        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            messageManager.sendMessage(buyer, "auction_buy_now_success");
            if (seller.isOnline() && seller.getPlayer() != null) {
                messageManager.sendMessage(seller.getPlayer(), "auction_your_item_sold_buy_now", "%buyer%", buyer.getName(), "%price%", String.valueOf(buyNowPrice));
            }
            // TODO: Actualizar GUIs
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la subasta (compra directa) ID: " + auction.getId(), e);
            messageManager.sendMessage(buyer, "auction_buy_now_error_database");
            return false;
        }
    }

    private void endAuction(Auction auction) {
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            plugin.getLogger().warning("endAuction llamado para una subasta no válida o no activa: " + (auction != null ? auction.getId() : "null"));
            return;
        }
        plugin.getLogger().info("Finalizando subasta ID: " + auction.getId());
        Economy econ = AetherAuctions.getEconomy();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());

        if (auction.getHighestBidderId() != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
            double finalPrice = auction.getCurrentBid();

            double commission = finalPrice * (configManager.getCommissionPercentage() / 100.0);
            double amountToSeller = finalPrice - commission;
            econ.depositPlayer(seller, amountToSeller);

            if (winner.isOnline() && winner.getPlayer() != null && winner.getPlayer().getInventory().firstEmpty() == -1) {
                messageManager.sendMessage(winner.getPlayer(), "auction_won_inventory_full", "%item_name%", auction.getItemStack().getType().toString());
                 // TODO: Sistema de Claim
            } else if (winner.isOnline() && winner.getPlayer() != null) {
                 winner.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                 messageManager.sendMessage(winner.getPlayer(), "auction_won_item_received", "%item_name%", auction.getItemStack().getType().toString());
            } else {
                plugin.getLogger().info("Ganador " + winner.getName() + " offline. Ítem para subasta " + auction.getId() + " necesita ser reclamado.");
                 // TODO: Sistema de Claim para jugadores offline
            }
            auction.setStatus(AuctionStatus.SOLD_BID);
             if (seller.isOnline() && seller.getPlayer() != null) {
                messageManager.sendMessage(seller.getPlayer(), "auction_your_item_sold_bid", "%winner%", winner.getName(), "%price%", String.valueOf(finalPrice));
            }

        } else {
            auction.setStatus(AuctionStatus.EXPIRED);
            if (seller.isOnline() && seller.getPlayer() != null && seller.getPlayer().getInventory().firstEmpty() == -1) {
                messageManager.sendMessage(seller.getPlayer(), "auction_expired_inventory_full", "%item_name%", auction.getItemStack().getType().toString());
                // TODO: Sistema de Claim
            } else if (seller.isOnline() && seller.getPlayer() != null) {
                seller.getPlayer().getInventory().addItem(auction.getItemStack().clone());
                 messageManager.sendMessage(seller.getPlayer(), "auction_expired_item_returned");
            } else {
                 plugin.getLogger().info("Vendedor " + seller.getName() + " offline. Ítem de subasta expirada " + auction.getId() + " necesita ser reclamado.");
                // TODO: Sistema de Claim para jugadores offline
            }
        }

        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            // TODO: Actualizar GUIs
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar el estado final de la subasta ID: " + auction.getId(), e);
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

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
         if (seller.isOnline() && seller.getPlayer() != null && seller.getPlayer().getInventory().firstEmpty() == -1) {
            messageManager.sendMessage(seller.getPlayer(), "auction_cancelled_inventory_full", "%item_name%", auction.getItemStack().getType().toString());
            // TODO: Sistema de Claim
        } else if (seller.isOnline() && seller.getPlayer() != null) {
            seller.getPlayer().getInventory().addItem(auction.getItemStack().clone());
            if (seller.getUniqueId().equals(canceller.getUniqueId())) {
                 messageManager.sendMessage(seller.getPlayer(), "auction_cancelled_item_returned_self");
            } else {
                 messageManager.sendMessage(seller.getPlayer(), "auction_cancelled_item_returned_by_admin", "%admin%", canceller.getName());
            }
        } else {
             plugin.getLogger().info("Vendedor " + seller.getName() + " offline. Ítem de subasta cancelada " + auction.getId() + " necesita ser reclamado.");
             // TODO: Sistema de Claim para jugadores offline
        }

        if (auction.getHighestBidderId() != null) {
            OfflinePlayer highestBidder = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
            AetherAuctions.getEconomy().depositPlayer(highestBidder, auction.getCurrentBid());
            if (highestBidder.isOnline() && highestBidder.getPlayer() != null) {
                messageManager.sendMessage(highestBidder.getPlayer(), "auction_cancelled_bid_refunded", "%auction_id%", auction.getId().toString().substring(0,8));
            }
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            if (isAdmin && !auction.getSellerId().equals(canceller.getUniqueId())) {
                messageManager.sendMessage(canceller, "auction_cancelled_admin_success", "%id%", auction.getId().toString().substring(0,8));
            } else if (auction.getSellerId().equals(canceller.getUniqueId())) {
                 // No enviar mensaje de éxito si ya se envió el de item_returned_self
            }
            // TODO: Actualizar GUIs
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la subasta cancelada ID: " + auction.getId(), e);
            messageManager.sendMessage(canceller, "auction_cancel_error_database");
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
        // Try parsing as full UUID first
        try {
            UUID fullUuid = UUID.fromString(idStr);
            return getAuctionById(fullUuid); // Check cache primarily
        } catch (IllegalArgumentException e) {
            // Not a full UUID, try as a short ID prefix
            for (Auction auction : activeAuctionsCache.values()) {
                if (auction.getId().toString().startsWith(idStr.toLowerCase())) {
                    return auction;
                }
            }
        }
        return null; // Not found by full UUID or short ID in cache
    }

    public boolean adminDeleteAuction(Auction auction, CommandSender admin) {
        if (auction == null) {
            plugin.getLogger().warning("Admin " + admin.getName() + " intentó borrar una subasta null.");
            return false;
        }
        plugin.getLogger().info("Admin " + admin.getName() + " está borrando la subasta ID: " + auction.getId());

        // Similar logic to cancelAuction but without permission checks for self
        // Devolver ítem al vendedor
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
         if (seller.isOnline() && seller.getPlayer() != null && seller.getPlayer().getInventory().firstEmpty() == -1) {
            messageManager.sendMessage(seller.getPlayer(), "auction_cancelled_inventory_full", "%item_name%", auction.getItemStack().getType().toString());
            // TODO: Sistema de Claim
        } else if (seller.isOnline() && seller.getPlayer() != null) {
            seller.getPlayer().getInventory().addItem(auction.getItemStack().clone());
            messageManager.sendMessage(seller.getPlayer(), "auction_cancelled_item_returned_by_admin", "%admin%", admin.getName());
        } else {
             plugin.getLogger().info("Vendedor " + seller.getName() + " offline. Ítem de subasta borrada por admin (ID: " + auction.getId() + ") necesita ser reclamado.");
             // TODO: Sistema de Claim para jugadores offline
        }

        // Reembolsar al pujador más alto si existe
        if (auction.getHighestBidderId() != null) {
            OfflinePlayer highestBidder = Bukkit.getOfflinePlayer(auction.getHighestBidderId());
            AetherAuctions.getEconomy().depositPlayer(highestBidder, auction.getCurrentBid());
            if (highestBidder.isOnline() && highestBidder.getPlayer() != null) {
                messageManager.sendMessage(highestBidder.getPlayer(), "auction_cancelled_bid_refunded", "%auction_id%", auction.getId().toString().substring(0,8));
            }
        }

        auction.setStatus(AuctionStatus.CANCELLED); // Or a new status like ADMIN_REMOVED
        try {
            auctionStorage.saveAuction(auction);
            activeAuctionsCache.remove(auction.getId());
            // messageManager.sendMessage(admin, "admin_borrar_success", "%id%", auction.getId().toString().substring(0,8)); // Message sent by CommandManager
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de DB al intentar borrar subasta por admin ID: " + auction.getId(), e);
            // messageManager.sendMessage(admin, "admin_borrar_failed", "%id%", auction.getId().toString().substring(0,8)); // Message sent by CommandManager
            // Attempt to revert status in cache if DB save failed, though item/bid refunds are not reverted here
            auction.setStatus(AuctionStatus.ACTIVE); // Simplistic rollback for cache
            return false;
        }
    }
}
