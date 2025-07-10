package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction;
import com.aetherauctions.util.ItemUtil;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
// import org.bukkit.Sound; // No se usa directamente
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ManageAuctionContextGUI {

    // Las constantes de slot y GUI_SIZE se leerán o derivarán de config.yml si es necesario
    // public static final int GUI_SIZE = 27; // Podría ser configurable si se desea
    // public static final int ITEM_DISPLAY_SLOT = 13;
    // public static final int CANCEL_AUCTION_SLOT = 11;
    // public static final int VIEW_DETAILS_SLOT = 15;
    // public static final int BACK_BUTTON_SLOT = 22;

    public static void open(Player player, Auction auctionToManage) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        String guiKey = "manage_auction_context_gui"; // Clave base para esta GUI en config
        int guiSize = 27; // Tamaño fijo por ahora, podría hacerse configurable

        String auctionIdStr = auctionToManage.getId().toString();
        String title = msgManager.getRawMessage("manage_auction_gui_title", "%id%", auctionIdStr.substring(0, Math.min(auctionIdStr.length(), 8)));
        Inventory gui = Bukkit.createInventory(player, guiSize, title);

        // Ítem de la subasta en el centro
        ItemStack displayAuctionItem = auctionToManage.isMystery() ?
            new ItemStack(cfgManager.getButtonMaterial(guiKey, "mystery_item_placeholder", "ENDER_CHEST")) : // Placeholder para misteriosa
            auctionToManage.getItemStack().clone();
        // Aquí se podría añadir un lore básico si displayAuctionItem es el placeholder
        gui.setItem(cfgManager.getButtonSlot(guiKey, "item_display_slot", 13), displayAuctionItem);


        // Botón Cancelar Subasta
        ItemStack cancelButton = ItemUtil.createItemStack(
            cfgManager.getButtonMaterial(guiKey, "cancel_this_auction", "RED_WOOL"),
            msgManager.getRawMessage("manage_auction_gui_button_cancel_name"),
            msgManager.getStringList("manage_auction_gui_button_cancel_lore")
        );
        gui.setItem(cfgManager.getButtonSlot(guiKey, "cancel_this_auction", 11), cancelButton);

        // Botón Ver Detalles
        ItemStack detailsButton = ItemUtil.createItemStack(
            cfgManager.getButtonMaterial(guiKey, "view_auction_details", "BOOK"),
            msgManager.getRawMessage("manage_auction_gui_button_details_name"),
            msgManager.getStringList("manage_auction_gui_button_details_lore")
        );
        gui.setItem(cfgManager.getButtonSlot(guiKey, "view_auction_details", 15), detailsButton);

        // Botón Volver (a MyActiveAuctionsGUI)
        ItemStack backButton = ItemUtil.createItemStack(
            cfgManager.getButtonMaterial(guiKey, "back_to_my_auctions", "ARROW"),
            msgManager.getRawMessage("manage_auction_gui_button_back_name"),
            msgManager.getStringList("manage_auction_gui_button_back_lore")
        );
        gui.setItem(cfgManager.getButtonSlot(guiKey, "back_to_my_auctions", 22), backButton);

        // Relleno decorativo
        ItemStack filler = ItemUtil.createItemStack(cfgManager.getDetailsDecorativePaneMaterial(), " "); // Usar details pane
        for (int i = 0; i < guiSize; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler.clone());
            }
        }
        // Sonido al abrir, gestionado por SoundManager si se llama desde CommandManager o InventoryClickListener
        // plugin.getSoundManager().playSound(player, "open_gui"); // Ejemplo si se quiere aquí
        player.openInventory(gui);
    }
}
