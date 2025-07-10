package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.ChatColor; // Importación añadida
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PrepareMysteryLotGUI {

    private final AetherAuctions plugin;
    private final Player player;
    private final double startPrice;
    private final double buyNowPrice;
    private final long durationHours; // Duración en horas como en el comando
    private final String description;
    private final Inventory gui;

    // Las constantes de slot y MAX_LOT_ITEMS se leerán de config.yml
    // public static final int MAX_LOT_ITEMS = 36;
    // public static final int CANCEL_BUTTON_SLOT = 47;
    // public static final int INFO_SLOT = 49;
    // public static final int CONFIRM_BUTTON_SLOT = 51;

    private final int maxLotItems; // Para almacenar el valor de config

    // Mapa para almacenar temporalmente los items que el jugador añade al lote.
    // No es estático, cada instancia de GUI tiene su propio conjunto de ítems.
    private final List<ItemStack> lotItems;
    private boolean confirmed = false; // Para rastrear si la GUI se confirmó o se cerró/canceló

    public PrepareMysteryLotGUI(AetherAuctions plugin, Player player, double startPrice, double buyNowPrice, long durationHours, String description) {
        this.plugin = plugin;
        this.player = player;
        this.startPrice = startPrice;
        this.buyNowPrice = buyNowPrice;
        this.durationHours = durationHours;
        this.description = description;
        this.lotItems = new ArrayList<>();
        this.maxLotItems = plugin.getConfigManager().getMysteryLotMaxItems(); // Leer de ConfigManager

        MessageManager msgManager = plugin.getMessageManager();
        String title = msgManager.getMessage("prepare_mystery_gui_title", "%player%", player.getName());
        this.gui = Bukkit.createInventory(player, 54, title);
    }

    public void open() {
        renderGUI();
        player.openInventory(gui);
        // Registrar la GUI abierta con OpenGUIManager
        // Pasamos 'this' como instancia, el inventario de Bukkit, página 0 (no aplicable pero requerido),
        // el tipo de GUI, null para visibleAuctionIds (no aplica), y "default" para sort (no aplica).
        plugin.getOpenGUIManager().playerOpenedGUI(player, this, gui, 0, "PrepareMysteryLotGUI", null, "default");
    }

    public void renderGUI() {
        gui.clear();
        String guiKey = "prepare_mystery_lot_gui"; // Clave base para esta GUI en config

        // Área de ítems del lote (slots 0 hasta maxLotItems - 1)
        for (int i = 0; i < lotItems.size() && i < this.maxLotItems; i++) {
            gui.setItem(i, lotItems.get(i));
        }

        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager();

        // Botón de Cancelar
        ItemStack cancelButton = InventoryUtil.createGuiItem(
                cfgManager.getButtonMaterial(guiKey, "cancel_preparation", "BARRIER"),
                msgManager.getMessage("prepare_mystery_gui_button_cancel_name"),
                msgManager.getStringList("prepare_mystery_gui_button_cancel_lore"));
        gui.setItem(cfgManager.getButtonSlot(guiKey, "cancel_preparation", 47), cancelButton);

        // Slot de Información
        List<String> infoLore = new ArrayList<>();
        infoLore.add(msgManager.getMessage("prepare_mystery_gui_info_item_count", "%count%", String.valueOf(lotItems.size()), "%max%", String.valueOf(this.maxLotItems)));
        infoLore.add(msgManager.getMessage("prepare_mystery_gui_info_description_label"));
        infoLore.add(ChatColor.GRAY + description);
        ItemStack infoItem = InventoryUtil.createGuiItem(
                cfgManager.getButtonMaterial(guiKey, "info_display_item", "PAPER"),
                msgManager.getMessage("prepare_mystery_gui_info_title"),
                infoLore);
        gui.setItem(cfgManager.getButtonSlot(guiKey, "info_display_item", 49), infoItem);

        // Botón de Confirmar
        ItemStack confirmButton = InventoryUtil.createGuiItem(
                cfgManager.getButtonMaterial(guiKey, "confirm_and_create", "EMERALD_BLOCK"),
                msgManager.getMessage("prepare_mystery_gui_button_confirm_name"),
                msgManager.getStringList("prepare_mystery_gui_button_confirm_lore"));
        gui.setItem(cfgManager.getButtonSlot(guiKey, "confirm_and_create", 51), confirmButton);

        // Rellenar slots vacíos restantes en la última fila
        Material decoMat = cfgManager.getMainDecorativePaneMaterial();
        ItemStack decorativePane = InventoryUtil.createGuiItem(decoMat, " ");
        // Los slots específicos de botones ya están definidos, rellenar el resto de la fila inferior
        for (int i = 45; i < 54; i++) { // Fila inferior
            if (gui.getItem(i) == null) { // Solo rellenar si está vacío
                gui.setItem(i, decorativePane.clone());
            }
        }
    }

    public Inventory getInventory() {
        return gui;
    }

    public Player getPlayer() {
        return player;
    }

    public List<ItemStack> getLotItems() {
        // Recoger los ítems directamente de la GUI al confirmar, en lugar de esta lista,
        // para asegurar que se usan los ítems que el jugador ve.
        // Esta lista 'lotItems' es más un caché interno mientras se añaden/quitan.
        List<ItemStack> currentGuiItems = new ArrayList<>();
        for (int i = 0; i < MAX_LOT_ITEMS; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                currentGuiItems.add(item.clone()); // Clonar para evitar problemas de referencia
            }
        }
        return currentGuiItems;
    }

    public void addItemToLot(ItemStack item) {
        if (lotItems.size() < this.maxLotItems) { // Usar this.maxLotItems
            lotItems.add(item.clone());
            renderGUI();
        } else {
            plugin.getMessageManager().sendMessage(player, "prepare_mystery_gui_error_lot_full");
        }
    }

    public void removeItemFromLot(int guiSlotIndex) { // El slot de la GUI es el índice de la lista
        if (guiSlotIndex >= 0 && guiSlotIndex < lotItems.size()) { // No MAX_LOT_ITEMS aquí, sino el tamaño actual de la lista
            lotItems.remove(guiSlotIndex);
            renderGUI();
        } else if (guiSlotIndex >= lotItems.size() && guiSlotIndex < this.maxLotItems) {
            // Clic en un slot vacío del área de ítems, no hacer nada o loguear si es inesperado.
            // No debería llegar aquí si currentItem es null en el listener.
        }
    }

    public int getMaxLotItems() { // Getter para que el listener lo use si es necesario
        return this.maxLotItems;
    }

    public double getStartPrice() { return startPrice; }
    public double getBuyNowPrice() { return buyNowPrice; }
    public long getDurationHours() { return durationHours; }
    public String getDescription() { return description; }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
