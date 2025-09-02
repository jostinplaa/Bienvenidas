package com.jules.auctionmasterelite.gui.menu.util;

public enum SortMode {
    ENDING_SOONEST("Finaliza Pronto"),
    NEWEST_LISTED("Más Recientes"),
    PRICE_ASCENDING("Precio: Menor a Mayor"),
    PRICE_DESCENDING("Precio: Mayor a Menor");

    private final String displayName;

    SortMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SortMode next() {
        // This will cycle through the enum values
        return values()[(this.ordinal() + 1) % values().length];
    }
}
