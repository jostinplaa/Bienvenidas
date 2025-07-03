package com.aetherauctions.events;

import com.aetherauctions.model.Auction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AuctionUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Auction auction;
    private final UpdateType updateType;

    public enum UpdateType {
        NEW_BID,
        SOLD_BID, // Subasta finalizada por puja
        SOLD_BUYNOW, // Subasta finalizada por compra directa
        EXPIRED,
        CANCELLED,
        NEW_AUCTION_LISTED // Para refrescar el conteo o añadirla a la GUI
        // Podríamos añadir AUCTION_MODIFIED si implementamos edición
    }

    public AuctionUpdateEvent(Auction auction, UpdateType updateType) {
        this.auction = auction;
        this.updateType = updateType;
    }

    public Auction getAuction() {
        return auction;
    }

    public UpdateType getUpdateType() {
        return updateType;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
