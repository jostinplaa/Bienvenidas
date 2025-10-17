package com.jules.auctionhouse.managers;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionManager {

    private final AuctionHouse plugin;
    private final Map<UUID, Auction> activeAuctions = new ConcurrentHashMap<>();

    public AuctionManager(AuctionHouse plugin) {
        this.plugin = plugin;
        loadAuctions();
    }

    private void loadAuctions() {
        plugin.getDatabaseManager().loadAllActiveAuctions().forEach(auction -> {
            activeAuctions.put(auction.getAuctionId(), auction);
        });
        plugin.getLogger().info("Cargadas " + activeAuctions.size() + " subastas activas.");
    }

    public void createAuction(Player seller, ItemStack item, long duration, double startPrice, double buyNowPrice) {
        Auction newAuction = new Auction(seller.getUniqueId(), item, duration, startPrice, buyNowPrice);
        activeAuctions.put(newAuction.getAuctionId(), newAuction);
        plugin.getDatabaseManager().saveAuction(newAuction);

        ItemStack itemToRemove = item.clone();
        itemToRemove.setAmount(1);
        seller.getInventory().removeItem(itemToRemove);

        seller.sendMessage("§a¡Tu subasta ha sido creada con éxito!");
    }

    public void createAuction(Player seller, ItemStack item, long duration, double startPrice) {
        createAuction(seller, item, duration, startPrice, -1);
    }

    public Auction getAuction(UUID auctionId) {
        return activeAuctions.get(auctionId);
    }

    public Collection<Auction> getActiveAuctions() {
        return activeAuctions.values().stream()
                .filter(auction -> auction.getStatus() == Auction.AuctionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public void removeAuction(UUID auctionId) {
        activeAuctions.remove(auctionId);
        plugin.getDatabaseManager().deleteAuction(auctionId);
    }

    public void removeAuctionFromMemory(UUID auctionId) {
        activeAuctions.remove(auctionId);
    }

    public boolean placeBid(Player bidder, UUID auctionId, double amount) {
        Auction auction = getAuction(auctionId);
        if (auction == null || auction.getStatus() != Auction.AuctionStatus.ACTIVE) {
            bidder.sendMessage("§cEsta subasta ya no está activa.");
            return false;
        }

        if (auction.getSellerUuid().equals(bidder.getUniqueId())) {
            bidder.sendMessage("§cNo puedes pujar en tu propia subasta.");
            return false;
        }

        double minIncrement = auction.getCurrentPrice() * 0.05;
        double minBid = auction.getCurrentPrice() + minIncrement;

        EconomyManager econ = plugin.getEconomyManager();

        if (amount < minBid) {
            bidder.sendMessage("§cTu puja debe ser de al menos " + econ.format(minBid));
            return false;
        }

        if (!econ.hasEnough(bidder, amount)) {
            bidder.sendMessage("§cNo tienes fondos suficientes para realizar esa puja.");
            return false;
        }

        if (auction.getCurrentWinner() != null) {
            OfflinePlayer previousWinner = Bukkit.getOfflinePlayer(auction.getCurrentWinner());
            econ.deposit(previousWinner, auction.getCurrentPrice());
            if (previousWinner.isOnline()) {
                previousWinner.getPlayer().sendMessage("§e¡Te han superado en la subasta por " + auction.getItem().getType() + "!");
            }
        }

        econ.withdraw(bidder, amount);

        auction.setCurrentPrice(amount);
        auction.setCurrentWinner(bidder.getUniqueId());
        plugin.getDatabaseManager().updateAuction(auction);

        bidder.sendMessage("§a¡Has pujado " + econ.format(amount) + " con éxito!");

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
        if (seller.isOnline()) {
            seller.getPlayer().sendMessage("§b" + bidder.getName() + " ha pujado " + econ.format(amount) + " en tu " + auction.getItem().getType() + "!");
        }

        return true;
    }

    public boolean buyNow(Player buyer, UUID auctionId) {
        Auction auction = getAuction(auctionId);
        if (auction == null || auction.getStatus() != Auction.AuctionStatus.ACTIVE) {
            buyer.sendMessage("§cEsta subasta ya no está activa.");
            return false;
        }

        if (auction.getBuyNowPrice() <= 0) {
            buyer.sendMessage("§cEste item no se puede comprar directamente.");
            return false;
        }

        EconomyManager econ = plugin.getEconomyManager();
        if (!econ.hasEnough(buyer, auction.getBuyNowPrice())) {
            buyer.sendMessage("§cNo tienes fondos suficientes para comprar este item.");
            return false;
        }

        if (auction.getCurrentWinner() != null) {
            OfflinePlayer previousWinner = Bukkit.getOfflinePlayer(auction.getCurrentWinner());
            econ.deposit(previousWinner, auction.getCurrentPrice());
            if (previousWinner.isOnline()) {
                previousWinner.getPlayer().sendMessage("§eLa subasta por " + auction.getItem().getType() + " fue comprada directamente.");
            }
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());

        econ.withdraw(buyer, auction.getBuyNowPrice());
        econ.deposit(seller, auction.getBuyNowPrice());

        buyer.getInventory().addItem(auction.getItem());

        buyer.sendMessage("§a¡Has comprado " + auction.getItem().getType().toString() + " por " + econ.format(auction.getBuyNowPrice()) + "!");
        if (seller.isOnline()) {
            seller.getPlayer().sendMessage("§a¡Tu " + auction.getItem().getType().toString() + " fue comprado por " + buyer.getName() + "!");
        }

        auction.setStatus(Auction.AuctionStatus.SOLD);
        plugin.getDatabaseManager().updateAuction(auction);
        activeAuctions.remove(auctionId);

        return true;
    }
}