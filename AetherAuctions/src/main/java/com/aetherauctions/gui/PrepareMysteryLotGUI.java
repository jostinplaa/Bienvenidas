package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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

    public static final int MAX_LOT_ITEMS = 36; // Filas 1-4
    public static final int CANCEL_BUTTON_SLOT = 47; // Ajustado, era 48
    public static final int INFO_SLOT = 49;          // Slot central para info
    public static final int CONFIRM_BUTTON_SLOT = 51; // Ajustado, era 50

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
        this.lotItems = new ArrayList<>(); // Inicializar la lista de ítems del lote

        MessageManager msgManager = plugin.getMessageManager();
        String title = msgManager.getMessage("prepare_mystery_gui_title", "%player%", player.getName());
        this.gui = Bukkit.createInventory(player, 54, title); // Player como owner
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
        gui.clear(); // Limpiar la GUI antes de volver a renderizar

        // Área de ítems del lote (slots 0-35)
        for (int i = 0; i < lotItems.size() && i < MAX_LOT_ITEMS; i++) {
            gui.setItem(i, lotItems.get(i));
        }

        MessageManager msgManager = plugin.getMessageManager();
        ConfigManager cfgManager = plugin.getConfigManager(); // No se usa directamente aquí, pero podría ser útil

        // Botón de Cancelar
        ItemStack cancelButton = InventoryUtil.createGuiItem(Material.BARRIER,
                msgManager.getMessage("prepare_mystery_gui_button_cancel_name"),
                msgManager.getStringList("prepare_mystery_gui_button_cancel_lore"));
        gui.setItem(CANCEL_BUTTON_SLOT, cancelButton);

        // Slot de Información (ej. contador de ítems)
        List<String> infoLore = new ArrayList<>();
        infoLore.add(msgManager.getMessage("prepare_mystery_gui_info_item_count", "%count%", String.valueOf(lotItems.size()), "%max%", String.valueOf(MAX_LOT_ITEMS)));
        infoLore.add(msgManager.getMessage("prepare_mystery_gui_info_description_label"));
        infoLore.add(ChatColor.GRAY + description); // Mostrar la descripción del lote
        // Podría añadir más info como precio, duración aquí si se desea.
        ItemStack infoItem = InventoryUtil.createGuiItem(Material.PAPER, // O BOOK, etc.
                msgManager.getMessage("prepare_mystery_gui_info_title"),
                infoLore);
        gui.setItem(INFO_SLOT, infoItem);


        // Botón de Confirmar
        ItemStack confirmButton = InventoryUtil.createGuiItem(Material.EMERALD_BLOCK, // O GREEN_STAINED_GLASS_PANE, etc.
                msgManager.getMessage("prepare_mystery_gui_button_confirm_name"),
                msgManager.getStringList("prepare_mystery_gui_button_confirm_lore"));
        gui.setItem(CONFIRM_BUTTON_SLOT, confirmButton);

        // Rellenar slots vacíos restantes en la última fila si se desea
        Material decoMat = cfgManager.getMainDecorativePaneMaterial(); // Reutilizar material decorativo
        ItemStack decorativePane = InventoryUtil.createGuiItem(decoMat, " ");
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) {
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
        if (lotItems.size() < MAX_LOT_ITEMS) {
            lotItems.add(item.clone()); // Clonar para seguridad
            renderGUI(); // Actualizar la GUI para mostrar el nuevo ítem
        } else {
            // Enviar mensaje de que el lote está lleno
            plugin.getMessageManager().sendMessage(player, "prepare_mystery_gui_error_lot_full");
        }
    }

    public void removeItemFromLot(int slot) {
        if (slot >= 0 && slot < MAX_LOT_ITEMS && slot < lotItems.size()) {
            ItemStack removed = lotItems.remove(slot);
            if (removed != null) {
                 // No es necesario devolverlo aquí, el InventoryClickListener lo hará
                 // player.getInventory().addItem(removed);
            }
            renderGUI(); // Actualizar la GUI
        }
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
