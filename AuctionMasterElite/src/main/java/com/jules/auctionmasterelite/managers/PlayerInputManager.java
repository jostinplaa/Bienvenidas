package com.jules.auctionmasterelite.managers;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Consumer;

public class PlayerInputManager {

    private final Map<UUID, Consumer<String>> playerInputMap = new ConcurrentHashMap<>();

    public void requestInput(UUID playerId, Consumer<String> callback) {
        playerInputMap.put(playerId, callback);
    }

    public Consumer<String> getCallback(UUID playerId) {
        return playerInputMap.get(playerId);
    }

    public void removePlayerInput(UUID playerId) {
        playerInputMap.remove(playerId);
    }

    public boolean isWaitingForInput(UUID playerId) {
        return playerInputMap.containsKey(playerId);
    }
}
