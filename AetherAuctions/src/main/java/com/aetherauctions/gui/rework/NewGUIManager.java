package com.aetherauctions.gui.rework;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NewGUIManager {

    private final AetherAuctions plugin;
    private final Map<UUID, Integer> playerCurrentMainPage = new HashMap<>();
    private final Map<UUID, Integer> playerPendingBidAuctionId = new HashMap<>();

    public NewGUIManager(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    public void openNewMainAuctionGUI(Player player, int page) {
        NewMainAuctionGUI mainGUI = new NewMainAuctionGUI(plugin, player, page);
        mainGUI.open();
        playerCurrentMainPage.put(player.getUniqueId(), page);
    }

    public void openAuctionDetailsGUI(Player player, AuctionItem auctionItem, int fromPage) {
        AuctionDetailsGUI detailsGUI = new AuctionDetailsGUI(plugin, player, auctionItem, fromPage);
        detailsGUI.open();
    }

    public int getPlayerCurrentMainPage(UUID playerUUID) {
        return playerCurrentMainPage.getOrDefault(playerUUID, 0);
    }

    public void removePlayerPageState(UUID playerUUID) {
        playerCurrentMainPage.remove(playerUUID);
        playerPendingBidAuctionId.remove(playerUUID);
    }

    public void clearAllPlayerStates() {
        playerCurrentMainPage.clear();
        playerPendingBidAuctionId.clear();
    }

    public void setPlayerPendingBid(UUID playerUUID, int auctionId) {
        playerPendingBidAuctionId.put(playerUUID, auctionId);
    }

    public Integer getAndRemovePlayerPendingBidAuctionId(UUID playerUUID) {
        return playerPendingBidAuctionId.remove(playerUUID);
    }

    public boolean isPlayerPendingBid(UUID playerUUID) {
        return playerPendingBidAuctionId.containsKey(playerUUID);
    }
}
