package com.jules.auctionmasterelite.listeners;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.managers.PlayerInputManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.Consumer;

public class PlayerChatListener implements Listener {

    private final PlayerInputManager inputManager;

    public PlayerChatListener(AuctionMasterElite plugin) {
        this.inputManager = plugin.getPlayerInputManager();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!inputManager.isWaitingForInput(player.getUniqueId())) {
            return;
        }

        // Cancel the event to prevent the message from being broadcast
        event.setCancelled(true);

        Consumer<String> callback = inputManager.getCallback(player.getUniqueId());
        if (callback != null) {
            callback.accept(event.getMessage());
        }

        // Remove player from input mode
        inputManager.removePlayerInput(player.getUniqueId());
    }
}
