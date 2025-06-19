package com.aetherauctions.gui.rework;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder; // Import InventoryHolder
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap; // Import HashMap
import java.util.List;
import java.util.Map; // Import Map
import java.util.stream.Collectors;

public class NewMainAuctionGUI implements InventoryHolder { // Implement InventoryHolder

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Player player;
    private int currentPage;
    private int totalPages; // Added totalPages field
    private Inventory inventory;
    private final Map<Integer, AuctionItem> slotToAuctionItemMap = new HashMap<>(); // Added slotToAuctionItemMap

    public NewMainAuctionGUI(AetherAuctions plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.currentPage = page;
        this.auctionManager = plugin.getAuctionManager();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        buildGUI();
    }

    private void buildGUI() {
        List<AuctionItem> activeAuctions = auctionManager.getActiveAuctionsMap().values().stream()
                .filter(auction -> auction.getStatus() == AuctionStatus.ACTIVE)
                .sorted(Comparator.comparingLong(auction -> auction.getStartTime() + auction.getDuration())) // Fixed: Use sum of startTime and duration
                .collect(Collectors.toList());

        this.totalPages = Math.max(1, (int) Math.ceil((double) activeAuctions.size() / 36.0)); // Initialize totalPages
        this.currentPage = Math.max(0, Math.min(this.currentPage, this.totalPages - 1));

        String title = messageManager.getMessage("new_main_gui_title_prefix", "&1Subastas Activas") +
                       " &7(Pág. " + (this.currentPage + 1) + "/" + this.totalPages + ")";

        inventory = Bukkit.createInventory(this, 54, title); // Use 'this' as InventoryHolder
        slotToAuctionItemMap.clear(); // Clear map before rebuilding

        // Fill with auction items
        int startIndex = this.currentPage * 36;
        int endIndex = Math.min(startIndex + 36, activeAuctions.size());
        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem auction = activeAuctions.get(i);
            ItemStack itemStack = auction.getItemStack().clone();
            ItemMeta itemMeta = itemStack.getItemMeta();

            if (itemMeta != null) {
                // Original item name as fallback if message key is not found or if desired
                String itemName = itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : auction.getItemStack().getType().name();
                itemMeta.setDisplayName(messageManager.getMessage("new_gui_main_item_lore_name", "&f%item_name%").replace("%item_name%", itemName));

                List<String> lore = new ArrayList<>();
                lore.add(messageManager.getMessage("new_gui_main_item_lore_seller", "&7Vendedor: &6%seller%")
                        .replace("%seller%", auction.getSellerName()));
                lore.add(messageManager.getMessage("new_gui_main_item_lore_current_bid", "&7Puja Actual: &e%price% %currency%")
                        .replace("%price%", String.format("%,.2f", auction.getCurrentBid()))
                        .replace("%currency%", configManager.getCurrencySymbol()));

                if (auction.getBuyNowPrice() > 0 && configManager.isBuyNowAllowed()) {
                    lore.add(messageManager.getMessage("new_gui_main_item_lore_buy_now", "&7Compra Directa: &a%price% %currency%")
                            .replace("%price%", String.format("%,.2f", auction.getBuyNowPrice()))
                            .replace("%currency%", configManager.getCurrencySymbol()));
                }

                lore.add(messageManager.getMessage("new_gui_main_item_lore_time_remaining", "&7Tiempo: &c%time%")
                        .replace("%time%", InventoryUtil.formatTime((auction.getStartTime() + auction.getDuration()) - System.currentTimeMillis()))); // Fixed: Calculate remaining time
                lore.add(messageManager.getMessage("new_gui_main_item_lore_id", "&8ID: #%id%")
                        .replace("%id%", String.valueOf(auction.getId())));
                lore.add(" "); // Spacer
                lore.add(messageManager.getMessage("new_gui_main_item_lore_instruction_details", "&bClic Derecho: &fVer Detalles"));

                itemMeta.setLore(lore);
                itemStack.setItemMeta(itemMeta);
            }
            int inventorySlot = i - startIndex;
            inventory.setItem(inventorySlot, itemStack); // Place in slots 0-35
            slotToAuctionItemMap.put(inventorySlot, auction); // Map slot to AuctionItem
        }

        // Add Decorative Panes
        Material paneMaterial = Material.getMaterial(plugin.getConfig().getString("gui.decorative_pane_material", "GRAY_STAINED_GLASS_PANE"));
        if (paneMaterial == null) {
            paneMaterial = Material.GRAY_STAINED_GLASS_PANE; // Fallback
        }
        ItemStack decorativePane = InventoryUtil.createGuiItem(paneMaterial,
                                                               messageManager.getMessage("new_gui_decorative_pane_name", "&r "));

        // Fill empty auction item slots in the 0-35 range if fewer than 36 items on the page
        int itemsOnPage = endIndex - startIndex;
        for (int i = itemsOnPage; i < 36; i++) {
            inventory.setItem(i, decorativePane);
        }

        for (int i = 45; i < 54; i++) { // Fill button row background (slots 45-53)
             inventory.setItem(i, decorativePane);
        }


        // Add Navigation Buttons
        inventory.setItem(49, InventoryUtil.createGuiItem(Material.BARRIER, messageManager.getMessage("button_close_gui", "&cCerrar")));

        if (this.currentPage > 0) {
            inventory.setItem(48, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_previous_page", "&aPágina Anterior")));
        } else {
            inventory.setItem(48, decorativePane);
        }

        if (this.currentPage < totalPages - 1) {
            inventory.setItem(50, InventoryUtil.createGuiItem(Material.ARROW, messageManager.getMessage("button_next_page", "&aPágina Siguiente")));
        } else {
            inventory.setItem(50, decorativePane);
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public Player getPlayer() {
        return player;
    }

    public AuctionItem getAuctionItemAtSlot(int slot) { // Added getter for slotToAuctionItemMap
        return slotToAuctionItemMap.get(slot);
    }

    public int getTotalPages() { // Added getter for totalPages
        return totalPages;
    }
}
