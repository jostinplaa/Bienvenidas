package com.aetherauctions.auction;

public enum AuctionStatus {
    ACTIVE,     // La subasta está en curso y acepta pujas/compras
    EXPIRED,    // La subasta ha terminado sin un ganador (sin pujas o la puja no cumplió reserva si se implementara)
    SOLD_BID,   // La subasta ha terminado y se vendió a través de una puja
    SOLD_BUYNOW,// La subasta se vendió a través de compra directa
    CANCELLED,  // La subasta fue cancelada por el vendedor o un administrador
    ADMIN_DELETED // La subasta fue eliminada directamente por un administrador
    // Podrían añadirse más estados si es necesario, ej. PENDING_DELIVERY
}
