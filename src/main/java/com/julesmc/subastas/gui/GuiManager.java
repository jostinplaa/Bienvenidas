package com.julesmc.subastas.gui;

import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.managers.EconomyManager;
import com.julesmc.subastas.managers.LocaleManager;
import com.julesmc.subastas.objects.AuctionItem;
import com.julesmc.subastas.utils.SerializationUtil;
import com.julesmc.subastas.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GuiManager implements InventoryHolder {

    private final SubastasPlugin plugin;
    private final DatabaseManager databaseManager;
    private final LocaleManager localeManager;
    private final EconomyManager economyManager;

    // Player UUID -> Current Page
    private final Map<UUID, Integer> openGUIPages = new HashMap<>();
    // Player UUID -> GUI Inventory object (for direct manipulation or reference)
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    // Player UUID -> Map<Slot, AuctionID>
    private final Map<UUID, Map<Integer, Integer>> playerAuctionSlots = new HashMap<>();
    public static final String AUCTION_GUI_TITLE_PREFIX = "Subastas Activas - Pág ";

    public GuiManager(SubastasPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.localeManager = plugin.getLocaleManager();
        this.economyManager = plugin.getEconomyManager();
    }

    public void openActiveAuctionsGUI(Player player, int page) {
        List<AuctionItem> activeAuctions = databaseManager.getActiveAuctions();
        if (activeAuctions.isEmpty() && page <= 1) {
            player.sendMessage(localeManager.getMessage("auction.gui.no-auctions"));
            return;
        }

        int itemsPerPage = 45; // 5 rows for items (9*5)
        int totalPages = (int) Math.ceil((double) activeAuctions.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1; // Always at least one page, even if empty after filtering

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // Create GUI
        String guiTitle = AUCTION_GUI_TITLE_PREFIX + page;
        Inventory gui = Bukkit.createInventory(this, 54, guiTitle); // 6 rows

        Map<Integer, Integer> playerSlotMap = new HashMap<>(); // To store slot -> auctionId for this specific GUI instance

        // Populate items
        int startIndex = (page - 1) * itemsPerPage;
        for (int i = 0; i < itemsPerPage; i++) {
            int auctionIndex = startIndex + i;
            if (auctionIndex < activeAuctions.size()) {
                AuctionItem auction = activeAuctions.get(auctionIndex);
                ItemStack displayItem;
                try {
                    displayItem = SerializationUtil.itemStackFromBase64(auction.getItemSerialized());
                    if (displayItem == null) { // Should not happen if data is consistent
                        displayItem = new ItemStack(Material.BARRIER);
                        ItemMeta meta = displayItem.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.RED + "Error al cargar item");
                            displayItem.setItemMeta(meta);
                        }
                    }
                } catch (IllegalStateException e) {
                    plugin.getLogger().warning("Error deserializing item for GUI: " + auction.getItemName() + " (ID: " + auction.getId() + "): " + e.getMessage());
                    displayItem = new ItemStack(Material.BARRIER);
                    ItemMeta meta = displayItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.RED + "Error de Deserialización");
                        displayItem.setItemMeta(meta);
                    }
                }

                ItemMeta meta = displayItem.getItemMeta();
                if (meta == null) { // Should only happen if item is barrier or similar, ensure it has meta
                     meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
                }

                List<String> lore = new ArrayList<>();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.item-name-prefix", "&b") + auction.getItemName()));

                lore.add(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.seller", "&7Vendedor: &e{seller}", "seller", auction.getSellerName())));

                String priceLabel;
                double priceValue;
                if (auction.getCurrentBid() != null && auction.getCurrentBid() > 0) {
                    priceLabel = localeManager.getRawMessage("auction.gui.current-bid-label", "&7Puja Actual: &a");
                    priceValue = auction.getCurrentBid();
                    lore.add(ChatColor.translateAlternateColorCodes('&', priceLabel + String.format("%,.2f", priceValue)));
                    if (auction.getHighestBidderName() != null && !auction.getHighestBidderName().isEmpty()) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.current-bidder", "&7Pujador Actual: &d{name}", "name", auction.getHighestBidderName())));
                    }
                } else {
                    priceLabel = localeManager.getRawMessage("auction.gui.initial-price-label", "&7Precio Inicial: &a");
                    priceValue = auction.getInitialPrice();
                    lore.add(ChatColor.translateAlternateColorCodes('&', priceLabel + String.format("%,.2f", priceValue)));
                }

                if (auction.getBuyNowPrice() != null && auction.getBuyNowPrice() > 0) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.buy-now-price", "&7Compra Directa: &6{price}", "price", String.format("%,.2f", auction.getBuyNowPrice()))));
                }

                long timeLeftMillis = auction.getEndTime() - System.currentTimeMillis();
                lore.add(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.time-left", "&7Tiempo Restante: &c{time}", "time", TimeUtil.formatDuration(timeLeftMillis))));

                lore.add(" "); // Spacer
                lore.add(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.bid-instruction", "&eClick Izquierdo: Pujar")));
                if (auction.getBuyNowPrice() != null && auction.getBuyNowPrice() > 0) {
                     lore.add(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.buy-instruction", "&6Click Derecho: Comprar")));
                }
                // Store auction ID in lore for click identification (less visible way)
                // lore.add(ChatColor.BLACK + "ID:" + auction.getId());
                // A more robust way would be a map or NBT on the item if complex interactions are needed,
                // but for now, slot based identification or simple lore tag is okay.
                // For simplicity, rely on slot for now for click identification, and store in map
                // Store auction ID in a visible/invisible part of lore or use a map
                // lore.add(ChatColor.BLACK + "" + ChatColor.STRIKETHROUGH + "ID:" + auction.getId());
                playerSlotMap.put(i, auction.getId());


                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                gui.setItem(i, displayItem);
            }
        }
        playerAuctionSlots.put(player.getUniqueId(), playerSlotMap);

        // Pagination controls (last row)
        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(ChatColor.GREEN + localeManager.getRawMessage("auction.gui.previous-page", "Página Anterior"));
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage); // Bottom-left
        }

        ItemStack refresh = new ItemStack(Material.SUNFLOWER); // Changed from LIME_DYE to something more distinct
        ItemMeta refreshMeta = refresh.getItemMeta();
        refreshMeta.setDisplayName(ChatColor.YELLOW + localeManager.getRawMessage("auction.gui.refresh", "Actualizar"));
        refresh.setItemMeta(refreshMeta);
        gui.setItem(49, refresh); // Bottom-center

        if (page < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(ChatColor.GREEN + localeManager.getRawMessage("auction.gui.next-page", "Página Siguiente"));
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage); // Bottom-right
        }

        openGUIPages.put(player.getUniqueId(), page);
        openInventories.put(player.getUniqueId(), gui); // Store the inventory instance
        player.openInventory(gui);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        // This method is required by InventoryHolder.
        // Returning a new dummy inventory each time is fine as this manager instance is the holder.
        return Bukkit.createInventory(this, 9, "AuctionGUIManagerHolder");
    }

    public Inventory getOpenInventory(Player player) {
        return openInventories.get(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        openGUIPages.remove(player.getUniqueId());
        openInventories.remove(player.getUniqueId());
        playerAuctionSlots.remove(player.getUniqueId());
    }

    public Integer getAuctionIdForSlot(UUID playerUuid, int slot) {
        Map<Integer, Integer> slotMap = playerAuctionSlots.get(playerUuid);
        if (slotMap != null) {
            return slotMap.get(slot);
        }
        return null;
    }

    public boolean isPlayerViewingAuctionGUI(Player player) {
        return openGUIPages.containsKey(player.getUniqueId());
    }

    public int getPlayerCurrentPage(Player player) {
        return openGUIPages.getOrDefault(player.getUniqueId(), 1);
    }
}
