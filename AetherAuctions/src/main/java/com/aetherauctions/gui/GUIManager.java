package com.aetherauctions.gui;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.model.Auction; // Asegurar que es el modelo correcto
import com.aetherauctions.config.MessageManager; // Para el mensaje de error

import org.bukkit.entity.Player;
// No se necesita ChatColor si MessageManager maneja los colores del mensaje de error

public class GUIManager {

    /**
     * Abre la GUI de detalles de la subasta para un jugador.
     * Este método actúa como un punto de entrada estático.
     *
     * @param player El jugador para quien abrir la GUI.
     * @param auction El Auction a mostrar.
     * @param currentPage La página de la GUI principal desde la cual se accedió (para el botón "Volver Atrás").
     */
    public static void openAuctionInfoGui(Player player, Auction auction, int currentPage) {
        AetherAuctions plugin = AetherAuctions.getInstance();
        MessageManager msgManager = plugin.getMessageManager();

        if (auction == null) {
            // El mensaje "auction_not_loaded_error" ya debería estar en messages.yml
            // y MessageManager.getMessage ya maneja el formateo y colores.
            player.sendMessage(msgManager.getPrefixedMessage("auction_not_loaded_error"));
            plugin.getLogger().warning("[GUIManager] Se intentó abrir AuctionDetailsGUI con auction null para el jugador: " + player.getName());
            return;
        }
        // Log opcional aquí si se desea, pero AuctionDetailsGUI.open() ya tiene uno.
        // plugin.getLogger().info("[GUIManager] Llamando a AuctionDetailsGUI.open para jugador: " + player.getName() + ", Subasta ID: " + auction.getId());
        AuctionDetailsGUI.open(player, auction, currentPage);
    }

    // Más adelante se añadirá:
    public static void openMainAuctionGUI(Player player, int page) {
        // AetherAuctions plugin = AetherAuctions.getInstance(); // Not strictly needed if MainAuctionGUI.open() gets it
        // plugin.getLogger().info("[GUIManager] Llamando a MainAuctionGUI.open para jugador: " + player.getName() + ", Página: " + page);
        MainAuctionGUI.open(player, page);
    }
}
