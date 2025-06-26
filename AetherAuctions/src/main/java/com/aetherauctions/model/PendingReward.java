package com.aetherauctions.model;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;


public class PendingReward {
    private final UUID rewardId;          // ID único de la recompensa
    private final UUID ownerId;           // UUID del jugador que debe recibir la recompensa
    private final RewardType type;        // ITEM, MONEY, MONEY_REFUND, ITEM_RETURN
    private ItemStack itemToClaim;        // ItemStack serializado (nullable)
    private double moneyToClaim;          // Cantidad de dinero (usado si type es MONEY o MONEY_REFUND)
    private String reasonMessageKey;      // Clave del mensaje para notificar al jugador (ej. "reward_reason_auction_won")
    private List<String> reasonPlaceholders; // Lista de pares de placeholders para el mensaje de razón (ej. "%item_name%", "Diamante")
    private final long creationTimestamp;
    private boolean delivered;            // Si la recompensa ya ha sido entregada/reclamada

    public enum RewardType {
        ITEM_AUCTION_WON,       // Ítem ganado en subasta
        ITEM_AUCTION_RETURNED,  // Ítem devuelto (expiró sin pujas, cancelada)
        MONEY_AUCTION_SOLD,     // Dinero por venta de subasta
        MONEY_BID_REFUND        // Dinero por puja devuelta (superada, cancelada)
    }

    // Constructor para recompensa de ÍTEM
    public PendingReward(UUID ownerId, RewardType type, ItemStack itemToClaim, String reasonMessageKey, List<String> reasonPlaceholders) {
        if (type != RewardType.ITEM_AUCTION_WON && type != RewardType.ITEM_AUCTION_RETURNED) {
            throw new IllegalArgumentException("Incorrect constructor for ITEM reward type. Provided: " + type);
        }
        this.rewardId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.type = type;
        this.itemToClaim = itemToClaim != null ? itemToClaim.clone() : null;
        this.moneyToClaim = 0;
        this.reasonMessageKey = reasonMessageKey;
        this.reasonPlaceholders = reasonPlaceholders != null ? new ArrayList<>(reasonPlaceholders) : new ArrayList<>();
        this.creationTimestamp = System.currentTimeMillis();
        this.delivered = false;
    }

    // Constructor para recompensa de DINERO
    public PendingReward(UUID ownerId, RewardType type, double moneyToClaim, String reasonMessageKey, List<String> reasonPlaceholders) {
        if (type != RewardType.MONEY_AUCTION_SOLD && type != RewardType.MONEY_BID_REFUND) {
            throw new IllegalArgumentException("Incorrect constructor for MONEY reward type. Provided: " + type);
        }
        this.rewardId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.type = type;
        this.itemToClaim = null;
        this.moneyToClaim = moneyToClaim;
        this.reasonMessageKey = reasonMessageKey;
        this.reasonPlaceholders = reasonPlaceholders != null ? new ArrayList<>(reasonPlaceholders) : new ArrayList<>();
        this.creationTimestamp = System.currentTimeMillis();
        this.delivered = false;
    }

    // Constructor para cargar desde DB (todos los campos)
    public PendingReward(UUID rewardId, UUID ownerId, RewardType type, ItemStack itemToClaim, double moneyToClaim,
                         String reasonMessageKey, List<String> reasonPlaceholders, long creationTimestamp, boolean delivered) {
        this.rewardId = rewardId;
        this.ownerId = ownerId;
        this.type = type;
        this.itemToClaim = itemToClaim;
        this.moneyToClaim = moneyToClaim;
        this.reasonMessageKey = reasonMessageKey;
        this.reasonPlaceholders = reasonPlaceholders;
        this.creationTimestamp = creationTimestamp;
        this.delivered = delivered;
    }

    // Getters
    public UUID getRewardId() { return rewardId; }
    public UUID getOwnerId() { return ownerId; }
    public RewardType getType() { return type; }
    public ItemStack getItemToClaim() { return itemToClaim != null ? itemToClaim.clone() : null; }
    public double getMoneyToClaim() { return moneyToClaim; }
    public String getReasonMessageKey() { return reasonMessageKey; }
    public List<String> getReasonPlaceholders() { return reasonPlaceholders != null ? new ArrayList<>(reasonPlaceholders) : new ArrayList<>(); }
    public long getCreationTimestamp() { return creationTimestamp; }
    public boolean isDelivered() { return delivered; }

    // Setters
    public void setDelivered(boolean delivered) { this.delivered = delivered; }
}
