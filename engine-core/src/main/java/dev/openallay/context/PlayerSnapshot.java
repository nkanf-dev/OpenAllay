package dev.openallay.context;

import java.util.Objects;
import java.util.UUID;

@dev.openallay.value.ValueType(PlayerSnapshot.ValueSchemaProvider.class)
public final class PlayerSnapshot {
    private final UUID uuid;
    private final String displayName;
    private final String dimension;
    private final BlockPositionSnapshot position;
    private final String gameMode;
    private final InventorySnapshot inventory;
    private final EvidenceMetadata evidence;
    public PlayerSnapshot(UUID uuid, String displayName, String dimension, BlockPositionSnapshot position, String gameMode, InventorySnapshot inventory, EvidenceMetadata evidence) {

        Objects.requireNonNull(uuid, "uuid");
        displayName = ContextValidation.nonBlank(displayName, "displayName");
        dimension = ContextValidation.identifier(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        gameMode = ContextValidation.nonBlank(gameMode, "gameMode");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(evidence, "evidence");
            this.uuid = uuid;
        this.displayName = displayName;
        this.dimension = dimension;
        this.position = position;
        this.gameMode = gameMode;
        this.inventory = inventory;
        this.evidence = evidence;
    }
    public UUID uuid() { return uuid; }
    public String displayName() { return displayName; }
    public String dimension() { return dimension; }
    public BlockPositionSnapshot position() { return position; }
    public String gameMode() { return gameMode; }
    public InventorySnapshot inventory() { return inventory; }
    public EvidenceMetadata evidence() { return evidence; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlayerSnapshot)) return false;
        PlayerSnapshot that = (PlayerSnapshot) other;
        return java.util.Objects.equals(uuid, that.uuid) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(dimension, that.dimension) && java.util.Objects.equals(position, that.position) && java.util.Objects.equals(gameMode, that.gameMode) && java.util.Objects.equals(inventory, that.inventory) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(uuid);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(dimension);
        hash = 31 * hash + java.util.Objects.hashCode(position);
        hash = 31 * hash + java.util.Objects.hashCode(gameMode);
        hash = 31 * hash + java.util.Objects.hashCode(inventory);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "PlayerSnapshot[uuid=" + uuid + ", displayName=" + displayName + ", dimension=" + dimension + ", position=" + position + ", gameMode=" + gameMode + ", inventory=" + inventory + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PlayerSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(PlayerSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PlayerSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "uuid", PlayerSnapshot::uuid),
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "displayName", PlayerSnapshot::displayName),
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "dimension", PlayerSnapshot::dimension),
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "position", PlayerSnapshot::position),
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "gameMode", PlayerSnapshot::gameMode),
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "inventory", PlayerSnapshot::inventory),
                    new dev.openallay.value.ValueSchema.Component<>(PlayerSnapshot.class, "evidence", PlayerSnapshot::evidence)), arguments -> new PlayerSnapshot((UUID) arguments[0], (String) arguments[1], (String) arguments[2], (BlockPositionSnapshot) arguments[3], (String) arguments[4], (InventorySnapshot) arguments[5], (EvidenceMetadata) arguments[6]));
        }
    }
}
