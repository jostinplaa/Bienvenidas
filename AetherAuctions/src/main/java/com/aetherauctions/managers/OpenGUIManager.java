package com.aetherauctions.managers;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.events.AuctionUpdateEvent;
import com.aetherauctions.guis.MainAuctionGUI;
import com.aetherauctions.guis.MyActiveAuctionsGUI;
import com.aetherauctions.model.Auction;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.aetherauctions.util.InventoryUtil;
import java.util.List;
import java.util.ArrayList;
import java.sql.SQLException;
import java.util.logging.Level;
import org.bukkit.Material;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// GUI imports that might be needed for specific GUI tracking (add as necessary)
import com.aetherauctions.guis.BidGUI;
import com.aetherauctions.guis.ConfirmBuyoutGUI;
import com.aetherauctions.guis.ClaimRewardsGUI;
import com.aetherauctions.guis.PlayerHistoryGUI;
import com.aetherauctions.guis.AdminHistoryGUI;
import com.aetherauctions.guis.ManageAuctionContextGUI;

public class OpenGUIManager implements Listener {

    private final AetherAuctions plugin;
    private final Map<UUID, ActiveGUIInfo> openGUIs = new ConcurrentHashMap<>();

    // Specific GUI tracking maps
    private final Map<UUID, BidGUI> openBidGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, ConfirmBuyoutGUI> openConfirmBuyoutGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, ClaimRewardsGUI> openClaimRewardsGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerHistoryGUI> openPlayerHistoryGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, AdminHistoryGUI> openAdminHistoryGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, ManageAuctionContextGUI> openManageAuctionContextGUIs = new ConcurrentHashMap<>();


    public static class ActiveGUIInfo {
        public Object guiInstance;
        public Inventory inventory;
        public int currentPage;
        public String guiType;
        public Map<Integer, UUID> visibleAuctionIdsBySlot;
        public String currentSort;

        public ActiveGUIInfo(Object guiInstance, Inventory inventory, int currentPage, String guiType, Map<Integer, UUID> visibleAuctionIdsBySlot, String currentSort) {
            this.guiInstance = guiInstance;
            this.inventory = inventory;
            this.currentPage = currentPage;
            this.guiType = guiType;
            this.visibleAuctionIdsBySlot = visibleAuctionIdsBySlot != null ? new HashMap<>(visibleAuctionIdsBySlot) : new HashMap<>();
            this.currentSort = currentSort;
        }
    }

    public OpenGUIManager(AetherAuctions plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void playerOpenedGUI(Player player, Object guiInstance, Inventory bukkitInventory, int page, String guiType, Map<Integer, UUID> visibleAuctionIds, String currentSort) {
        openGUIs.put(player.getUniqueId(), new ActiveGUIInfo(guiInstance, bukkitInventory, page, guiType, visibleAuctionIds, currentSort));

        if (guiInstance instanceof BidGUI) openBidGUIs.put(player.getUniqueId(), (BidGUI) guiInstance);
        else if (guiInstance instanceof ConfirmBuyoutGUI) openConfirmBuyoutGUIs.put(player.getUniqueId(), (ConfirmBuyoutGUI) guiInstance);
        else if (guiInstance instanceof ClaimRewardsGUI) openClaimRewardsGUIs.put(player.getUniqueId(), (ClaimRewardsGUI) guiInstance);
        else if (guiInstance instanceof PlayerHistoryGUI) openPlayerHistoryGUIs.put(player.getUniqueId(), (PlayerHistoryGUI) guiInstance);
        else if (guiInstance instanceof AdminHistoryGUI) openAdminHistoryGUIs.put(player.getUniqueId(), (AdminHistoryGUI) guiInstance);
        else if (guiInstance instanceof ManageAuctionContextGUI) openManageAuctionContextGUIs.put(player.getUniqueId(), (ManageAuctionContextGUI) guiInstance);
    }

    public void registerOpenGUI(Player player, MainAuctionGUI gui, Map<Integer, UUID> visibleAuctionsMap) {
        playerOpenedGUI(player, gui, player.getOpenInventory().getTopInventory(), gui.getCurrentPage(), "MainAuctionGUI", visibleAuctionsMap, gui.getCurrentSort());
    }
     public void registerOpenGUI(Player player, MyActiveAuctionsGUI gui, Map<Integer, UUID> visibleAuctionsMap) {
        playerOpenedGUI(player, gui, player.getOpenInventory().getTopInventory(), gui.getCurrentPage(), "MyActiveAuctionsGUI", visibleAuctionsMap, "default");
    }

    public void onInventoryClose(Player player, String closedInventoryTitle) {
        ActiveGUIInfo info = openGUIs.get(player.getUniqueId());
        if (info != null && info.inventory != null && info.inventory.getTitle().equals(closedInventoryTitle)) {
            openGUIs.remove(player.getUniqueId());
        }
        openBidGUIs.remove(player.getUniqueId());
        openConfirmBuyoutGUIs.remove(player.getUniqueId());
        openClaimRewardsGUIs.remove(player.getUniqueId());
        openPlayerHistoryGUIs.remove(player.getUniqueId());
        openAdminHistoryGUIs.remove(player.getUniqueId());
        openManageAuctionContextGUIs.remove(player.getUniqueId());
    }

    public ActiveGUIInfo getOpenGUIInfo(Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    public MainAuctionGUI getOpenMainGUI(Player player) {
        ActiveGUIInfo info = openGUIs.get(player.getUniqueId());
        if (info != null && info.guiInstance instanceof MainAuctionGUI) return (MainAuctionGUI) info.guiInstance;
        return null;
    }
    public MyActiveAuctionsGUI getOpenMyActiveAuctionsGUI(Player player) {
         ActiveGUIInfo info = openGUIs.get(player.getUniqueId());
        if (info != null && info.guiInstance instanceof MyActiveAuctionsGUI) return (MyActiveAuctionsGUI) info.guiInstance;
        return null;
    }
    public BidGUI getOpenBidGUI(Player player) { return openBidGUIs.get(player.getUniqueId()); }
    public ConfirmBuyoutGUI getOpenConfirmBuyoutGUI(Player player) { return openConfirmBuyoutGUIs.get(player.getUniqueId()); }
    public ClaimRewardsGUI getOpenClaimRewardsGUI(Player player) { return openClaimRewardsGUIs.get(player.getUniqueId()); }
    public PlayerHistoryGUI getOpenPlayerHistoryGUI(Player player) { return openPlayerHistoryGUIs.get(player.getUniqueId()); }
    public AdminHistoryGUI getOpenAdminHistoryGUI(Player player) { return openAdminHistoryGUIs.get(player.getUniqueId()); }
    public ManageAuctionContextGUI getOpenManageAuctionContextGUI(Player player) { return openManageAuctionContextGUIs.get(player.getUniqueId()); }

    public void removeBidGUI(Player player) { openBidGUIs.remove(player.getUniqueId()); }
    public void removeConfirmBuyoutGUI(Player player) { openConfirmBuyoutGUIs.remove(player.getUniqueId());}


    @EventHandler
    public void onAuctionUpdate(AuctionUpdateEvent event) {
        Auction updatedAuction = event.getAuction();
        AuctionUpdateEvent.UpdateType type = event.getUpdateType();

        for (Map.Entry<UUID, ActiveGUIInfo> entry : openGUIs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                openGUIs.remove(entry.getKey());
                continue;
            }

            ActiveGUIInfo guiInfo = entry.getValue();
            if ("MainAuctionGUI".equals(guiInfo.guiType) || "MyActiveAuctionsGUI".equals(guiInfo.guiType)) {
                int affectedSlot = -1;
                if (guiInfo.visibleAuctionIdsBySlot != null) {
                    for (Map.Entry<Integer, UUID> visibleEntry : guiInfo.visibleAuctionIdsBySlot.entrySet()) {
                        if (visibleEntry.getValue().equals(updatedAuction.getAuctionId())) {
                            affectedSlot = visibleEntry.getKey();
                            break;
                        }
                    }
                }

                if (type == AuctionUpdateEvent.UpdateType.NEW_AUCTION_LISTED ||
                    type == AuctionUpdateEvent.UpdateType.EXPIRED ||
                    type == AuctionUpdateEvent.UpdateType.CANCELLED ||
                    type == AuctionUpdateEvent.UpdateType.SOLD_BID ||
                    type == AuctionUpdateEvent.UpdateType.SOLD_BUYNOW) {
                    plugin.getLogger().fine("Refrescando GUI completa para " + player.getName() + " debido a evento: " + type.name());
                    refreshFullGui(player, guiInfo);
                } else if (affectedSlot != -1 && type == AuctionUpdateEvent.UpdateType.NEW_BID) {
                    plugin.getLogger().fine("Actualizando slot " + affectedSlot + " para " + player.getName() + " debido a NEW_BID en subasta " + updatedAuction.getAuctionId());
                    ItemStack newDisplayItem = createDisplayItemForAuction(updatedAuction, player);
                    if (newDisplayItem != null && guiInfo.inventory != null && affectedSlot >= 0 && affectedSlot < guiInfo.inventory.getSize()) {
                        guiInfo.inventory.setItem(affectedSlot, newDisplayItem);
                    }
                }
            }
        }
    }

    private void refreshFullGui(Player player, ActiveGUIInfo guiInfo) {
        if ("MainAuctionGUI".equals(guiInfo.guiType) && guiInfo.guiInstance instanceof MainAuctionGUI) {
            ((MainAuctionGUI)guiInfo.guiInstance).openGUI(guiInfo.currentPage, guiInfo.currentSort);
        } else if ("MyActiveAuctionsGUI".equals(guiInfo.guiType) && guiInfo.guiInstance instanceof MyActiveAuctionsGUI) {
             ((MyActiveAuctionsGUI)guiInfo.guiInstance).openGUI(guiInfo.currentPage);
        }
    }

    public ItemStack createDisplayItemForAuction(Auction auction, Player viewer) {
        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();
        ItemStack displayItem = null;
        ItemMeta meta = null;
        List<String> lore = new ArrayList<>();

        if (auction.isMystery()) {
            displayItem = new ItemStack(Material.ENDER_CHEST);
            meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(msgManager.getRawMessage("mystery_auction_gui_item_name"));
                lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_description", "%description%", auction.getMysteryDescription()));
                try {
                    int itemCount = plugin.getAuctionStorage().getMysteryAuctionContentsCount(auction.getAuctionId());
                    lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_item_count", "%count%", String.valueOf(itemCount)));
                } catch (SQLException e) {
                     plugin.getLogger().log(Level.WARNING, "Could not get item count for mystery auction " + auction.getAuctionId() + " for GUI update.", e);
                     lore.add(msgManager.getRawMessage("mystery_auction_gui_lore_item_count_error"));
                }
            }
        } else {
            if (auction.getItemStack() == null || auction.getItemStack().getType() == Material.AIR) {
                 displayItem = new ItemStack(Material.BARRIER);
                 meta = displayItem.getItemMeta();
                 if(meta != null) meta.setDisplayName(ChatColor.RED + "Error: Ítem no disponible");
            } else {
                displayItem = auction.getItemStack().clone();
                meta = displayItem.getItemMeta();
            }
            if (meta == null && displayItem != null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

            if (meta != null) {
                String itemName = meta.hasDisplayName() ? meta.getDisplayName() : InventoryUtil.formatMaterialName(displayItem.getType());
                meta.setDisplayName(msgManager.getRawMessage("main_gui_item_name_format", "%item_name%", itemName));
                if (displayItem.getType() != Material.BARRIER) {
                    lore.add(msgManager.getRawMessage("main_gui_lore_item_material", "%material%", displayItem.getType().toString()));
                }
            }
        }

        if (displayItem == null) {
            displayItem = new ItemStack(Material.BARRIER);
            meta = displayItem.getItemMeta();
            if (meta != null) meta.setDisplayName(ChatColor.RED + "Error al mostrar ítem");
        }

        if (meta == null && displayItem != null) meta = displayItem.getItemMeta();
        if (meta == null && displayItem != null) meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());

        if (meta != null) {
            lore.add(msgManager.getRawMessage("main_gui_lore_seller", "%seller%", auction.getSellerName()));
            lore.add(msgManager.getRawMessage("main_gui_lore_price",
                "%price%", String.format("%.2f", auction.getCurrentBid()),
                "%currency%", cfgManager.getCurrencySymbol()
            ));
            if (auction.getBuyoutPrice() > 0 && cfgManager.isBuyNowAllowed()) {
                lore.add(msgManager.getRawMessage("main_gui_lore_buy_now",
                    "%buy_now_price%", String.format("%.2f", auction.getBuyoutPrice()),
                    "%currency%", cfgManager.getCurrencySymbol()
                ));
            } else {
                lore.add(msgManager.getRawMessage("main_gui_lore_buy_now_not_available"));
            }
            lore.add(msgManager.getRawMessage("main_gui_lore_time_remaining", "%time%", InventoryUtil.formatTime(auction.getRemainingTimeMillis())));
            lore.add(msgManager.getRawMessage("main_gui_lore_id", "%id%", auction.getAuctionId().toString()));
            lore.add(msgManager.getRawMessage("main_gui_lore_instruction_details"));

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }
}
