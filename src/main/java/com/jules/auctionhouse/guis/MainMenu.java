package com.jules.auctionhouse.guis;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

public class MainMenu {

    private final Inventory inventory;

    public MainMenu() {
        inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Casa de Subastas");
        initializeItems();
    }

    private void initializeItems() {
        // Fila 1 (Decoración + Título)
        ItemStack greenGlass = createGuiItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                inventory.setItem(i, greenGlass);
            }
        }
        inventory.setItem(4, createGuiItem(Material.NETHER_STAR, ChatColor.GOLD + "" + ChatColor.BOLD + "Casa de Subastas"));

        // Fila 2-4 (Navegación)
        inventory.setItem(10, createGuiItem(Material.CHEST, ChatColor.GREEN + "Subastas Activas", "§fClick para ver todas las subastas en curso"));
        inventory.setItem(12, createGuiItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Mis Subastas", "§fSubastas que has creado"));
        inventory.setItem(14, createGuiItem(Material.BOOK, ChatColor.WHITE + "Historial", "§fVer tu historial de compras y ventas"));
        inventory.setItem(16, createGuiItem(Material.COMPARATOR, ChatColor.RED + "Configuración Personal", "§fNotificaciones, filtros, preferencias"));
        inventory.setItem(22, createGuiItem(Material.EMERALD, ChatColor.DARK_GREEN + "Crear Subasta", "§fSubasta el item que tienes en la mano"));
        inventory.setItem(31, createGuiItem(Material.DIAMOND, ChatColor.AQUA + "Top Vendedores", "§fRanking del mes"));
        inventory.setItem(40, createGuiItem(Material.PAPER, ChatColor.GRAY + "Ayuda / Tutorial"));

        // Fila 5-6 (Decoración + Cerrar)
        ItemStack redGlass = createGuiItem(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            if (i != 49) {
                inventory.setItem(i, redGlass);
            }
        }
        inventory.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Cerrar"));
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}