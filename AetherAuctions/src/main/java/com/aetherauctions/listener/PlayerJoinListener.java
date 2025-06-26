package com.aetherauctions.listener;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.managers.RewardManager; // Corrected import
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final AetherAuctions plugin;
    private final RewardManager rewardManager;

    public PlayerJoinListener(AetherAuctions plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay a bit to ensure player is fully loaded and Vault is ready
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) { // Check if player is still online after delay
                 rewardManager.processPendingRewards(player);
            }
        }, 60L); // 3 seconds delay (60 ticks)
    }
}
