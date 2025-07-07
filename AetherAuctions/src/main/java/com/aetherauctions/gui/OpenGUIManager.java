package com.aetherauctions.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OpenGUIManager {

    private final Map<UUID, GUIInfo> openGUIs = new ConcurrentHashMap<>();

    public void playerOpenedGUI(Player player, Inventory inventory, int page, String guiType, Map<Integer, UUID> auctionMap) {
        openGUIs.put(player.getUniqueId(), new GUIInfo(inventory, page, guiType, auctionMap));
    }

    public GUIInfo getPlayerGUIInfo(Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    public void closeGUI(Player player) {
        openGUIs.remove(player.getUniqueId());
    }

    public static class GUIInfo {
        private final Inventory inventory;
        private final int page;
        private final String guiType;
        private final Map<Integer, UUID> auctionMap;

        public GUIInfo(Inventory inventory, int page, String guiType, Map<Integer, UUID> auctionMap) {
            this.inventory = inventory;
            this.page = page;
            this.guiType = guiType;
            this.auctionMap = auctionMap;
        }

        public Inventory getInventory() { return inventory; }
        public int getPage() { return page; }
        public String getGuiType() { return guiType; }
        public Map<Integer, UUID> getAuctionMap() { return auctionMap; }
    }
}

