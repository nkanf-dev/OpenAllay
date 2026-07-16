package dev.tomewisp.context;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerSnapshot(
        UUID uuid,
        String displayName,
        String dimension,
        BlockPositionSnapshot position,
        String gameMode,
        ItemStackSnapshot mainHand,
        ItemStackSnapshot offHand,
        List<InventorySlotSnapshot> inventory) {
    public PlayerSnapshot {
        Objects.requireNonNull(uuid, "uuid");
        displayName = ContextValidation.nonBlank(displayName, "displayName");
        dimension = ContextValidation.identifier(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        gameMode = ContextValidation.nonBlank(gameMode, "gameMode");
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        inventory = List.copyOf(inventory);
    }
}
