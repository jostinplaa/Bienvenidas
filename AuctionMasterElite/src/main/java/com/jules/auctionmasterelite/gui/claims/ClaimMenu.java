package com.jules.auctionmasterelite.gui.claims;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.util.LoreUtil;
import com.jules.auctionmasterelite.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.managers.DatabaseManager.MoneyClaim;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaimMenu extends GUI {

    private final Map<Integer, Integer> slotToItemClaimId = new HashMap<>();
    private final Map<Integer, Integer> slotToMoneyClaimId = new HashMap<>();

    public ClaimMenu(AuctionMasterElite plugin, Player player) {
        super(plugin, 54, "§8Reclama tus Objetos y Dinero");
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        Map<Integer, ItemStack> itemClaims = plugin.getDatabaseManager().getPlayerClaims(player.getUniqueId());
        List<MoneyClaim> moneyClaims = plugin.getDatabaseManager().getPlayerMoneyClaims(player.getUniqueId());

        if (itemClaims.isEmpty() && moneyClaims.isEmpty()) {
            MessageUtil.sendMessage(player, "no-items-to-claim");
            // Don't open an empty inventory
            return;
        }

        // Display Item Claims
        int itemSlot = 0;
        for (Map.Entry<Integer, ItemStack> entry : itemClaims.entrySet()) {
            if (itemSlot >= 45) break; // Ensure we don't overwrite money claims area
            int claimId = entry.getKey();
            ItemStack item = entry.getValue().clone();

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setLore(LoreUtil.getLore("claim-menu.claim-item"));
                item.setItemMeta(meta);
            }

            inventory.setItem(itemSlot, item);
            slotToItemClaimId.put(itemSlot, claimId);
            itemSlot++;
        }

        // Display Money Claims
        int moneySlot = 53; // Start from the end of the inventory
        for (MoneyClaim claim : moneyClaims) {
             if (moneySlot < 45) break; // Ensure we don't overwrite item claims area
            ItemStack moneyItem = new ItemStack(Material.GOLD_NUGGET);
            ItemMeta meta = moneyItem.getItemMeta();
            meta.setDisplayName("§aReclamar §6" + String.format("%,.2f", claim.amount()));
            List<String> lore = new ArrayList<>();
            lore.add("§7Motivo: " + claim.reason());
            lore.addAll(LoreUtil.getLore("claim-menu.claim-money"));
            meta.setLore(lore);
            moneyItem.setItemMeta(meta);

            inventory.setItem(moneySlot, moneyItem);
            slotToMoneyClaimId.put(moneySlot, claim.claimId());
            moneySlot--;
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int clickedSlot = event.getSlot();

        // Handle Item Claim
        if (slotToItemClaimId.containsKey(clickedSlot)) {
            handleItemClaim(player, clickedSlot, clickedItem);
        }
        // Handle Money Claim
        else if (slotToMoneyClaimId.containsKey(clickedSlot)) {
            handleMoneyClaim(player, clickedSlot);
        }
    }

    private void handleItemClaim(Player player, int slot, ItemStack item) {
        if (player.getInventory().firstEmpty() == -1) {
            MessageUtil.sendMessage(player, "inventory-full-cannot-claim");
            return;
        }

        int claimId = slotToItemClaimId.get(slot);
        ItemStack cleanItem = item.clone();
        ItemMeta meta = cleanItem.getItemMeta();
        if (meta != null) {
            meta.setLore(null);
            cleanItem.setItemMeta(meta);
        }

        player.getInventory().addItem(cleanItem);
        plugin.getDatabaseManager().deleteClaim(claimId);
        MessageUtil.sendMessage(player, "item-claimed-successfully");

        // Refresh the menu
        new ClaimMenu(plugin, player).open(player);
    }

    private void handleMoneyClaim(Player player, int slot) {
        int claimId = slotToMoneyClaimId.get(slot);

        // This is a bit tricky since the amount is in the lore.
        // It's better to refetch the claim from the DB to be safe.
        MoneyClaim claim = plugin.getDatabaseManager().getPlayerMoneyClaims(player.getUniqueId())
                .stream().filter(c -> c.claimId() == claimId).findFirst().orElse(null);

        if (claim == null) {
            MessageUtil.sendRawMessage(player, "&cError: No se pudo encontrar este reclamo de dinero.");
            return;
        }

        plugin.getEconomyManager().deposit(player, claim.amount());
        plugin.getDatabaseManager().deleteMoneyClaim(claimId);
        MessageUtil.sendMessage(player, "money-claimed-successfully", "amount", String.format("%,.2f", claim.amount()));

        // Refresh the menu
        new ClaimMenu(plugin, player).open(player);
    }
}
