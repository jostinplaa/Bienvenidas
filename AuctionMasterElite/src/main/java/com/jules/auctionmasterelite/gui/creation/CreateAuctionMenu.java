package com.jules.auctionmasterelite.gui.creation;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.gui.GUI;
import com.jules.auctionmasterelite.managers.PlayerInputManager;
import com.jules.auctionmasterelite.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

import com.jules.auctionmasterelite.data.AuctionType;
import com.jules.auctionmasterelite.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;

public class CreateAuctionMenu extends GUI {

    private ItemStack itemToAuction;
    private AuctionType currentType = AuctionType.PUBLIC;
    private double price = 0.0;
    private long duration = -1; // in milliseconds

    public CreateAuctionMenu(AuctionMasterElite plugin, Player player) {
        super(plugin, 54, "§8Crear Nueva Subasta");
        initializeItems(player);
    }

    private void initializeItems(Player player) {
        // Fill the background with glass panes
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, background);
        }

        // Item slot - players will place their item here
        inventory.setItem(13, new ItemStack(Material.AIR)); // The slot for the item

        // Configuration buttons
        inventory.setItem(29, createGuiItem(Material.GOLD_NUGGET, "§6Precio Inicial", "§eHaz clic para establecer", "§eel precio de inicio."));
        inventory.setItem(31, createGuiItem(Material.CLOCK, "§bDuración", "§eHaz clic para establecer", "§ela duración de la subasta."));
        inventory.setItem(33, createGuiItem(Material.PAPER, "§dTipo de Subasta", "§eHaz clic para cambiar", "§eel tipo de subasta."));

        // Control buttons
        inventory.setItem(48, createGuiItem(Material.RED_WOOL, "§cCancelar", "§7Vuelve al menú principal."));
        inventory.setItem(50, createGuiItem(Material.GREEN_WOOL, "§aConfirmar y Crear", "§7Crea la subasta con", "§7la configuración actual."));

        updateAuctionTypeItem();
        updatePriceItem();
        updateDurationItem();
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
        int slot = event.getRawSlot();

        // Check if click is in the top inventory (the GUI)
        if (slot < 54) {
            // The designated slot for the item to be auctioned
            if (slot == 13) {
                // This allows the player to place an item in the slot.
                // We don't cancel the event here.
                // After the click event, we can retrieve the item.
                this.itemToAuction = event.getClickedInventory().getItem(slot);
            } else {
                // For all other slots in the GUI, cancel the event
                event.setCancelled(true);

                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

                // Handle button clicks
                switch (clickedItem.getType()) {
                    case GOLD_NUGGET:
                        player.closeInventory();
                        MessageUtil.sendMessage(player, "prompt-for-price");
                        plugin.getPlayerInputManager().requestInput(player.getUniqueId(), (priceInput) -> {
                            try {
                                this.price = Double.parseDouble(priceInput);
                                MessageUtil.sendMessage(player, "price-set", "price", String.format("%.2f", this.price));
                                Bukkit.getScheduler().runTask(plugin, this::updatePriceItem);
                            } catch (NumberFormatException e) {
                                MessageUtil.sendMessage(player, "invalid-price-input");
                            }
                            // Re-open the GUI for the player
                            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(getInventory()));
                        });
                        break;
                    case CLOCK:
                        if (currentType == AuctionType.FLASH) {
                            // Message is already in the lore, no need to send another.
                            return;
                        }
                        player.closeInventory();
                        MessageUtil.sendMessage(player, "prompt-for-duration");
                        plugin.getPlayerInputManager().requestInput(player.getUniqueId(), (durationInput) -> {
                            long parsedDuration = TimeUtil.parseTime(durationInput);
                            if (parsedDuration > 0) {
                                this.duration = parsedDuration;
                                MessageUtil.sendMessage(player, "duration-set", "duration", durationInput);
                                Bukkit.getScheduler().runTask(plugin, this::updateDurationItem);
                            } else {
                                MessageUtil.sendMessage(player, "invalid-duration-input");
                            }
                            // Re-open the GUI for the player
                            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(getInventory()));
                        });
                        break;
                    case RED_WOOL: // Cancel button
                        player.openInventory(new com.jules.auctionmasterelite.gui.menu.MainMenu(plugin).getInventory());
                        break;
                    case GREEN_WOOL: // Confirm button
                        handleAuctionCreation(player);
                        break;
                    case PAPER: // Change Auction Type
                        cycleAuctionType(player);
                        break;
                }
            }
        }
        // If the click is in the player's inventory (slot >= 54), we don't do anything,
        // allowing them to move items freely.
    }

    private void cycleAuctionType(Player player) {
        // Cycle through PUBLIC, PRIVATE, FLASH. SCHEDULED will be handled separately.
        switch (currentType) {
            case PUBLIC:
                currentType = AuctionType.PRIVATE;
                break;
            case PRIVATE:
                currentType = AuctionType.FLASH;
                break;
            case FLASH:
                currentType = AuctionType.PUBLIC;
                break;
        }
        updateAuctionTypeItem();
        updateDurationItem(); // Also update the duration item in case it needs to be disabled/enabled
    }

    private void updateAuctionTypeItem() {
        String lore1 = "§eHaz clic para cambiar";
        String lore2 = "§eel tipo de subasta.";
        String current = "§7Actual: §a" + currentType.toString();
        inventory.setItem(33, createGuiItem(Material.PAPER, "§dTipo de Subasta", lore1, lore2, "", current));
    }

    private void updatePriceItem() {
        String lore1 = "§eHaz clic para establecer";
        String lore2 = "§eel precio de inicio.";
        String current = "§7Actual: §6" + (price > 0 ? String.format("%.2f", price) : "No establecido");
        inventory.setItem(29, createGuiItem(Material.GOLD_NUGGET, "§6Precio Inicial", lore1, lore2, "", current));
    }

    private void updateDurationItem() {
        if (currentType == AuctionType.FLASH) {
            String lore1 = "§cLas subastas Flash tienen";
            String lore2 = "§cuna duración fija de 60 segundos.";
            inventory.setItem(31, createGuiItem(Material.CLOCK, "§bDuración", lore1, lore2));
        } else {
            String lore1 = "§eHaz clic para establecer";
            String lore2 = "§ela duración de la subasta.";
            String current = "§7Actual: §b" + (duration > 0 ? TimeUtil.formatDuration(duration) : "No establecida");
            inventory.setItem(31, createGuiItem(Material.CLOCK, "§bDuración", lore1, lore2, "", current));
        }
    }

    private void handleAuctionCreation(Player player) {
        itemToAuction = inventory.getItem(13); // Make sure we have the latest item state

        if (itemToAuction == null || itemToAuction.getType() == Material.AIR) {
            MessageUtil.sendMessage(player, "creation-no-item");
            return;
        }
        if (price <= 0) {
            MessageUtil.sendMessage(player, "creation-no-price");
            return;
        }
        if (currentType != AuctionType.FLASH && duration <= 0) {
            MessageUtil.sendMessage(player, "creation-no-duration");
            return;
        }

        // Check auction limit
        int maxAuctions = plugin.getConfigManager().getConfig().getInt("settings.max-active-auctions", 10);
        if (maxAuctions > 0 && plugin.getAuctionManager().getAuctionsBySeller(player.getUniqueId()).size() >= maxAuctions) {
            MessageUtil.sendMessage(player, "creation-limit-reached"); // Assumes new message key
            return;
        }

        // All checks passed, create the auction
        player.closeInventory();
        inventory.setItem(13, new ItemStack(Material.AIR)); // Remove item from GUI

        long auctionDuration = (currentType == AuctionType.FLASH) ? 60 * 1000 : duration;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + auctionDuration;

        com.jules.auctionmasterelite.data.Auction newAuction = new com.jules.auctionmasterelite.data.Auction(
                player.getUniqueId(),
                player.getName(),
                itemToAuction.clone(),
                startTime,
                endTime,
                price,
                currentType
        );

        plugin.getAuctionManager().createAuction(newAuction);
        plugin.getDatabaseManager().saveAuction(newAuction);

        MessageUtil.sendMessage(player, "creation-success");
    }
}
