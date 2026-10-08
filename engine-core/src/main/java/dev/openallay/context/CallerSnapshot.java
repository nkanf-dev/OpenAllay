package dev.openallay.context;

import java.util.Objects;
import java.util.UUID;

@dev.openallay.value.ValueType(CallerSnapshot.ValueSchemaProvider.class)
public final class CallerSnapshot {
    private final CallerKind kind;
    private final UUID uuid;
    private final String displayName;
    private final boolean gameMaster;
    public CallerSnapshot(CallerKind kind, UUID uuid, String displayName, boolean gameMaster) {

        Objects.requireNonNull(kind, "kind");
        displayName = ContextValidation.nonBlank(displayName, "displayName");
        if (kind == CallerKind.PLAYER && uuid == null) {
            throw new IllegalArgumentException("Player caller requires a UUID");
        }
        if (kind == CallerKind.CONSOLE && uuid != null) {
            throw new IllegalArgumentException("Console caller must not have a UUID");
        }
            this.kind = kind;
        this.uuid = uuid;
        this.displayName = displayName;
        this.gameMaster = gameMaster;
    }
    public CallerKind kind() { return kind; }
    public UUID uuid() { return uuid; }
    public String displayName() { return displayName; }
    public boolean gameMaster() { return gameMaster; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CallerSnapshot)) return false;
        CallerSnapshot that = (CallerSnapshot) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(uuid, that.uuid) && java.util.Objects.equals(displayName, that.displayName) && gameMaster == that.gameMaster;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(uuid);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + Boolean.hashCode(gameMaster);
        return hash;
    }
    @Override public String toString() { return "CallerSnapshot[kind=" + kind + ", uuid=" + uuid + ", displayName=" + displayName + ", gameMaster=" + gameMaster + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CallerSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(CallerSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CallerSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(CallerSnapshot.class, "kind", CallerSnapshot::kind),
                    new dev.openallay.value.ValueSchema.Component<>(CallerSnapshot.class, "uuid", CallerSnapshot::uuid),
                    new dev.openallay.value.ValueSchema.Component<>(CallerSnapshot.class, "displayName", CallerSnapshot::displayName),
                    new dev.openallay.value.ValueSchema.Component<>(CallerSnapshot.class, "gameMaster", CallerSnapshot::gameMaster)), arguments -> new CallerSnapshot((CallerKind) arguments[0], (UUID) arguments[1], (String) arguments[2], (Boolean) arguments[3]));
        }
    }
}
