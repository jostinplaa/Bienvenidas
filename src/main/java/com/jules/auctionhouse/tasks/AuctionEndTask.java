package com.jules.auctionhouse.tasks;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.managers.AuctionManager;
import com.jules.auctionhouse.managers.EconomyManager;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

public class AuctionEndTask extends BukkitRunnable {

    private final AuctionHouse plugin;

    public AuctionEndTask(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        AuctionManager auctionManager = plugin.getAuctionManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        for (Auction auction : auctionManager.getActiveAuctions()) {
            if (auction.hasExpired()) {
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());

                if (auction.getCurrentWinner() != null) {
                    // La subasta tiene un ganador
                    OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getCurrentWinner());

                    // Pagar al vendedor
                    economyManager.deposit(seller, auction.getCurrentPrice());

                    // Entregar item al ganador (o a un sistema de correo si está offline)
                    if (winner.isOnline()) {
                        winner.getPlayer().getInventory().addItem(auction.getItem());
                        winner.getPlayer().sendMessage("§a¡Has ganado la subasta por " + auction.getItem().getType() + "!");
                    } else {
                        // Lógica de correo/entrega offline (se puede implementar más tarde)
                    }

                    if(seller.isOnline()){
                        seller.getPlayer().sendMessage("§a¡Tu subasta por " + auction.getItem().getType() + " ha finalizado! Has ganado " + economyManager.format(auction.getCurrentPrice()));
                    }

                    auction.setStatus(Auction.AuctionStatus.SOLD);
                } else {
                    // La subasta expiró sin pujas
                    if (seller.isOnline()) {
                        seller.getPlayer().getInventory().addItem(auction.getItem());
                        seller.getPlayer().sendMessage("§eTu subasta por " + auction.getItem().getType() + " ha expirado sin pujas.");
                    } else {
                        // Lógica de correo/entrega offline
                    }
                    auction.setStatus(Auction.AuctionStatus.EXPIRED);
                }

                // Archivar y remover
                plugin.getDatabaseManager().archiveAuction(auction);
                auctionManager.removeAuctionFromMemory(auction.getAuctionId());
            }
        }
    }
}