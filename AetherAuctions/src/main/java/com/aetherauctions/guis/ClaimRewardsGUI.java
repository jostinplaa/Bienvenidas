package com.aetherauctions.guis;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.PendingReward;
import com.aetherauctions.managers.RewardManager;
import com.aetherauctions.util.InventoryUtil;
import com.aetherauctions.util.ItemUtil; // Asumiendo que tienes una clase para crear items de GUI
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClaimRewardsGUI implements Listener {

    private final AetherAuctions plugin;
    private final RewardManager rewardManager;
    private static final String GUI_TITLE_KEY = "claim_gui_title"; // Necesitarás añadir esto a messages.yml
    private static final int GUI_SIZE = 54; // 6 filas

    public ClaimRewardsGUI(AetherAuctions plugin) {
        this.plugin = plugin;
        this.rewardManager = plugin.getRewardManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        String title = plugin.getMessageManager().getRawMessage(GUI_TITLE_KEY, "%player_name%", player.getName());
        Inventory gui = Bukkit.createInventory(player, GUI_SIZE, title);

        // Obtener recompensas de forma asíncrona para no bloquear el hilo principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PendingReward> rewards = plugin.getAuctionStorage().getPendingRewardsByOwner(player.getUniqueId())
                    .stream()
                    .filter(r -> !r.isDelivered())
                    .collect(Collectors.toList());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (rewards.isEmpty()) {
                    plugin.getMessageManager().sendMessage(player, "rewards_none_pending");
                    player.closeInventory(); // Cerrar si no hay nada que mostrar
                    return;
                }
                populateGUI(gui, rewards, player);
                player.openInventory(gui);
            });
        });
    }

    private void populateGUI(Inventory gui, List<PendingReward> rewards, Player player) {
        gui.clear();
        int slot = 0;

        for (PendingReward reward : rewards) {
            if (slot >= GUI_SIZE - 9) break; // Dejar espacio para botones de control si es necesario

            ItemStack displayItem;
            List<String> lore = new ArrayList<>();
            // String rewardIdShort = reward.getRewardId().toString().substring(0, 8); // No es necesario aquí si el mensaje de razón ya lo incluye.

            // Construir los placeholders para el mensaje de razón.
            // Es crucial que si el mensaje de razón usa %id_short%, se pase %id% con el UUID completo.
            List<String> reasonPlaceholdersWithFullId = new ArrayList<>(reward.getReasonPlaceholders());
            boolean hasIdPlaceholder = false;
            for (int i = 0; i < reasonPlaceholdersWithFullId.size(); i += 2) {
                if ("%id%".equals(reasonPlaceholdersWithFullId.get(i)) || "%auction_id%".equals(reasonPlaceholdersWithFullId.get(i)) || "#id_short%".equals(reasonPlaceholdersWithFullId.get(i))) {
                    hasIdPlaceholder = true;
                    // Asegurarse de que el valor sea el UUID completo si el placeholder es %id% o %auction_id%
                    // y que MessageManager lo maneje para %id_short%
                    // Si el placeholder ya es #id_short% y el valor es corto, MessageManager no lo tocará.
                    // Si el placeholder es %id_short% (correcto), MessageManager lo reemplazará si %id% se pasa.
                }
            }
            // Si no hay un placeholder de ID explícito en los placeholders de la recompensa,
            // pero el mensaje de razón SÍ espera un %id% o %id_short% (lo cual es común para el origen),
            // debemos añadirlo. Esto es un poco una suposición y podría necesitar ajuste.
            // La forma más segura es que la creación de PendingReward SIEMPRE incluya "%id%", auction.getId().toString()
            // en sus placeholders si el reasonMessageKey lo va a usar.

            // Para el lore de la GUI, queremos el ID corto de la recompensa, no de la subasta necesariamente.
             lore.add(ChatColor.GRAY + "ID Recompensa: " + ChatColor.YELLOW + reward.getRewardId().toString().substring(0, 8));

            // Obtener el mensaje de razón, MessageManager se encargará de %id_short% si %id% (de la subasta) está en reasonPlaceholders.
            // Aquí asumimos que reward.getReasonMessageKey() + "_short" es la clave correcta.
            String reasonMessage = plugin.getMessageManager().getRawMessage(
                reward.getReasonMessageKey() + "_short",
                reasonPlaceholdersWithFullId.toArray(new String[0])
            );
            lore.add(ChatColor.GRAY + "Origen: " + ChatColor.AQUA + reasonMessage);

            switch (reward.getType()) {
                case ITEM_AUCTION_WON:
                case ITEM_AUCTION_RETURNED:
                    displayItem = reward.getItemToClaim().clone();
                    ItemMeta itemMeta = displayItem.getItemMeta();
                    if (itemMeta != null) {
                        lore.add(0, ChatColor.GOLD + "Ítem: " + ChatColor.WHITE + InventoryUtil.formatMaterialName(displayItem.getType()));
                        itemMeta.setLore(lore);
                        displayItem.setItemMeta(itemMeta);
                    }
                    break;
                case MONEY_AUCTION_SOLD:
                case MONEY_BID_REFUND:
                    displayItem = new ItemStack(Material.GOLD_INGOT); // Ícono para dinero
                    ItemMeta moneyMeta = displayItem.getItemMeta();
                    if (moneyMeta != null) {
                        moneyMeta.setDisplayName(ChatColor.GOLD + "Recompensa de Dinero");
                        lore.add(0, ChatColor.GOLD + "Monto: " + ChatColor.WHITE + String.format("%.2f %s", reward.getMoneyToClaim(), plugin.getConfigManager().getCurrencySymbol()));
                        moneyMeta.setLore(lore);
                        displayItem.setItemMeta(moneyMeta);
                    }
                    break;
                default:
                    displayItem = new ItemStack(Material.BARRIER); // Ítem desconocido
                    ItemMeta unknownMeta = displayItem.getItemMeta();
                    if (unknownMeta != null) {
                        unknownMeta.setDisplayName(ChatColor.RED + "Recompensa Desconocida");
                        lore.add(0, ChatColor.RED + "Tipo: " + reward.getType().toString());
                        unknownMeta.setLore(lore);
                        displayItem.setItemMeta(unknownMeta);
                    }
            }
            // Guardar el UUID de la recompensa en el lore para identificarla al hacer clic
            // Esto es un poco hacky, una mejor forma sería usar NBT tags si tienes una lib para eso o un mapa en la clase GUI.
            if (displayItem.hasItemMeta()) {
                List<String> currentLore = displayItem.getItemMeta().getLore();
                if (currentLore == null) currentLore = new ArrayList<>();
                currentLore.add(ChatColor.BLACK + "rewardID:" + reward.getRewardId().toString()); // Oculto o semi-oculto
                ItemMeta tempMeta = displayItem.getItemMeta();
                tempMeta.setLore(currentLore);
                displayItem.setItemMeta(tempMeta);
            }
            gui.setItem(slot++, displayItem);
        }

        // Botón "Reclamar Todo"
        ItemStack claimAllButton = ItemUtil.createItemStack(Material.CHEST_MINECART,
            plugin.getMessageManager().getRawMessage("claim_gui_button_claim_all_name"), // messages.yml
            plugin.getMessageManager().getStringList("claim_gui_button_claim_all_lore") // messages.yml
        );
        gui.setItem(GUI_SIZE - 5, claimAllButton); // Centro de la última fila

        // Relleno decorativo (opcional)
        // ItemStack filler = ItemUtil.createItemStack(Material.GRAY_STAINED_GLASS_PANE, " ");
        // for (int i = GUI_SIZE - 9; i < GUI_SIZE; i++) {
        //     if (gui.getItem(i) == null) {
        //         gui.setItem(i, filler);
        //     }
        // }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        String title = plugin.getMessageManager().getRawMessage(GUI_TITLE_KEY, "%player_name%", player.getName());
        if (topInventory != null && event.getView().getTitle().equals(title)) {
            event.setCancelled(true); // Prevenir que el jugador tome ítems de la GUI

            if (clickedInventory == null || clickedInventory.equals(player.getInventory())) {
                 // Clic fuera de la GUI de recompensas o en el inventario del jugador, no hacer nada especial.
                return;
            }

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // Identificar si es el botón "Reclamar Todo"
            if (event.getSlot() == GUI_SIZE - 5 && clickedItem.getType() == Material.CHEST_MINECART) {
                rewardManager.attemptClaimAllRewards(player);
                // Se espera que attemptClaimAllRewards maneje los mensajes al jugador.
                // La GUI se refrescará después de que el jugador cierre y vuelva a abrir, o si se llama a open(player) explícitamente.
                // Por ahora, cerramos para forzar un refresco si el jugador vuelve a ejecutar /reclamar.
                // Una mejor UX sería que la GUI se actualice en vivo, pero eso es más complejo.
                open(player); // Reabrir para refrescar después del intento de reclamar todo.
                return;
            }

            // Identificar recompensa individual
            UUID rewardId = null;
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
                for (String loreLine : clickedItem.getItemMeta().getLore()) {
                    if (loreLine.startsWith(ChatColor.BLACK + "rewardID:")) {
                        try {
                            rewardId = UUID.fromString(ChatColor.stripColor(loreLine).substring("rewardID:".length()));
                            break;
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Error al parsear UUID de recompensa desde el lore: " + loreLine);
                        }
                    }
                }
            }

            if (rewardId != null) {
                PendingReward rewardToClaim = plugin.getAuctionStorage().getPendingReward(rewardId);
                if (rewardToClaim != null && !rewardToClaim.isDelivered()) {
                    boolean claimed = rewardManager.attemptClaimSpecificReward(player, rewardToClaim);
                    if (claimed) {
                        // El mensaje de éxito ya se envía desde deliverRewardInternal o attemptClaimSpecificReward
                        // Simplemente refrescamos la GUI
                        open(player); // Re-abrir para refrescar
                    } else {
                    // El mensaje de error (ej. inventario lleno) ya se envía desde deliverRewardInternal si no es claimAll.
                    // Si es claimAll, el resumen se encarga.
                    // No es necesario cerrar aquí si el reclamo falló, el jugador puede ver la GUI.
                    // Se podría refrescar la GUI si el estado de la recompensa (aunque fallida) necesita actualizarse visualmente.
                    // open(player); // Opcional, si el fallo debe refrescar la GUI.
                    }
            } else if (rewardId == null && clickedItem.getType() != Material.AIR && event.getSlot() != (GUI_SIZE -5) ){
                // Clic en un panel decorativo o ítem no reconocido (que no sea el botón de reclamar todo)
                 plugin.getLogger().fine("Player " + player.getName() + " clicked on a non-reward item in ClaimRewardsGUI: " + clickedItem.getType());
            } else if (rewardId == null && clickedItem.getType() != Material.CHEST_MINECART) {
                 plugin.getMessageManager().sendMessage(player, "claim_gui_error_reward_gone"); // Mensaje genérico si no se pudo identificar recompensa
                 open(player); // Reabrir para refrescar
                }
            }
        }
    }
}
