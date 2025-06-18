package com.julesmc.subastas.tasks;

import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.managers.EconomyManager;
import com.julesmc.subastas.managers.LocaleManager;
import com.julesmc.subastas.objects.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

public class AuctionEndTask extends BukkitRunnable {

    private final SubastasPlugin plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final LocaleManager localeManager;

    public AuctionEndTask(SubastasPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();
        this.localeManager = plugin.getLocaleManager();
    }

    @Override
    public void run() {
        plugin.getLogger().info(localeManager.getRawMessage("logs.task-auction-end-start"));
        List<AuctionItem> activeAuctions = databaseManager.getActiveAuctions();

        if (activeAuctions.isEmpty()) {
            // No need to log if no auctions are active, or a very light log.
            // plugin.getLogger().info(localeManager.getRawMessage("logs.task-auction-end-processing", "count", "0"));
            return;
        }

        plugin.getLogger().info(localeManager.getRawMessage("logs.task-auction-end-processing", "count", String.valueOf(activeAuctions.size())));

        int processed = 0;
        int sold = 0;
        int expired = 0;
        int failedPayment = 0;

        for (AuctionItem auction : activeAuctions) {
            if (System.currentTimeMillis() >= auction.getEndTime()) {
                processed++;
                UUID sellerUuid = auction.getSellerUuid();
                Player sellerOnline = Bukkit.getPlayer(sellerUuid);
                String itemName = auction.getItemName();

                if (auction.getHighestBidderUuid() != null) {
                    UUID buyerUuid = auction.getHighestBidderUuid();
                    OfflinePlayer buyerOffline = Bukkit.getOfflinePlayer(buyerUuid);
                    Player buyerOnline = Bukkit.getPlayer(buyerUuid);
                    double winningBid = auction.getCurrentBid();

                    if (economyManager.withdrawPlayer(buyerOffline, winningBid)) {
                        double commissionRate = plugin.getConfigManager().getDouble("auction-settings.commission.sales-tax-percentage", 0.0);
                        double commission = winningBid * commissionRate;
                        double amountToSeller = winningBid - commission;

                        economyManager.depositPlayer(Bukkit.getOfflinePlayer(sellerUuid), amountToSeller);
                        databaseManager.updateAuctionStatus(auction.getId(), "SOLD");
                        sold++;

                        if (sellerOnline != null) {
                            sellerOnline.sendMessage(localeManager.getMessage("auction.sold-to-player-notification-seller",
                                    "item", itemName, "amount", String.format("%,.2f", amountToSeller), "buyer", buyerOffline.getName()));
                        }
                        if (buyerOnline != null) {
                            buyerOnline.sendMessage(localeManager.getMessage("auction.won-notification-buyer",
                                    "item", itemName, "price", String.format("%,.2f", winningBid)));
                        }
                        plugin.getLogger().info(localeManager.getRawMessage("logs.auction-processed-sold",
                            "id", String.valueOf(auction.getId()), "item", itemName,
                            "buyer", buyerOffline.getName(), "price", String.format("%,.2f", winningBid)));

                    } else {
                        databaseManager.updateAuctionStatus(auction.getId(), "FAILED_PAYMENT");
                        failedPayment++;
                        if (sellerOnline != null) {
                            sellerOnline.sendMessage(localeManager.getMessage("auction.buyer-no-funds-notification-seller",
                                    "item", itemName, "buyer", buyerOffline.getName()));
                        }
                        if (buyerOnline != null) {
                            buyerOnline.sendMessage(localeManager.getMessage("auction.payment-failed-notification-buyer",
                                    "item", itemName));
                        }
                        plugin.getLogger().warning(localeManager.getRawMessage("logs.auction-processed-payment-failed",
                            "id", String.valueOf(auction.getId()), "item", itemName,
                            "buyer", buyerOffline.getName(), "price", String.format("%,.2f", winningBid) ));
                    }
                } else {
                    databaseManager.updateAuctionStatus(auction.getId(), "EXPIRED");
                    expired++;
                    if (sellerOnline != null) {
                        sellerOnline.sendMessage(localeManager.getMessage("auction.expired-no-bids-notification-seller", "item", itemName));
                    }
                    plugin.getLogger().info(localeManager.getRawMessage("logs.auction-processed-expired",
                        "id", String.valueOf(auction.getId()), "item", itemName));
                }
            }
        }
        if (processed > 0) {
            plugin.getLogger().info(localeManager.getRawMessage("logs.task-auction-end-summary",
                "processed", String.valueOf(processed), "sold", String.valueOf(sold),
                "expired", String.valueOf(expired), "failed_payment", String.valueOf(failedPayment)
            ));

            // Schedule GUI refresh on the main thread
            if (processed > 0) { // Only refresh if something actually changed
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getGuiManager() != null) { // Ensure GuiManager is available
                        plugin.getGuiManager().refreshOpenAuctionGuis();
                    }
                });
            }
        }
    }
}
