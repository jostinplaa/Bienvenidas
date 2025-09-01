package com.jules.auctionmasterelite.gui.menu;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.gui.GUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class MainMenu extends GUI {

    public MainMenu(AuctionMasterElite plugin) {
        super(plugin, 45, "§8AuctionMaster Elite");
        initializeItems();
    }

    private void initializeItems() {
        // Placeholder items for the main menu buttons
        inventory.setItem(11, createGuiItem(Material.EMERALD, "§aSubastas Activas", "§7Ve las subastas que están", "§7actualmente en curso."));
        inventory.setItem(13, createGuiItem(Material.GOLD_INGOT, "§eMis Subastas", "§7Gestiona tus propias subastas", "§7y pujas."));
        inventory.setItem(15, createGuiItem(Material.BOOK, "§9Historial", "§7Revisa el historial de", "§7subastas finalizadas."));
        inventory.setItem(30, createGuiItem(Material.ANVIL, "§6Crear Subasta", "§7Crea una nueva subasta", "§7para vender un objeto."));
        inventory.setItem(32, createGuiItem(Material.REDSTONE, "§cConfiguración", "§7Ajusta tus preferencias", "§7personales."));
    }

    protected ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
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
                player.openInventory(new com.jules.auctionmasterelite.gui.menu.ActiveAuctionsMenu(plugin, player, 0).getInventory());
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
