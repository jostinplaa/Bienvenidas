package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all active auctions in memory.
 */
public class AuctionManager {

    private final AuctionMasterElite plugin;
    private final Map<UUID, Auction> activeAuctions;

    public AuctionManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
        this.activeAuctions = new ConcurrentHashMap<>(plugin.getDatabaseManager().loadAuctions());
    }

    /**
     * Creates a new auction and adds it to the manager.
     *
     * @param auction The auction to create.
     */
    public void createAuction(Auction auction) {
        activeAuctions.put(auction.getAuctionId(), auction);

        // Send Discord notification if manager is available
        if (plugin.getDiscordManager() != null) {
            plugin.getDiscordManager().sendNewAuctionNotification(auction);
        }

        // Announce Flash auctions globally
        if (auction.getType() == com.jules.auctionmasterelite.data.AuctionType.FLASH) {
            String itemName = auction.getItem().hasItemMeta() && auction.getItem().getItemMeta().hasDisplayName()
                    ? auction.getItem().getItemMeta().getDisplayName()
                    : auction.getItem().getType().toString().replace("_", " ").toLowerCase();
            String message = String.format("§6§l[SUBASTA FLASH] §e¡%s ha iniciado una subasta de %s por solo 60 segundos! ¡Date prisa! §f/ah",
                    auction.getSellerName(), itemName);
            Bukkit.broadcastMessage(message);
        }
    }

    public void placeBid(Player player, UUID auctionId, double amount) {
        Auction auction = getAuction(auctionId);

        // --- Validation Checks ---
        if (auction == null || auction.getStatus() != com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE) {
            player.sendMessage("§cEsta subasta ya no está activa.");
            return;
        }
        if (auction.getSellerId().equals(player.getUniqueId())) {
            player.sendMessage("§cNo puedes pujar en tu propia subasta.");
            return;
        }
        if (auction.getType() == com.jules.auctionmasterelite.data.AuctionType.PRIVATE && !auction.getInvitedPlayers().contains(player.getUniqueId())) {
            player.sendMessage("§cEsta es una subasta privada y no has sido invitado.");
            return;
        }
        if (amount <= auction.getCurrentBid()) {
            player.sendMessage("§cTu puja debe ser mayor que la puja actual de §6" + String.format("%.2f", auction.getCurrentBid()));
            return;
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        if (!economyManager.hasEnough(player, amount)) {
            player.sendMessage("§cNo tienes fondos suficientes para realizar esa puja.");
            return;
        }

        // --- Logic ---
        OfflinePlayer previousTopBidder = auction.getTopBidderId() != null ? Bukkit.getOfflinePlayer(auction.getTopBidderId()) : null;

        // Withdraw from new bidder
        economyManager.withdraw(player, amount);

        // Refund previous bidder
        if (previousTopBidder != null) {
            economyManager.deposit(previousTopBidder, auction.getCurrentBid());
            if (previousTopBidder.isOnline()) {
                previousTopBidder.getPlayer().sendMessage(String.format("§e¡Tu puja de §6%.2f§e ha sido superada en la subasta de %s!", auction.getCurrentBid(), auction.getItem().getType()));
            }
        }

        // Update auction object
        com.jules.auctionmasterelite.data.Bid newBid = new com.jules.auctionmasterelite.data.Bid(player.getUniqueId(), player.getName(), amount, System.currentTimeMillis());
        auction.addBid(newBid);

        // Update database
        plugin.getDatabaseManager().saveBid(newBid, auctionId);
        plugin.getDatabaseManager().updateAuctionBid(auction);

        // --- Notifications ---
        player.sendMessage(String.format("§a¡Has pujado §6%.2f §ay ahora eres el pujador más alto!", amount));

        // Notify seller
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
        if (seller.isOnline()) {
            seller.getPlayer().sendMessage(String.format("§d¡Alguien ha pujado §6%.2f §den tu subasta de %s!", amount, auction.getItem().getType()));
        }
    }

    /**
     * Retrieves an auction by its ID.
     *
     * @param auctionId The UUID of the auction.
     * @return The Auction object, or null if not found.
     */
    public Auction getAuction(UUID auctionId) {
        return activeAuctions.get(auctionId);
    }

    /**
     * Removes an auction from the manager.
     *
     * @param auctionId The UUID of the auction to remove.
     */
    public void removeAuction(UUID auctionId) {
        activeAuctions.remove(auctionId);
    }

    /**
     * Gets a map of all active auctions.
     *
     * @return A map of active auctions.
     */
    public Map<UUID, Auction> getActiveAuctions() {
        return activeAuctions.entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<Auction> getAuctionsBySeller(UUID sellerId) {
        return activeAuctions.values().stream()
                .filter(auction -> auction.getSellerId().equals(sellerId))
                .collect(Collectors.toList());
    }

    public List<Auction> getAuctionsByBidder(UUID bidderId) {
        return activeAuctions.values().stream()
                .filter(auction -> bidderId.equals(auction.getTopBidderId()))
                .collect(Collectors.toList());
    }

    /**
     * Periodically called to check for and end expired auctions.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Auction auction : activeAuctions.values()) {
            if (auction.getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE && now >= auction.getEndTime()) {
                endAuction(auction);
            }
        }
    }

    private void endAuction(Auction auction) {
        auction.setStatus(com.jules.auctionmasterelite.data.AuctionStatus.FINISHED);
        plugin.getDatabaseManager().updateAuctionStatus(auction);
        System.out.println("Auction " + auction.getAuctionId() + " has ended.");

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());

        // Case 1: There was a winner
        if (auction.getTopBidderId() != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getTopBidderId());

            // Pay the seller
            plugin.getEconomyManager().deposit(seller, auction.getCurrentBid());
            if (seller.isOnline()) {
                seller.getPlayer().sendMessage(String.format("§aTu subasta de %s ha finalizado. ¡Has ganado §6%.2f!", auction.getItem().getType(), auction.getCurrentBid()));
            }

            // Give item to winner
            if (winner.isOnline()) {
                Player winnerPlayer = winner.getPlayer();
                if (winnerPlayer.getInventory().firstEmpty() == -1) {
                    // Inventory is full, drop at their location
                    winnerPlayer.getWorld().dropItem(winnerPlayer.getLocation(), auction.getItem());
                    winnerPlayer.sendMessage("§e¡Ganaste una subasta pero tu inventario estaba lleno! El objeto ha sido dropeado a tus pies.");
                } else {
                    winnerPlayer.getInventory().addItem(auction.getItem());
                    winnerPlayer.sendMessage("§a¡Has ganado la subasta de %s! El objeto ha sido añadido a tu inventario.");
                }
            } else {
                // TODO: Implement a more robust offline item delivery system (e.g., /claim command)
                // For now, we can't safely give the item. We'll just log it.
                plugin.getLogger().warning("Player " + winner.getName() + " won auction " + auction.getAuctionId() + " but is offline. Item delivery pending robust system.");
            }

        } else { // Case 2: No bids
            if (seller.isOnline()) {
                Player sellerPlayer = seller.getPlayer();
                 if (sellerPlayer.getInventory().firstEmpty() == -1) {
                    sellerPlayer.getWorld().dropItem(sellerPlayer.getLocation(), auction.getItem());
                    sellerPlayer.sendMessage("§eTu subasta de %s finalizó sin pujas. Tu inventario estaba lleno, así que el objeto fue dropeado a tus pies.");
                } else {
                    sellerPlayer.getInventory().addItem(auction.getItem());
                    sellerPlayer.sendMessage("§eTu subasta de %s finalizó sin pujas. El objeto ha sido devuelto a tu inventario.");
                }
            } else {
                // TODO: Implement robust offline item delivery
                plugin.getLogger().warning("Auction " + auction.getAuctionId() + " for player " + seller.getName() + " ended with no bids, but player is offline. Item return pending robust system.");
            }
        }
    }
}
