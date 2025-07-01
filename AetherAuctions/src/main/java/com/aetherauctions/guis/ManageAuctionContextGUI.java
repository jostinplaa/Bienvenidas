package com.aetherauctions.guis;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.util.ItemUtil;
import com.aetherauctions.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ManageAuctionContextGUI {

    public static final int GUI_SIZE = 27; // 3x3
    public static final int ITEM_DISPLAY_SLOT = 13; // Center slot
    public static final int CANCEL_AUCTION_SLOT = 11; // Left of center
    public static final int VIEW_DETAILS_SLOT = 15;   // Right of center
    public static final int BACK_BUTTON_SLOT = 22;    // Bottom center

    public static void open(Player player, Auction auctionToManage) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();

        String auctionIdStr = auctionToManage.getId().toString();
        String title = msgManager.getRawMessage("manage_auction_gui_title", "%id%", auctionIdStr.substring(0, Math.min(auctionIdStr.length(), 8)));
        Inventory gui = Bukkit.createInventory(player, GUI_SIZE, title);

        // Ítem de la subasta en el centro
        ItemStack displayAuctionItem = auctionToManage.getItemStack().clone();
        // Podríamos añadir un lore simple si es necesario, pero el ítem en sí ya es visual
        gui.setItem(ITEM_DISPLAY_SLOT, displayAuctionItem);

        // Botón Cancelar Subasta
        ItemStack cancelButton = ItemUtil.createItemStack(Material.RED_WOOL,
            msgManager.getRawMessage("manage_auction_gui_button_cancel_name"),
            msgManager.getStringList("manage_auction_gui_button_cancel_lore")
        );
        gui.setItem(CANCEL_AUCTION_SLOT, cancelButton);

        // Botón Ver Detalles
        ItemStack detailsButton = ItemUtil.createItemStack(Material.BOOK,
            msgManager.getRawMessage("manage_auction_gui_button_details_name"),
            msgManager.getStringList("manage_auction_gui_button_details_lore")
        );
        gui.setItem(VIEW_DETAILS_SLOT, detailsButton);

        // Botón Volver (a MyActiveAuctionsGUI)
        ItemStack backButton = ItemUtil.createItemStack(Material.ARROW,
            msgManager.getRawMessage("manage_auction_gui_button_back_name"),
            msgManager.getStringList("manage_auction_gui_button_back_lore")
        );
        gui.setItem(BACK_BUTTON_SLOT, backButton);

        // Relleno decorativo
        ItemStack filler = ItemUtil.createItemStack(plugin.getConfigManager().getMainDecorativePaneMaterial(), " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler.clone());
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f); // Sonido al abrir
        player.openInventory(gui);
    }
}
