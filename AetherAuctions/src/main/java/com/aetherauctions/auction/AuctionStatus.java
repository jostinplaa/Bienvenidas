package com.aetherauctions.auction;

public enum AuctionStatus {
    ACTIVE,     // La subasta está en curso y acepta pujas/compras
    EXPIRED,    // La subasta ha terminado sin un ganador (sin pujas o la puja no cumplió reserva si se implementara)
    SOLD_BID,   // La subasta ha terminado y se vendió a través de una puja
    SOLD_BUYNOW ("Vendido (Compra Ahora)"),
    CANCELLED ("Cancelado"),
    ADMIN_DELETED ("Eliminado por Admin");

    private final String displayName;

    AuctionStatus(String displayName) {
        this.displayName = displayName;
    }

    AuctionStatus() {
        // Para los estados que no necesitan un nombre diferente al enum constante
        this.displayName = this.name().replace("_", " ");
    }

    public String getDisplayName() {
        return displayName;
    }
}
