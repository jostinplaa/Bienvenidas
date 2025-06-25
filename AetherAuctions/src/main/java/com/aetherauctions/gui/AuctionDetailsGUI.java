package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.model.Auction;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor; // For direct use if MessageManager doesn't handle a specific case

import java.util.ArrayList;
import java.util.List;

public class AuctionDetailsGUI {

    public static final int ITEM_DISPLAY_SLOT = 22; // Adjusted for a more central position in 6 rows (54 slots)
    public static final int BID_BUTTON_SLOT = 38;
    public static final int BUY_NOW_BUTTON_SLOT = 40;
    public static final int BACK_BUTTON_SLOT = 49;

    public static void open(Player player, Auction auction, int returnPage) { // returnPage currently unused by this static version
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();

        if (auction == null) {
            plugin.getLogger().severe("[AuctionDetailsGUI] Se intentó abrir con una subasta null para el jugador: " + player.getName());
            player.sendMessage(ChatColor.RED + "Error: No se pudo cargar la información de la subasta seleccionada."); // Fallback, GUIManager should handle main message
            return;
        }

        String guiTitle = msgManager.getMessage("auction_details_gui_title");
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle);

        // --- Ítem Subastado en el Centro ---
        ItemStack itemToDisplay = auction.getItemStack().clone();
        ItemMeta meta = itemToDisplay.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(itemToDisplay.getType());
        }

        String originalItemName = meta.hasDisplayName() ? meta.getDisplayName() : InventoryUtil.formatMaterialName(itemToDisplay.getType());
        meta.setDisplayName(msgManager.getMessage("item_default_name_format", "%item_name%", originalItemName));

        List<String> loreLinesRaw = msgManager.getStringList("auction_lore");
        List<String> processedLore = new ArrayList<>();
        String buyNowPriceString = auction.hasBuyNow() && cfgManager.isBuyNowAllowed()
                                   ? String.format("%.2f %s", auction.getBuyNowPrice(), cfgManager.getCurrencySymbol())
                                   : msgManager.getMessage("auction_lore_buy_now_not_available");

        for (String loreLine : loreLinesRaw) {
            processedLore.add(loreLine // MessageManager now handles color translation
                    .replace("%id%", auction.getId().toString().substring(0, 8))
                    .replace("%seller%", auction.getSellerName())
                    .replace("%price%", String.format("%.2f %s", auction.getCurrentBid(), cfgManager.getCurrencySymbol()))
                    .replace("%buy_now%", buyNowPriceString)
                    .replace("%time%", InventoryUtil.formatTime(auction.getRemainingTimeMillis()))
            );
        }
        meta.setLore(processedLore);
        itemToDisplay.setItemMeta(meta);
        gui.setItem(ITEM_DISPLAY_SLOT, itemToDisplay);

        // --- Botones ---
        ItemStack bidButton = InventoryUtil.createGuiItem(
            Material.EMERALD,
            msgManager.getMessage("button_bid")
        );
        gui.setItem(BID_BUTTON_SLOT, bidButton);

        if (auction.hasBuyNow() && cfgManager.isBuyNowAllowed() && auction.getStatus() == AuctionStatus.ACTIVE) {
            ItemStack buyNowButton = InventoryUtil.createGuiItem(
                Material.GOLD_INGOT,
                msgManager.getMessage("button_buy_now"),
                msgManager.getMessage("button_buy_now_lore_price", "%price%", String.format("%.2f %s", auction.getBuyNowPrice(), cfgManager.getCurrencySymbol()))
            );
            gui.setItem(BUY_NOW_BUTTON_SLOT, buyNowButton);
        }

        ItemStack backButton = InventoryUtil.createGuiItem(
            Material.BARRIER,
            msgManager.getMessage("button_back")
        );
        gui.setItem(BACK_BUTTON_SLOT, backButton);

        // --- Rellenar slots vacíos ---
        Material decoMat = cfgManager.getDetailsDecorativePaneMaterial();
        String decoName = msgManager.getMessage("main_gui_decorative_pane_name");
        ItemStack decorativePane = InventoryUtil.createGuiItem(decoMat, decoName);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, decorativePane.clone());
            }
        }

        player.openInventory(gui);
        plugin.getLogger().info("[DEBUG] Se abrió AuctionDetailsGUI para " + player.getName() + " subasta ID: " + auction.getId().toString().substring(0,8));
    }
}
