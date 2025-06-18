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
import org.bukkit.NamespacedKey; // Added import
import org.bukkit.persistence.PersistentDataType; // Added import
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
                // Item Name - Use the actual display name if available, otherwise format from material
                String displayItemName = auction.getItemStack() != null && auction.getItemStack().hasItemMeta() && auction.getItemStack().getItemMeta().hasDisplayName()
                                       ? auction.getItemStack().getItemMeta().getDisplayName()
                                       : (auction.getItemStack() != null ? formatMaterialName(auction.getItemStack().getType()) : auction.getItemName());
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', localeManager.getRawMessage("auction.gui.item-name-prefix", "&b") + displayItemName));

                lore.add(localeManager.getMessage("auction.gui.lore.seller", "{seller_name}", auction.getSellerName()));

                if (auction.getCurrentBid() != null && auction.getCurrentBid() > 0) {
                    lore.add(localeManager.getMessage("auction.gui.lore.current-bid", "{amount}", String.format("%,.2f", auction.getCurrentBid())));
                    if (auction.getHighestBidderName() != null && !auction.getHighestBidderName().isEmpty()) {
                        lore.add(localeManager.getMessage("auction.gui.lore.highest-bidder", "{player_name}", auction.getHighestBidderName()));
                    } else {
                        // This case should ideally not happen if currentBid > 0, but as a fallback:
                        lore.add(localeManager.getMessage("auction.gui.lore.no-current-bidder"));
                    }
                } else {
                    lore.add(localeManager.getMessage("auction.gui.lore.initial-price", "{amount}", String.format("%,.2f", auction.getInitialPrice())));
                    lore.add(localeManager.getMessage("auction.gui.lore.no-current-bidder")); // No bids yet means no highest bidder
                }

                if (auction.getBuyNowPrice() != null && auction.getBuyNowPrice() > 0) {
                    lore.add(localeManager.getMessage("auction.gui.lore.buy-now-price", "{amount}", String.format("%,.2f", auction.getBuyNowPrice())));
                } else {
                    lore.add(localeManager.getMessage("auction.gui.lore.buy-now-not-available"));
                }

                long timeLeftMillis = auction.getEndTime() - System.currentTimeMillis();
                lore.add(localeManager.getMessage("auction.gui.lore.time-left", "{time}", TimeUtil.formatDuration(timeLeftMillis > 0 ? timeLeftMillis : 0)));

                lore.add(" "); // Spacer
                lore.add(localeManager.getMessage("auction.gui.lore.bid-instruction"));
                if (auction.getBuyNowPrice() != null && auction.getBuyNowPrice() > 0) {
                     lore.add(localeManager.getMessage("auction.gui.lore.buy-instruction"));
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
        // Slot 45: Prev | Slot 49: Close | Slot 50: Refresh (Optional) | Slot 53: Next
        if (page > 1) {
            gui.setItem(45, createControlItem(Material.ARROW, localeManager.getMessage("auction.gui.button.previous-page-name"), "prev_page"));
        }

        gui.setItem(49, createControlItem(Material.BARRIER, localeManager.getMessage("auction.gui.button.close-gui-name"), "close_gui"));
        gui.setItem(50, createControlItem(Material.SUNFLOWER, localeManager.getMessage("auction.gui.button.refresh-name"), "refresh_gui"));


        if (page < totalPages) {
            gui.setItem(53, createControlItem(Material.ARROW, localeManager.getMessage("auction.gui.button.next-page-name"), "next_page"));
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

    private ItemStack createControlItem(Material material, String name, String actionTag) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            NamespacedKey key = new NamespacedKey(plugin, "gui_action");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, actionTag);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatMaterialName(Material material) {
        // Converts MATERIAL_NAME to "Material Name"
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                formattedName.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return formattedName.toString().trim();
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

    public Set<UUID> getPlayersWithGuiOpen() {
        return Collections.unmodifiableSet(openGUIPages.keySet()); // Provide read-only access
    }

    public void refreshAuctionGuiForPlayer(Player player) {
        if (isPlayerViewingAuctionGUI(player)) {
            // Re-fetch current page as it might be needed if player object is stale or for safety
            int currentPage = getPlayerCurrentPage(player);
            // To prevent issues if player logs off during refresh, ensure they are online.
            // Bukkit.getPlayer(player.getUniqueId()) will return null if offline.
            Player onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                 // Run on next tick to avoid issues if called during an inventory event loop
                Bukkit.getScheduler().runTask(plugin, () -> openActiveAuctionsGUI(onlinePlayer, currentPage));
            } else {
                // Player logged off, remove them
                removePlayer(player.getUniqueId());
            }
        }
    }

    public void refreshOpenAuctionGuis() {
        // Create a copy of the set to avoid ConcurrentModificationException if a GUI closure modifies the original map
        Set<UUID> playersToRefresh = new HashSet<>(openGUIPages.keySet());

        for (UUID playerUuid : playersToRefresh) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                refreshAuctionGuiForPlayer(player);
            } else {
                // Player is offline, remove them from tracking
                removePlayer(playerUuid); // Make removePlayer accept UUID
            }
        }
    }

    // Overload removePlayer to accept UUID for internal use
    public void removePlayer(UUID playerUuid) {
        openGUIPages.remove(playerUuid);
        openInventories.remove(playerUuid);
        playerAuctionSlots.remove(playerUuid);
    }
}
