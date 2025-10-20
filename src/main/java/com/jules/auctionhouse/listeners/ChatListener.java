package com.jules.auctionhouse.listeners;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.guis.AuctionCreationGUI;
import com.jules.auctionhouse.managers.AuctionManager;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final AuctionHouse plugin;

    public ChatListener(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        AuctionManager auctionManager = plugin.getAuctionManager();
        AuctionManager.ChatInputType inputType = auctionManager.getWaitingChatInputType(player);

        if (inputType == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        Auction wizard = auctionManager.getCreationWizard(player);

        try {
            switch (inputType) {
                case PRICE:
                    double price = Double.parseDouble(message);
                    if (price > 0) wizard.setCurrentPrice(price);
                    else player.sendMessage("§cEl precio debe ser mayor que cero.");
                    break;
                case DURATION:
                    long duration;
                    String dStr = message.toLowerCase();
                    long dVal = Long.parseLong(dStr.replaceAll("[^0-9]", ""));
                    if (dStr.endsWith("s")) duration = dVal;
                    else if (dStr.endsWith("m")) duration = dVal * 60;
                    else if (dStr.endsWith("h")) duration = dVal * 3600;
                    else if (dStr.endsWith("d")) duration = dVal * 86400;
                    else duration = Long.parseLong(dStr);
                    wizard.setEndTime(System.currentTimeMillis() + (duration * 1000));
                    break;
                case BUY_NOW:
                    double buyNowPrice = Double.parseDouble(message);
                    if (buyNowPrice > wizard.getCurrentPrice() || buyNowPrice <= 0) {
                        wizard.setBuyNowPrice(buyNowPrice);
                    } else {
                        player.sendMessage("§cEl precio de 'Comprar Ya' debe ser mayor que el precio inicial.");
                    }
                    break;
            }
            player.sendMessage("§aValor actualizado.");
        } catch (Exception e) {
            player.sendMessage("§cEntrada inválida.");
        }

        auctionManager.setWaitingForChatInput(player, null); // Dejar de esperar entrada

        // Volver a abrir la GUI en el hilo principal
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            new AuctionCreationGUI(wizard).open(player);
        });
    }
}