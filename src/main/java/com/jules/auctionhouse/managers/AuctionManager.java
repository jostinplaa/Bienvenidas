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

    public enum ChatInputType { PRICE, DURATION, BUY_NOW }

    private final AuctionHouse plugin;
    private final Map<UUID, Auction> activeAuctions = new ConcurrentHashMap<>();
    private final Map<UUID, Auction> creationWizards = new ConcurrentHashMap<>();
    private final Map<UUID, ChatInputType> waitingForChatInput = new ConcurrentHashMap<>();

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

    public void startCreationWizard(Player player, ItemStack item) {
        long duration = plugin.getConfigManager().getDefaultDuration();
        Auction wizard = new Auction(player.getUniqueId(), item, duration, 1.0, -1.0);
        creationWizards.put(player.getUniqueId(), wizard);
    }

    public Auction getCreationWizard(Player player) {
        return creationWizards.get(player.getUniqueId());
    }

    public void endCreationWizard(Player player) {
        creationWizards.remove(player.getUniqueId());
        waitingForChatInput.remove(player.getUniqueId());
    }

    public void setWaitingForChatInput(Player player, ChatInputType type) {
        waitingForChatInput.put(player.getUniqueId(), type);
    }

    public ChatInputType getWaitingChatInputType(Player player) {
        return waitingForChatInput.get(player.getUniqueId());
    }

    public boolean placeBid(Player bidder, UUID auctionId, double amount) {
        Auction auction = getAuction(auctionId);
        if (auction == null || auction.getStatus() != Auction.AuctionStatus.ACTIVE) {
            bidder.sendMessage("§cEsta subasta ya no está activa.");
            return false;
        }

        String prefix = plugin.getConfigManager().getPrefix();
        if (auction.getSellerUuid().equals(bidder.getUniqueId())) {
            bidder.sendMessage(prefix + " §cNo puedes pujar en tu propia subasta.");
            return false;
        }

        double minIncrementPercentage = plugin.getConfigManager().getMinimumBidIncrement();
        double minIncrement = auction.getCurrentPrice() * minIncrementPercentage;
        double minBid = auction.getCurrentPrice() + minIncrement;

        EconomyManager econ = plugin.getEconomyManager();

        if (amount < minBid) {
            bidder.sendMessage(prefix + " §cTu puja debe ser de al menos " + econ.format(minBid));
            return false;
        }

        if (!econ.hasEnough(bidder, amount)) {
            bidder.sendMessage(prefix + " §cNo tienes fondos suficientes para realizar esa puja.");
            return false;
        }

        if (auction.getCurrentWinner() != null) {
            OfflinePlayer previousWinner = Bukkit.getOfflinePlayer(auction.getCurrentWinner());
            econ.deposit(previousWinner, auction.getCurrentPrice());
            if (previousWinner.isOnline()) {
                previousWinner.getPlayer().sendMessage(prefix + " §e¡Te han superado en la subasta por " + auction.getItem().getType() + "!");
            }
        }

        econ.withdraw(bidder, amount);

        auction.setCurrentPrice(amount);
        auction.setCurrentWinner(bidder.getUniqueId());
        plugin.getDatabaseManager().updateAuction(auction);

        bidder.sendMessage(prefix + " §a¡Has pujado " + econ.format(amount) + " con éxito!");

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
        if (seller.isOnline()) {
            seller.getPlayer().sendMessage(prefix + " §b" + bidder.getName() + " ha pujado " + econ.format(amount) + " en tu " + auction.getItem().getType() + "!");
        }

        return true;
    }

    public boolean buyNow(Player buyer, UUID auctionId) {
        Auction auction = getAuction(auctionId);
        String prefix = plugin.getConfigManager().getPrefix();
        if (auction == null || auction.getStatus() != Auction.AuctionStatus.ACTIVE) {
            buyer.sendMessage(prefix + " §cEsta subasta ya no está activa.");
            return false;
        }

        if (auction.getBuyNowPrice() <= 0) {
            buyer.sendMessage(prefix + " §cEste item no se puede comprar directamente.");
            return false;
        }

        EconomyManager econ = plugin.getEconomyManager();
        if (!econ.hasEnough(buyer, auction.getBuyNowPrice())) {
            buyer.sendMessage(prefix + " §cNo tienes fondos suficientes para comprar este item.");
            return false;
        }

        if (auction.getCurrentWinner() != null) {
            OfflinePlayer previousWinner = Bukkit.getOfflinePlayer(auction.getCurrentWinner());
            econ.deposit(previousWinner, auction.getCurrentPrice());
            if (previousWinner.isOnline()) {
                previousWinner.getPlayer().sendMessage(prefix + " §eLa subasta por " + auction.getItem().getType() + " fue comprada directamente.");
            }
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());

        double commission = auction.getBuyNowPrice() * plugin.getConfigManager().getSellerCommission();
        double finalPrice = auction.getBuyNowPrice() - commission;

        econ.withdraw(buyer, auction.getBuyNowPrice());
        econ.deposit(seller, finalPrice);

        buyer.getInventory().addItem(auction.getItem());

        buyer.sendMessage(prefix + " §a¡Has comprado " + auction.getItem().getType().toString() + " por " + econ.format(auction.getBuyNowPrice()) + "!");
        if (seller.isOnline()) {
            seller.getPlayer().sendMessage(prefix + " §a¡Tu " + auction.getItem().getType().toString() + " fue comprado por " + buyer.getName() + "! Recibiste " + econ.format(finalPrice));
        }

        auction.setStatus(Auction.AuctionStatus.SOLD);
        plugin.getDatabaseManager().updateAuction(auction);
        activeAuctions.remove(auctionId);

        return true;
    }
}