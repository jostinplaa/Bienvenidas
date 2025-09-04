package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.PlayerPreferences;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the preferences for each player, caching them to avoid frequent database calls.
 */
public class PlayerSettingsManager {

    private final AuctionMasterElite plugin;
    private final Map<UUID, PlayerPreferences> preferencesCache = new ConcurrentHashMap<>();

    public PlayerSettingsManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads a player's preferences from the database into the cache.
     * Should be called on player join.
     * @param playerId The player's UUID.
     */
    public void loadPlayer(UUID playerId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerPreferences prefs = plugin.getDatabaseManager().loadPlayerPreferences(playerId);
            preferencesCache.put(playerId, prefs);
        });
    }

    /**
     * Unloads a player's preferences from the cache.
     * Should be called on player quit to prevent memory leaks.
     * @param playerId The player's UUID.
     */
    public void unloadPlayer(UUID playerId) {
        // Before unloading, save any changes.
        if (preferencesCache.containsKey(playerId)) {
            savePlayerPreferences(preferencesCache.get(playerId));
            preferencesCache.remove(playerId);
        }
    }

    /**
     * Gets a player's preferences from the cache.
     * This returns the cached object, so modifications will be saved on quit.
     * @param playerId The player's UUID.
     * @return The PlayerPreferences object, or a default if not loaded.
     */
    public PlayerPreferences getPreferences(UUID playerId) {
        return preferencesCache.getOrDefault(playerId, new PlayerPreferences(playerId));
    }

    /**
     * Saves a player's preferences to the database.
     * This can be called when a setting is changed for immediate persistence.
     * @param prefs The preferences object to save.
     */
    public void savePlayerPreferences(PlayerPreferences prefs) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().savePlayerPreferences(prefs);
        });
    }
}
