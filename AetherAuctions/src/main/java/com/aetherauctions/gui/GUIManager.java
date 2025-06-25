package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem;
import org.bukkit.entity.Player;

public class GUIManager {

    /**
     * Abre la GUI de detalles de la subasta para un jugador.
     * Este método actúa como un punto de entrada estático.
     *
     * @param player El jugador para quien abrir la GUI.
     * @param auction El AuctionItem a mostrar.
     * @param currentPage La página de la GUI principal desde la cual se accedió (para el botón "Volver Atrás").
     */
    public static void openAuctionInfoGui(Player player, AuctionItem auction, int currentPage) {
        AetherAuctions plugin = AetherAuctions.getInstance(); // Obtener instancia del plugin

        if (auction == null) {
            // Usar MessageManager para consistencia
            String errorMessage = plugin.getMessageManager().getMessage("auction_not_loaded_error", "&cNo se pudo cargar la información de la subasta seleccionada.");
            player.sendMessage(errorMessage);
            plugin.getLogger().warning("[GUIManager] Se intentó abrir AuctionDetailsGUI con auction null para el jugador: " + player.getName());
            return;
        }
        // Registrar el intento de apertura (opcional, ya que AuctionDetailsGUI.open() también lo hace)
        // plugin.getLogger().info("[GUIManager] Llamando a AuctionDetailsGUI.open para jugador: " + player.getName() + ", Subasta ID: " + auction.getId());
        AuctionDetailsGUI.open(player, auction, currentPage);
    }
}
