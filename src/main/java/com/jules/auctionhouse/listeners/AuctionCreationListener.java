package com.jules.auctionhouse.listeners;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.managers.AuctionManager;
import com.jules.auctionhouse.models.Auction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class AuctionCreationListener implements Listener {

    private final AuctionHouse plugin;

    public AuctionCreationListener(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Crear Subasta")) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        AuctionManager auctionManager = plugin.getAuctionManager();
        Auction wizard = auctionManager.getCreationWizard(player);

        if (wizard == null) {
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        switch (clickedItem.getType()) {
            case GOLD_INGOT:
                player.closeInventory();
                player.sendMessage("§ePor favor, escribe el precio inicial en el chat.");
                auctionManager.setWaitingForChatInput(player, AuctionManager.ChatInputType.PRICE);
                break;
            case CLOCK:
                player.closeInventory();
                player.sendMessage("§ePor favor, escribe la duración en el chat (ej. 10s, 5m, 1h).");
                auctionManager.setWaitingForChatInput(player, AuctionManager.ChatInputType.DURATION);
                break;
            case EMERALD:
                player.closeInventory();
                player.sendMessage("§ePor favor, escribe el precio de 'Comprar Ya' en el chat (o 0 para desactivar).");
                auctionManager.setWaitingForChatInput(player, AuctionManager.ChatInputType.BUY_NOW);
                break;
            case LIME_STAINED_GLASS_PANE:
                player.closeInventory();
                long duration = (wizard.getEndTime() - System.currentTimeMillis()) / 1000;
                auctionManager.createAuction(player, wizard.getItem(), duration, wizard.getCurrentPrice(), wizard.getBuyNowPrice());
                auctionManager.endCreationWizard(player);
                break;
            case RED_STAINED_GLASS_PANE:
                player.closeInventory();
                auctionManager.endCreationWizard(player);
                break;
        }
    }
}