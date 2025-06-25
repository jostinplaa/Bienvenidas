package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionStatus; // Assuming this is the correct import
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil; // Assuming this is the correct import
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AuctionDetailsGUI {

    // Slot constants
    private static final int ITEM_DISPLAY_SLOT = 13; // Center slot for 6 rows (index 13 for 3rd row center in 9-col)
                                                    // For 6 rows (54 slots), middle would be around 22, 31. Let's use 22.
                                                    // The prompt example uses 13 for a 54-slot inventory, which is unusual.
                                                    // Let's adjust to a more central slot for 6 rows (e.g. 22 or 31).
                                                    // Based on common 6-row layouts, 4th slot of 3rd row (22) or 4th of 4th row (31)
                                                    // For this implementation, I'll use 22 as a common central point.
                                                    // The prompt later indicates slot 13 for item, 30, 32, 49 for buttons, implying 6 rows. Slot 13 is fine.

    private static final int ACTUAL_ITEM_DISPLAY_SLOT = 13; // As per prompt example's final code block
    private static final int BID_BUTTON_SLOT = 30;
    private static final int BUY_NOW_BUTTON_SLOT = 32;
    private static final int BACK_BUTTON_SLOT = 49;

    public static void open(Player player, AuctionItem auction, int returnPage) { // returnPage currently unused by this static version
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager messageManager = plugin.getMessageManager();
        ConfigManager configManager = plugin.getConfigManager();
        // InventoryUtil inventoryUtil = plugin.getInventoryUtil(); // Direct static calls will be used

        if (auction == null) {
            plugin.getLogger().severe("[AuctionDetailsGUI] Se intentó abrir con una subasta null para el jugador: " + player.getName());
            player.sendMessage(ChatColor.RED + "Error: No se pudo cargar la información de la subasta seleccionada.");
            return;
        }

        String guiTitle = messageManager.getMessage("auction_details_gui_title"); // Default removed as MessageManager handles it
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle); // 54 slots (6 rows)

        // Ítem subastado
        ItemStack itemToDisplay = auction.getItemStack().clone();
        ItemMeta meta = itemToDisplay.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(itemToDisplay.getType());
        }

        String originalItemName = itemToDisplay.hasItemMeta() && itemToDisplay.getItemMeta().hasDisplayName()
                                ? itemToDisplay.getItemMeta().getDisplayName()
                                : itemToDisplay.getType().name().replace("_", " ");
        meta.setDisplayName(messageManager.getMessage("item_default_name_format", "%item_name%", originalItemName));

        List<String> loreLinesTemplate = messageManager.getRawStringList("auction_lore");
        List<String> processedLore = new ArrayList<>();
        String buyNowPriceString = auction.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed()
                                   ? String.format("%.2f %s", auction.getBuyNowPrice(), configManager.getCurrencySymbol())
                                   : messageManager.getMessage("auction_lore_buy_now_not_available");

        for (String loreLine : loreLinesTemplate) {
            processedLore.add(ChatColor.translateAlternateColorCodes('&', loreLine
                    .replace("%id%", String.valueOf(auction.getId()))
                    .replace("%seller%", auction.getSellerName())
                    .replace("%price%", String.format("%.2f %s", auction.getCurrentBid(), configManager.getCurrencySymbol()))
                    .replace("%buy_now%", buyNowPriceString)
                        .replace("%time%", InventoryUtil.formatTime(auction.getStartTime() + auction.getDuration() - System.currentTimeMillis())) // Used static call
            ));
        }
        meta.setLore(processedLore);
        itemToDisplay.setItemMeta(meta);
        gui.setItem(ACTUAL_ITEM_DISPLAY_SLOT, itemToDisplay);

        // Botón PUJAR
        ItemStack bidButton = new ItemStack(Material.EMERALD);
        ItemMeta bidMeta = bidButton.getItemMeta();
        bidMeta.setDisplayName(messageManager.getMessage("button_bid"));
        bidButton.setItemMeta(bidMeta);
        gui.setItem(BID_BUTTON_SLOT, bidButton);

        // Botón COMPRAR AHORA
        if (auction.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed() && auction.getStatus() == AuctionStatus.ACTIVE) {
            ItemStack buyNowButton = new ItemStack(Material.GOLD_INGOT);
            ItemMeta buyMeta = buyNowButton.getItemMeta();
            buyMeta.setDisplayName(messageManager.getMessage("button_buy_now"));
            buyNowButton.setItemMeta(buyMeta);
            gui.setItem(BUY_NOW_BUTTON_SLOT, buyNowButton);
        }

        // Botón VOLVER ATRÁS
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(messageManager.getMessage("button_back"));
        backButton.setItemMeta(backMeta);
        gui.setItem(BACK_BUTTON_SLOT, backButton);

        // Rellenar slots vacíos
        Material decorativeMaterial = configManager.getDetailsDecorativePaneMaterial();
        ItemStack decorativePane = inventoryUtil.createGuiItem(
            decorativeMaterial,
            messageManager.getMessage("new_gui_decorative_pane_name")
        );
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, decorativePane.clone()); // Use clone if createGuiItem returns a shared instance
            }
        }

        player.openInventory(gui);
        plugin.getLogger().info("[DEBUG] Se abrió AuctionDetailsGUI para " + player.getName() + " subasta ID: " + auction.getId());
    }
}
