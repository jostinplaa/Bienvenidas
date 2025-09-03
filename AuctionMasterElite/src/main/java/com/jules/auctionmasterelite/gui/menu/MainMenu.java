package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.gui.menu.util.SortMode;
import com.jules.auctionmasterelite.util.LoreUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MainMenu extends GUI {

    public MainMenu(AuctionMasterElite plugin) {
        super(plugin, 45, "§8AuctionMaster Elite");
        initializeItems();
    }

    private void initializeItems() {
        inventory.setItem(11, createGuiItem(Material.EMERALD, "§aSubastas Activas", LoreUtil.getLore("main-menu.active-auctions")));
        inventory.setItem(13, createGuiItem(Material.GOLD_INGOT, "§eMis Subastas", LoreUtil.getLore("main-menu.my-auctions")));
        inventory.setItem(15, createGuiItem(Material.BOOK, "§9Historial", LoreUtil.getLore("main-menu.history")));
        inventory.setItem(30, createGuiItem(Material.ANVIL, "§6Crear Subasta", LoreUtil.getLore("main-menu.create-auction")));
        inventory.setItem(32, createGuiItem(Material.REDSTONE, "§cConfiguración", LoreUtil.getLore("main-menu.settings")));
    }

    protected ItemStack createGuiItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }


    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Handle clicks
        switch (clickedItem.getType()) {
            case EMERALD:
                player.openInventory(new com.jules.auctionmasterelite.gui.menu.ActiveAuctionsMenu(plugin, player, 0, SortMode.ENDING_SOONEST).getInventory());
                break;
            case GOLD_INGOT:
                player.openInventory(new com.jules.auctionmasterelite.gui.menu.MyAuctionsMenu(plugin, player, 0).getInventory());
                break;
            case BOOK:
                player.openInventory(new com.jules.auctionmasterelite.gui.menu.HistoryMenu(plugin, player, 0).getInventory());
                break;
            case ANVIL:
                if (player.hasPermission("auctionmaster.elite.create.public")) {
                    player.openInventory(new com.jules.auctionmasterelite.gui.creation.CreateAuctionMenu(plugin, player).getInventory());
                } else {
                    player.sendMessage("§cNo tienes permiso para crear subastas.");
                    player.closeInventory();
                }
                break;
            case REDSTONE:
                player.sendMessage("Opening Settings... (Not Implemented)");
                player.closeInventory();
                break;
            default:
                break;
        }
    }
}
