package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.PlayerPreferences;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.util.LoreUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SettingsMenu extends GUI {

    public SettingsMenu(AuctionMasterElite plugin, Player player) {
        super(plugin, 27, "§8Configuración Personal");
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        PlayerPreferences prefs = plugin.getPlayerSettingsManager().getPreferences(player.getUniqueId());

        // Outbid Notification Toggle
        boolean outbidEnabled = prefs.hasOutbidNotification();
        ItemStack outbidItem = createToggleItem(
            outbidEnabled,
            "§bNotificación de Sobrepuja",
            LoreUtil.getLore("settings-menu.outbid-notification-lore"),
            "§aActivado",
            "§cDesactivado"
        );
        inventory.setItem(11, outbidItem);

        // Bid on Own Auction Notification Toggle
        boolean bidEnabled = prefs.hasBidNotification();
        ItemStack bidItem = createToggleItem(
            bidEnabled,
            "§bNotificación de Puja",
            LoreUtil.getLore("settings-menu.bid-notification-lore"),
            "§aActivado",
            "§cDesactivado"
        );
        inventory.setItem(13, bidItem);

        inventory.setItem(18, createGuiItem(Material.RED_WOOL, "§cVolver al Menú", LoreUtil.getLore("active-auctions-menu.back-button")));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        PlayerPreferences prefs = plugin.getPlayerSettingsManager().getPreferences(player.getUniqueId());

        switch(event.getSlot()) {
            case 11: // Outbid Notification
                prefs.setOutbidNotification(!prefs.hasOutbidNotification());
                break;
            case 13: // Bid Notification
                prefs.setBidNotification(!prefs.hasBidNotification());
                break;
            case 18: // Back button
                new MainMenu(plugin).open(player);
                return; // Don't save or refresh
            default:
                return; // Clicked on empty space
        }

        // Save and refresh
        plugin.getPlayerSettingsManager().savePlayerPreferences(prefs);
        new SettingsMenu(plugin, player).open(player);
    }

    private ItemStack createToggleItem(boolean enabled, String name, List<String> lore, String enabledText, String disabledText) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> finalLore = new java.util.ArrayList<>(lore);
        finalLore.add("");
        finalLore.add(enabled ? enabledText : disabledText);
        finalLore.add(LoreUtil.getLore("settings-menu.click-to-toggle").get(0));

        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
