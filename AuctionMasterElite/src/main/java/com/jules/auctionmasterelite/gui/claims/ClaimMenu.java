package com.jules.auctionmasterelite.gui.claims;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClaimMenu {

    private final AuctionMasterElite plugin;
    private final Player player;
    private final Map<Integer, Integer> slotToClaimIdMap = new HashMap<>(); // Map slot -> claim_id

    public ClaimMenu(AuctionMasterElite plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Map<Integer, ItemStack> claims = plugin.getDatabaseManager().getPlayerClaims(player.getUniqueId());

        if (claims.isEmpty()) {
            MessageUtil.sendMessage(player, "no-items-to-claim");
            return;
        }

        int inventorySize = (int) Math.ceil(claims.size() / 9.0) * 9;
        inventorySize = Math.max(9, Math.min(54, inventorySize));
        Inventory inv = Bukkit.createInventory(null, inventorySize, "Claim Your Items");

        int slot = 0;
        for (Map.Entry<Integer, ItemStack> entry : claims.entrySet()) {
            int claimId = entry.getKey();
            ItemStack item = entry.getValue();

            // Add a lore to the item to distinguish it in the GUI, if you want
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setLore(Collections.singletonList("Click to claim this item!"));
                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);
            slotToClaimIdMap.put(slot, claimId);
            slot++;
        }

        player.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int clickedSlot = event.getSlot();
        if (!slotToClaimIdMap.containsKey(clickedSlot)) {
            return; // Clicked on something that isn't a claim
        }

        if (player.getInventory().firstEmpty() == -1) {
            MessageUtil.sendMessage(player, "inventory-full-cannot-claim");
            player.closeInventory();
            return;
        }

        int claimId = slotToClaimIdMap.get(clickedSlot);

        // Remove lore before giving item
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            meta.setLore(null); // Or restore original lore if you stored it
            clickedItem.setItemMeta(meta);
        }

        // Give item and update database
        player.getInventory().addItem(clickedItem);
        plugin.getDatabaseManager().deleteClaim(claimId);

        MessageUtil.sendMessage(player, "item-claimed-successfully");

        // Refresh or close the inventory
        player.closeInventory();
        open(); // Re-open to show updated claims
    }

    public Map<Integer, Integer> getSlotToClaimIdMap() {
        return this.slotToClaimIdMap;
    }
}
